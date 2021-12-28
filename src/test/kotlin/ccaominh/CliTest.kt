/*
 * Copyright 2020-2022 Chi Cao Minh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ccaominh

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemPropertyTestListener
import io.kotest.extensions.system.withSystemProperty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class IsValidIntellijTest : TempDirStringSpec() {
    init {
        "fails if no OS script present" {
            forAll(
                row(LINUX, "bin/inspect.bat"),
                row(MAC, "bin/inspect.sh"),
                row(WINDOWS, "bin\\inspect.sh"),
                row("unknown", "bin/inspect.sh")
            ) { os, script ->
                createFile(script)
                withSystemProperty(PROP_OS, os, OverrideMode.SetOrOverride) {
                    val pwd = tempDir.absolutePath
                    isValidIntellij(pwd) shouldBe false
                }
            }
        }

        "succeeds if OS script present" {
            forAll(
                row(LINUX, "bin/inspect.sh"),
                row(MAC, "Contents/bin/inspect.sh"),
                row(WINDOWS, "bin\\inspect.bat")
            ) { os, script ->
                createFile(script)
                withSystemProperty(PROP_OS, os, OverrideMode.SetOrOverride) {
                    val pwd = tempDir.absolutePath
                    isValidIntellij(pwd) shouldBe true
                }
            }
        }
    }
}

class IsValidProjectTest : TempDirStringSpec() {
    init {
        "succeeds if directory" {
            val project = createDir("project")
            isValidProject(project.absolutePath) shouldBe true
        }

        "succeeds if file" {
            val project = createFile("project")
            isValidProject(project.absolutePath) shouldBe true
        }

        "fails if does not exist" {
            val project = File("missing")
            isValidProject(project.absolutePath) shouldBe false
        }
    }
}

class IsValidProfileTest : TempDirStringSpec() {
    init {
        "fails if profile is not file" {
            val profile = createDir("profile.xml")
            isValidProfile(profile.absolutePath) shouldBe false
        }

        "fails if profile is not XML" {
            val profile = createFile("profile")
            isValidProfile(profile.absolutePath) shouldBe false
        }

        "succeeds if profile is valid XML file" {
            val profile = createFile("profile.xml")
            isValidProfile(profile.absolutePath) shouldBe true
        }
    }
}

class IsValidSubdirTest : TempDirStringSpec() {
    init {
        "fails if subdir is not directory" {
            val subdir = createFile("subdir")
            isValidSubdir(subdir.absolutePath) shouldBe false
        }

        "succeeds if subdir is directory" {
            val subdir = createDir("subdir")
            isValidSubdir(subdir.absolutePath) shouldBe true
        }
    }
}

class CliTest : TempDirStringSpec() {
    private val NO_ARGS = listOf<String>()
    private val INTELLIJ = "path/to/intellij"
    private val PROJECT = "path/to/project"
    private val PROFILE = "profile.xml"

    private lateinit var target: Cli

    override fun listeners() = listOf(
        SystemPropertyTestListener(PROP_OS, LINUX, OverrideMode.SetOrOverride)
    )

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        target = Cli(test = true)
    }

    init {
        "prints help if no args" {
            shouldThrow<PrintHelpMessage> {
                target.parse(NO_ARGS)
            }
        }

        "prints help with help option" {
            forAll(
                row("-h"),
                row("--help")
            ) { helpOpt ->
                shouldThrow<PrintHelpMessage> {
                    target.parse(listOf(helpOpt))
                }
            }
        }

        "sets required args when options absent" {
            target.parse(args())
            requiredArgsShouldMatch()
        }

        "sets required args and option when option present" {
            val shortOpt = { p: String -> "-${p.first()}" }
            val longOpt = { p: String -> "--${p}" }
            val makeDir: (String) -> String = { s: String -> createDir(s).absolutePath }
            val makeNoop: (String) -> String = { s: String -> s }

            forAll(
                row("output", "path/to/output/", makeNoop),
                row("directory", "path/to/dir/", makeDir),
                row("scope", "MY_SCOPE", makeNoop)
            ) { propName, arg, make ->
                val value = make(arg)

                target.parse(args(shortOpt(propName), value))
                requiredArgsShouldMatch()
                readProp<String>(propName) shouldBe value

                target.parse(args(longOpt(propName), value))
                requiredArgsShouldMatch()
                readProp<String>(propName) shouldBe value
            }
        }

        "sets intellij, project, and levels prop with args and with level option" {
            forAll(
                row("-l"),
                row("--levels")
            ) { opt ->
                val levels = listOf("ONE", "TWO", "THREE")
                target.parse(args(opt, levels.joinToString(separator = ",")))
                requiredArgsShouldMatch()
                readProp<List<String>>("levels") shouldBe levels
            }
        }

        "fails if required arg is invalid" {
            val invalid = File("invalid")
            val validIntellij = createIntellij()
            val validProject = createProject()
            val validProfile = createProfile()

            forAll(
                row(invalid, validProject, validProfile, "INTELLIJ"),
                row(validIntellij, invalid, validProfile, "PROJECT"),
                row(validIntellij, validProject, invalid, "PROFILE")
            ) { intellij, project, profile, name ->
                val exception = shouldThrow<UsageError> {
                    target.parse(listOf(intellij.absolutePath, project.absolutePath, profile.absolutePath))
                }
                exception.message shouldStartWith "Invalid value for \"${name}\""
            }
        }

        "fails if too many args" {
            val exception = shouldThrow<UsageError> {
                target.parse(args("extra"))
            }
            exception.message shouldBe "Got unexpected extra argument (extra)"
        }
    }

    private fun args(vararg options: String): List<String> {
        val intellij = createIntellij()
        val project = createProject()
        val profile = createProfile()
        val args = mutableListOf(intellij.absolutePath, project.absolutePath, profile.absolutePath)
        args.addAll(options)
        return args
    }

    private fun createIntellij(): File {
        val intellij = createDir(INTELLIJ)
        val script = getIntellijInspectScript()
        createFile("${INTELLIJ}/${script}")
        return intellij
    }

    private fun createProject(): File {
        return createDir(PROJECT)
    }

    private fun createProfile(): File {
        return createFile(PROFILE)
    }

    private fun requiredArgsShouldMatch() {
        readProp<String>("intellij") shouldBe "${tempDir.absolutePath}/${INTELLIJ}"
        readProp<String>("project") shouldBe "${tempDir.absolutePath}/${PROJECT}"
        readProp<String>("profile") shouldBe "${tempDir.absolutePath}/${PROFILE}"
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readProp(propName: String): T {
        return target::class.memberProperties
            .first { it.name == propName }
            .also { it.isAccessible = true }
            .getter
            .call(target) as T
    }
}

