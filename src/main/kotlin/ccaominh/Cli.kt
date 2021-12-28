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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.validate
import java.io.File
import kotlin.system.exitProcess

private val DEFAULT_LEVELS = listOf("ERROR", "WARNING")
private const val DEFAULT_OUTPUT = "inspection-results"

internal fun isValidIntellij(intellij: String): Boolean {
    val script = getIntellijInspectScript()
    return script != null && File(intellij, script).isFile
}

internal fun isValidProject(project: String): Boolean {
    return File(project).isDirectory || File(project).isFile
}

internal fun isValidProfile(profile: String): Boolean {
    val profileFile = File(profile)
    return profileFile.isFile && profileFile.extension == "xml"
}

internal fun isValidSubdir(subdir: String): Boolean {
    return File(subdir).isDirectory
}

class Cli(
    private val test: Boolean = false
) : CliktCommand(
    name = "java -jar intellij-inspect.jar",
    printHelpOnEmptyArgs = true
) {
    private val intellij by argument(help = "Absolute path to IntelliJ installation")
        .validate {
            if (!isValidIntellij(it)) {
                fail("Cannot find IntelliJ inspect script in \"${it}\". Is it a valid IntelliJ installation?")
            }
        }

    private val project by argument(
        help = "Absolute path to IntelliJ project directory, pom.xml, or build.gradle, etc. to analyze"
    ).validate {
        if (!isValidProject(it)) {
            fail("Is \"${it}\" a valid directory or file?")
        }
    }

    private val profile by argument(help = "Absolute path to inspection profile to use")
        .validate {
            if (!isValidProfile(it)) {
                fail("Is \"${it}\" a valid XML file?")
            }
        }

    private val directory by option(
        "-d", "--directory",
        help = "Absolute path to directory within project to be inspected"
    ).validate {
        if (!isValidSubdir(it)) {
            fail("Is \"${it}\" a valid directory?")
        }
    }

    private val levels by option(
        "-l", "--levels",
        help = "Inspection severity levels to analyze ${default(DEFAULT_LEVELS.joinToString(separator = ","))}"
    ).split(",").default(DEFAULT_LEVELS)

    private val output by option(
        "-o", "--output",
        help = "Absolute path to output inspection analysis results ${default(DEFAULT_OUTPUT)}"
    ).default(DEFAULT_OUTPUT)

    private val scope by option("-s", "--scope", help = "Name of IntelliJ scope to use")

    // Clikt's help formatting of default does not work correctly for multiple options or split options
    private fun default(msg: String): String {
        return "(default: ${msg})"
    }

    override fun run() {
        if (test) {
            return  // for tests, we just want to test parsing
        }

        val preparer = fun() { scope?.let { setupInspectScope(intellij, it) } }
        val runner = { runIntellijInspect(intellij, project, profile, output, directory) }
        val gatherer = { getOutputFiles(output) }
        val reporter = { f: File -> getReportSummary(f, levels.toSet()) }

        if (!analyze(preparer, runner, gatherer, reporter)) {
            exitProcess(1)
        }
    }
}
