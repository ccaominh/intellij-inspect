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

import io.kotlintest.TestCase
import io.kotlintest.data.forall
import io.kotlintest.extensions.system.OverrideMode
import io.kotlintest.extensions.system.withSystemProperty
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import java.io.File

class GetIntellijInspectScriptTest : StringSpec() {
    init {
        "returns correct script for OS" {
            forall(
                row(LINUX, "bin/inspect.sh"),
                row(MAC, "Contents/bin/inspect.sh"),
                row(WINDOWS, "bin\\inspect.bat"),
                row("unknown", null)
            ) { os, expected ->
                withSystemProperty(PROP_OS, os, OverrideMode.SetOrOverride) {
                    getIntellijInspectScript() shouldBe expected
                }
            }
        }
    }
}

class AnalyzeTest : StringSpec() {
    private val dummyPreparer = {}
    private val dummyRunner = { 1 }
    private val emptyGatherer = { emptySequence<File>() }
    private val singleGatherer = { sequenceOf(File("a")) }
    private val nullReporter: (f: File) -> String? = { null }
    private val emptyReporter: (f: File) -> String? = { "" }
    private val nameReporter: (File) -> String? = { f -> f.name }

    init {
        "finds violations if they exist" {
            forall(
                row(emptyGatherer, nameReporter, true),
                row(singleGatherer, nullReporter, true),
                row(singleGatherer, emptyReporter, true),
                row(singleGatherer, nameReporter, false)
            ) { gatherer, reporter, expected ->
                analyze(dummyPreparer, dummyRunner, gatherer, reporter) shouldBe expected
            }
        }
    }
}

private const val INTELLIJ = "intellij"

class SetupInspectScope : TempDirStringSpec() {
    private lateinit var intellij: String

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        intellij = createDir(INTELLIJ).absolutePath
    }

    init {
        "sets up correct properties for OS" {
            forall(
                row(LINUX, "bin/idea.properties"),
                row(MAC, "Contents/bin/idea.properties"),
                row(WINDOWS, "bin\\idea.properties")
            ) { os, intellijProperties ->
                withSystemProperty(PROP_OS, os, OverrideMode.SetOrOverride) {
                    val scope = "MY_SCOPE"
                    val expected = "idea.analyze.scope=${scope}"
                    setupInspectScope(intellij, scope)
                    val actual = File(intellij, intellijProperties).readText()
                    actual shouldBe expected
                }
            }
        }

        "fails if OS unknown" {
            val os = "unknown"
            val dummy = ""
            withSystemProperty(PROP_OS, os, OverrideMode.SetOrOverride) {
                val exception = shouldThrow<IllegalStateException> {
                    setupInspectScope(dummy, dummy)
                }
                exception.message shouldBe "Invalid operating system: ${os}"
            }
        }
    }
}

class RunIntellijInspectTest : TempDirStringSpec() {
    private lateinit var intellij: String
    private lateinit var project: String
    private lateinit var profile: String
    private lateinit var output: String

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        intellij = createDir(INTELLIJ).absolutePath
        project = createDir("project").absolutePath
        profile = createDir("profile").absolutePath
        output = createDir("output").absolutePath
    }

    init {
        "fails if invalid os" {
            withSystemProperty(PROP_OS, "unknown", OverrideMode.SetOrOverride) {
                shouldThrow<NullPointerException> {
                    runIntellijInspect(intellij, project, profile, output, null)
                }
            }
        }

        "returns correct exit code" {
            val script = getIntellijInspectScript()
            val scriptFile = createFile("${INTELLIJ}/${script}")
            val exitCode = 123
            scriptFile.writeText("exit ${exitCode}")
            scriptFile.setExecutable(true)

            runIntellijInspect(intellij, project, profile, output, null) shouldBe exitCode
        }

        // TODO: Verify there is output on stdout. Unfortunately, using System.setOut() does not capture
        // ProcessBuilder().inheritIO() output.
        "succeeds without subdir or scope" {
            val (scriptFile, processOutputFile) = createMockInspectScript()

            runIntellijInspect(intellij, project, profile, output, null) shouldBe 0
            val processOutput = processOutputFile.readText().trim()
            processOutput shouldBe "${scriptFile.absolutePath} ${project} ${profile} ${output} -v2"
        }

        "succeeds if script with subdir successful" {
            val (scriptFile, processOutputFile) = createMockInspectScript()

            val directory = createDir("directory").absolutePath
            runIntellijInspect(intellij, project, profile, output, directory) shouldBe 0
            val processOutput = processOutputFile.readText().trim()
            processOutput shouldBe "${scriptFile.absolutePath} ${project} ${profile} ${output} -v2 -d ${directory}"
        }
    }

    data class MockInspect(
        val script: File,
        val output: File
    )

    private fun createMockInspectScript(): MockInspect {
        val output = File(tempDir, "process.out")
        val script = getIntellijInspectScript()
        val scriptFile = createFile("${INTELLIJ}/${script}")
        scriptFile.writeText("#!/bin/sh\necho \"$0 $@\" > ${output.absolutePath}")
        scriptFile.setExecutable(true)
        return MockInspect(scriptFile, output)
    }
}
