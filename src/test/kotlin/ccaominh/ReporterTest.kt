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

import io.kotlintest.shouldBe

class GetOutputFilesTest : TempDirStringSpec() {
    init {
        "visits only visible XML files" {
            createFile("a.x")  // wrong extension
            val b = createFile("b.xml")
            createDir("c.xml")  // not a file
            createFile(".d.xml")  // hidden
            val files = getOutputFiles(tempDir.absolutePath)
            files.toList() shouldBe listOf(b)
        }

        "traverses only direct children" {
            val a = createFile("a.xml")
            createFile("nested/b.xml")
            val files = getOutputFiles(tempDir.absolutePath)
            files.toList() shouldBe listOf(a)
        }
    }
}

private const val RELATIVE_FILE = "src/main/java/com/company/Main.java"
private const val FILE = "file://${'$'}PROJECT_DIR${'$'}/$RELATIVE_FILE"
private const val LINE = 3
private const val PROBLEM_CLASS_SEVERITY = "WARNING"
private const val DESCRIPTION = "Can be package-private"
private const val XML = """
    <problems is_local_tool="true">
      <problem>
        <file>$FILE</file>
        <line>$LINE</line>
        <module>dirty</module>
        <package>org.company</package>
        <entry_point TYPE="class" FQNAME="org.company.Main" />
        <problem_class severity="$PROBLEM_CLASS_SEVERITY" attribute_key="WARNING_ATTRIBUTES">"Declaration access can be weaker"</problem_class>
        <description>$DESCRIPTION</description>
      </problem>
    </problems>
"""
private val LEVELS = setOf(PROBLEM_CLASS_SEVERITY)
private val LEVELS_NO_MATCH = setOf("ERROR")

class GetReportSummaryTest : TempDirStringSpec() {
    init {
        "gets summary if levels match" {
            val xml = createFile("xml", XML)
            getReportSummary(xml, LEVELS) shouldBe "[$PROBLEM_CLASS_SEVERITY] $RELATIVE_FILE:$LINE -- $DESCRIPTION"
        }

        "gets empty summary if levels do not match" {
            val xml = createFile("xml", XML)
            getReportSummary(xml, LEVELS_NO_MATCH) shouldBe ""
        }

        "gets null if unable to read report" {
            val invalid = createFile("invalid", "not valid xml report")
            getReportSummary(invalid, LEVELS) shouldBe null
        }
    }
}
