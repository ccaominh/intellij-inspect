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

import com.fasterxml.jackson.core.JsonParseException
import io.kotlintest.TestCase
import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row

private const val RELATIVE_FILE = "src/main/java/com/company/Main.java"
private const val FILE = "file://${'$'}PROJECT_DIR${'$'}/$RELATIVE_FILE"
private const val LINE = 3
private const val MODULE = "dirty"
private const val PACKAGE = "org.company"
private const val ENTRY_POINT_TYPE = "class"
private const val ENTRY_POINT_FQNAME = "org.company.Main"
private const val PROBLEM_CLASS_SEVERITY = "WARNING"
private const val PROBLEM_CLASS_ATTRIBUTE_KEY = "WARNING_ATTRIBUTES"
private const val PROBLEM_CLASS_VALUE = "Declaration access can be weaker"
private const val HINT_VALUE = "packageLocal"
private const val DESCRIPTION = "Can be package-private"

private const val SUMMARY = "[${PROBLEM_CLASS_SEVERITY}] ${RELATIVE_FILE}:${LINE} -- ${DESCRIPTION}"
private val LEVELS = setOf(PROBLEM_CLASS_SEVERITY)

private val PROBLEM = Problem(
    file = FILE,
    line = LINE,
    module = MODULE,
    packageName = PACKAGE,
    entryPoint = EntryPoint(
        type = ENTRY_POINT_TYPE,
        fullyQualifiedName = ENTRY_POINT_FQNAME
    ),
    problemClass = ProblemClass(
        severity = PROBLEM_CLASS_SEVERITY,
        attributeKey = PROBLEM_CLASS_ATTRIBUTE_KEY,
        value = PROBLEM_CLASS_VALUE
    ),
    hints = listOf(Hint(
        value = HINT_VALUE
    )),
    description = DESCRIPTION
)

private const val PROBLEM_MODULE_LOCATION = "gradle-clean.test"
private const val PROBLEM_MODULE_DESCRIPTION =
        "Module '${PROBLEM_MODULE_LOCATION}' sources do not depend on module 'gradle-clean.main' sources"
private const val PROBLEM_MODULE_SUMMARY =
        "[${PROBLEM_CLASS_SEVERITY}] ${PROBLEM_MODULE_LOCATION} -- ${PROBLEM_MODULE_DESCRIPTION}"

private val PROBLEM_MODULE = Problem(
    file = PROBLEM_MODULE_LOCATION,
    module = PROBLEM_MODULE_LOCATION,
    packageName = "&lt;default&gt;",
    entryPoint = EntryPoint(
        type = "module",
        fullyQualifiedName = PROBLEM_MODULE_LOCATION
    ),
    problemClass = ProblemClass(
        severity = PROBLEM_CLASS_SEVERITY,
        attributeKey = PROBLEM_CLASS_ATTRIBUTE_KEY,
        value = "Unnecessary module dependency"
    ),
    hints = listOf(Hint(
        value = "gradle-clean.main"
    )),
    description = PROBLEM_MODULE_DESCRIPTION
)

private val REPORT = Report(listOf(PROBLEM))

@Suppress("BlockingMethodInNonBlockingContext")  // FIXME
class ParseTest : StringSpec() {
    init {
        "parses valid report XML" {
            val report = parseReport(xml())
            report shouldBe REPORT
        }

        "parses valid minimal report XML" {
            val xml = """
                <problems is_local_tool="true">
                  <problem>
                    <file>$FILE</file>
                    <package>$PACKAGE</package>
                    <entry_point TYPE="$ENTRY_POINT_TYPE" FQNAME="$ENTRY_POINT_FQNAME" />
                    <problem_class severity="$PROBLEM_CLASS_SEVERITY" attribute_key="$PROBLEM_CLASS_ATTRIBUTE_KEY">$PROBLEM_CLASS_VALUE</problem_class>
                    <description>$DESCRIPTION</description>
                  </problem>
                </problems>
            """
            val expected = Report(listOf(
                Problem(
                    file = FILE,
                    packageName = PACKAGE,
                    entryPoint = EntryPoint(
                        type = ENTRY_POINT_TYPE,
                        fullyQualifiedName = ENTRY_POINT_FQNAME
                    ),
                    problemClass = ProblemClass(
                        severity = PROBLEM_CLASS_SEVERITY,
                        attributeKey = PROBLEM_CLASS_ATTRIBUTE_KEY,
                        value = PROBLEM_CLASS_VALUE
                    ),
                    description = DESCRIPTION
                )
            ))

            val report = parseReport(xml)
            report shouldBe expected
        }

        "parses valid report XML with empty hints" {
            val xml = """
                <problems>
                  <problem>
                    <file>$FILE</file>
                    <line>$LINE</line>
                    <module>$MODULE</module>
                    <package>$PACKAGE</package>
                    <entry_point TYPE="$ENTRY_POINT_TYPE" FQNAME="$ENTRY_POINT_FQNAME" />
                    <problem_class severity="$PROBLEM_CLASS_SEVERITY" attribute_key="$PROBLEM_CLASS_ATTRIBUTE_KEY">$PROBLEM_CLASS_VALUE</problem_class>
                    <hints />
                    <description>$DESCRIPTION</description>
                  </problem>
                </problems>
            """
            val expected = Report(listOf(
                Problem(
                    file = FILE,
                    line = LINE,
                    module = MODULE,
                    packageName = PACKAGE,
                    entryPoint = EntryPoint(
                        type = ENTRY_POINT_TYPE,
                        fullyQualifiedName = ENTRY_POINT_FQNAME
                    ),
                    problemClass = ProblemClass(
                        severity = PROBLEM_CLASS_SEVERITY,
                        attributeKey = PROBLEM_CLASS_ATTRIBUTE_KEY,
                        value = PROBLEM_CLASS_VALUE
                    ),
                    hints = null,
                    description = DESCRIPTION
                )
            ))

            val report = parseReport(xml)
            report shouldBe expected
        }

        "parses valid report XML with unknown properties" {
            val report = parseReport(xml("<unknown>property</unknown>"))
            report shouldBe REPORT
        }

        "fails parse with invalid XML" {
            shouldThrow<JsonParseException> {
                parseReport("invalid XML")
            }
        }

        "has accurate summary" {
            val summary = parseReport(xml()).getSummary(LEVELS)
            summary shouldBe listOf(SUMMARY)
        }
    }

    private fun xml(extra: String = ""): String {
        return """
        <problems>
          <problem>
            <file>$FILE</file>
            <line>$LINE</line>
            <module>$MODULE</module>
            <package>$PACKAGE</package>
            <entry_point TYPE="$ENTRY_POINT_TYPE" FQNAME="$ENTRY_POINT_FQNAME" />
            <problem_class severity="$PROBLEM_CLASS_SEVERITY" attribute_key="$PROBLEM_CLASS_ATTRIBUTE_KEY">$PROBLEM_CLASS_VALUE</problem_class>
            <hints>
              <hint value="$HINT_VALUE" />
            </hints>
            <description>$DESCRIPTION</description>
          </problem>${extra}
        </problems>
    """
    }
}

class ReportTest : StringSpec() {
    init {
        "returns matching summary" {
            forall(
                row(setOf(PROBLEM_CLASS_SEVERITY), listOf(SUMMARY)),
                row(emptySet(), emptyList())
            ) { levels, expected ->
                REPORT.getSummary(levels) shouldBe expected
            }
        }
    }
}

class ProblemTest : StringSpec() {
    init {
        "returns correct severity" {
            PROBLEM.getSeverity() shouldBe PROBLEM_CLASS_SEVERITY
        }

        "returns correct summary for non-module problem" {
            PROBLEM.getSummary() shouldBe SUMMARY
        }

        "returns correct summary for module problem" {
            PROBLEM_MODULE.getSummary() shouldBe PROBLEM_MODULE_SUMMARY
        }
    }
}

class ProblemClassTest : StringSpec() {
    private lateinit var target: ProblemClass

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        target = ProblemClass(severity = "severity", attributeKey = "attributeKey", value = "value")
    }

    init {
        "considers all properties for equals()" {
            forall(
                row(target, true),
                row(null, false),
                row(ProblemClass(target.severity, target.attributeKey, target.value), true),
                row(ProblemClass("diff", target.attributeKey, target.value), false),
                row(ProblemClass(target.severity, "diff", target.value), false),
                row(ProblemClass(target.severity, target.attributeKey, "diff"), false)
            ) { other, expected ->
                (target == other) shouldBe expected
            }
        }

        "considers all properties for hashCode()" {
            target.hashCode() shouldBe target.hashCode()
            target.hashCode() shouldBe ProblemClass(target.severity, target.attributeKey, target.value).hashCode()
            target.hashCode() shouldNotBe ProblemClass("diff", target.attributeKey, target.value).hashCode()
            target.hashCode() shouldNotBe ProblemClass(target.severity, "diff", target.value).hashCode()
            target.hashCode() shouldNotBe ProblemClass(target.severity, target.attributeKey, "diff").hashCode()
        }

        "considers all properties for toString()" {
            target.toString() shouldBe
                    "ProblemClass(severity='${target.severity}', attributeKey='${target.attributeKey}', value='${target.value}')"
        }
    }
}
