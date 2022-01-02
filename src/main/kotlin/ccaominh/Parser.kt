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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.module.kotlin.KotlinModule

private const val FILE_NAME_PREFIX = "file://\$PROJECT_DIR\$/"

private val MAPPER: ObjectMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
        .registerModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@Throws(JsonParseException::class)
fun parseReport(xml: String): Report {
    return MAPPER.readValue(xml, Report::class.java)
}

data class Report(
    @JsonProperty("problem")
    val problems: List<Problem>
) {
    fun getSummary(levels: Set<String>): List<String> {
        return problems
                .filter { p -> p.getSeverity() in levels }
                .map { p -> p.getSummary() }
    }
}

data class Problem(
    val file: String,
    val line: Int? = null,
    val module: String? = null,
    @JacksonXmlProperty(localName = "package")
    val packageName: String,
    @JacksonXmlProperty(localName = "entry_point")
    val entryPoint: EntryPoint,
    @JacksonXmlProperty(localName = "problem_class")
    val problemClass: ProblemClass,
    @JacksonXmlElementWrapper
    val hints: List<Hint>? = null,  // Jackson deserializes empty list as null: https://git.io/JeCnd
    val description: String
) {
    fun getSeverity(): String {
        return problemClass.severity
    }

    fun getSummary(): String {
        val location = if (line == null) {
            entryPoint.fullyQualifiedName
        } else {
            "${file.substring((FILE_NAME_PREFIX.length))}:${line}"
        }

        return "[${problemClass.severity}] ${location} -- ${description}"
    }
}

data class EntryPoint(
    @JacksonXmlProperty(isAttribute = true, localName = "TYPE")
    val type: String,
    @JacksonXmlProperty(isAttribute = true, localName = "FQNAME")
    val fullyQualifiedName: String
)

@JacksonXmlRootElement(localName = "problem_class")
data class ProblemClass(
    @JacksonXmlProperty(isAttribute = true)
    val severity: String,
    @JacksonXmlProperty(isAttribute = true, localName = "attribute_key")
    val attributeKey: String
) {
    // "lazyinit" is the suggested workaround for jackson-module-kotlin bug with @JacksonXmlText:
    // https://github.com/FasterXML/jackson-module-kotlin/issues/138
    @JacksonXmlText
    lateinit var value: String
        private set

    constructor(severity: String, attributeKey: String, value: String) : this(severity, attributeKey) {
        this.value = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProblemClass

        if (severity != other.severity) return false
        if (attributeKey != other.attributeKey) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = severity.hashCode()
        result = 31 * result + attributeKey.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String {
        return "ProblemClass(severity='$severity', attributeKey='$attributeKey', value='$value')"
    }
}

@JacksonXmlRootElement(localName = "hint")
data class Hint(
    @JacksonXmlProperty(isAttribute = true)
    val value: String
)

