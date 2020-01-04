/*
 * Copyright 2020 Chi Cao Minh
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

import java.io.File

/**
 * Get Intellij inspect script name for current OS, or null if unknown OS
 */
internal fun getIntellijInspectScript(): String? {
    // https://www.jetbrains.com/help/idea/command-line-code-inspector.html
    val os = getOsName()
    return when {
        os.indexOf("nux") >= 0 -> "bin/inspect.sh"
        os.indexOf("mac") >= 0 -> "Contents/bin/inspect.sh"
        os.indexOf("win") >= 0 -> "bin\\inspect.bat"
        else -> null
    }
}

private fun getOsName(): String {
    return System.getProperty("os.name").toLowerCase()
}

// For facilitating testing
internal fun analyze(
    preparer: () -> Unit,
    runner: () -> Int,
    gatherer: () -> Sequence<File>,
    reporter: (File) -> String?
): Boolean {
    preparer()
    runner()

    var noViolation = true
    val outputFiles = gatherer()
    outputFiles.forEach { file ->
        reporter(file)?.apply {
            if (this.isNotBlank()) {
                noViolation = false
                println(this)
            }
        }
    }

    return noViolation
}

internal fun setupInspectScope(intellij: String, scope: String) {
    // https://www.jetbrains.com/help/idea/tuning-the-ide.html#configure-platform-properties
    val os = getOsName()
    val intellijPropertiesName = when {
        os.indexOf("nux") >= 0 -> "bin/"
        os.indexOf("mac") >= 0 -> "Contents/bin/"
        os.indexOf("win") >= 0 -> "bin\\"
        else -> throw IllegalStateException("Invalid operating system: ${os}")
    } + "idea.properties"

    val intellijPropertiesFile = File(intellij, intellijPropertiesName)
    intellijPropertiesFile.parentFile.mkdirs()
    intellijPropertiesFile.writeText("idea.analyze.scope=${scope}")
}

internal fun runIntellijInspect(
    intellij: String,
    project: String,
    profile: String,
    output: String,
    directory: String?
): Int {
    val inspect = File(intellij, getIntellijInspectScript()!!).absolutePath
    val projectPath = File(project).absolutePath
    val inspectionPath = File(profile).absolutePath
    val outputDir = File(output)
    outputDir.mkdirs()
    val outputPath = outputDir.absolutePath

    val command = mutableListOf<String>(
        inspect,
        projectPath,
        inspectionPath,
        outputPath,
        "-v2"  // for progress indicator
    )

    if (directory != null) {
        command.addAll(listOf("-d", directory))
    }

    // https://www.jetbrains.com/help/idea/command-line-code-inspector.html
    return ProcessBuilder(command).inheritIO().start().waitFor()
}
