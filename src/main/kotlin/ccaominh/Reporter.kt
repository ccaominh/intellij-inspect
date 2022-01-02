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

import java.io.File
import java.io.IOException

fun getOutputFiles(outputDir: String): Sequence<File> {
    return File(outputDir).walk()
            .maxDepth(1)
            .filter { file -> file.isFile && !file.name.startsWith(".") && file.extension == "xml" }
}

fun getReportSummary(file: File, levels: Set<String>): String? {
    return try {
        val report = parseReport(file.readText())
        report.getSummary(levels).joinToString("\n")
    } catch (e: IOException) {
        System.err.println("Error parsing ${file.absolutePath}:\n}")
        System.err.println("---")
        System.err.println(file.readText())
        System.err.println("---")
        e.printStackTrace()
        null
    }
}

