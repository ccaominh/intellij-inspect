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

@file:Suppress("RemoveCurlyBracesFromTemplate")

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.*

plugins {
    kotlin("jvm") version "1.6.10"
    jacoco
    id("com.github.hierynomus.license-base") version "0.16.1"
}

object Versions {
    const val JACKSON = "2.10.1"
    const val KOTEST = "5.0.3"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.JACKSON}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:${Versions.JACKSON}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.JACKSON}")
    implementation("com.github.ajalt.clikt:clikt:3.2.0")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:${Versions.KOTEST}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${Versions.KOTEST}")
    testRuntimeOnly("org.slf4j:slf4j-nop:1.7.30")
}

repositories {
    mavenCentral()
}

object Docker {
    private const val NAME = "intellij-inspect"
    private const val TAG = "latest"
    const val IMAGE = "${NAME}:${TAG}"
}

object Directory {
    const val SOURCE = "src"
    const val MAIN = "${SOURCE}/main"
    const val DOCKER = "${SOURCE}/docker"
    const val INTEGRATION_TEST = "integration-test"
}

object Files {
    const val HEADER = "HEADER"
    const val DOCKERFILE = "${Directory.DOCKER}/Dockerfile"
}

sourceSets.create("docker").java {
    srcDir("src/docker")
}

sourceSets.create("gradle").java {
    srcDir(".")
    include("**/*.gradle.kts")
}

sourceSets.create("integrationTest").java {
    srcDir("integration-test")
    include(listOf(
        "**/*.gradle",
        "**/*.java",
        "**/*.xml"
    ))
    exclude("**/.idea/*")
    exclude("**/build")
    exclude("**/inspectionProfile.xml")
    exclude("**/target")
}


val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "ccaominh.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Make fat jar
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter {
            it.name.endsWith("jar")
        }.map {
            zipTree(it)
        }
    })
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}


val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}


tasks.assemble {
    dependsOn(dockerBuild)
}


val dockerBuild by tasks.registering(Exec::class) {
    dependsOn(hadolint)

    group = "Build"
    description = "Build docker image."
    commandLine = listOf("docker", "build", "-f", Files.DOCKERFILE, ".", "-t", Docker.IMAGE)

    inputs.files(fileTree(file(Directory.SOURCE)))
            .withPropertyName("input")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(file("${buildDir}/reports/${name}.out"))
            .withPropertyName("output")
}


val hadolint by tasks.registering(Exec::class) {
    group = "Verification"
    description = "Run hadolint."
    commandLine = listOf("docker", "run", "--rm", "-i", "hadolint/hadolint:v2.8.0")

    inputs.files(file(Files.DOCKERFILE))
            .withPropertyName("input")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(file("${buildDir}/reports/${name}.out"))
            .withPropertyName("output")

    doFirst {
        standardInput = FileInputStream(file(Files.DOCKERFILE))
    }
}


configure<LicenseExtension> {
    header = project.file(Files.HEADER)
    mapping(mapOf(
        "Dockerfile" to "SCRIPT_STYLE",
        "gradle" to "SLASHSTAR_STYLE",
        "kts" to "SLASHSTAR_STYLE"
    ))
}


val licenseCheck by tasks.registering {
    description = "Check license header on all files"

    dependsOn(project.tasks.matching { task ->
        task.name.startsWith("license")
                && task != this
                && !task.name.startsWith("licenseFormat")
    })
}


val licenseCheckHeader by tasks.registering {
    description = "Check license header year is up-to-date"

    inputs.files(file(Files.HEADER))
            .withPropertyName("input")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(file("${buildDir}/reports/${name}.out"))
            .withPropertyName("output")

    doLast {
        val baseYear = "2020"
        val currYear = Calendar.getInstance().get(Calendar.YEAR).toString()
        val year = if (baseYear == currYear) baseYear else "${baseYear}-${currYear}"

        val copyright = file(Files.HEADER).useLines { it.first() }
        val regex = Regex("""Copyright ([-\d]+) .+""")
        val result = regex.matchEntire(copyright)

        val copyrightYear = result?.groupValues?.getOrNull(1)
        if (copyrightYear != year) {
            throw GradleException("'${Files.HEADER}' has invalid copyright year: '${copyrightYear}' should be '${year}'")
        }
    }
}


val licenseDockerfile by tasks.registering {
    inputs.files(file(Files.DOCKERFILE))
            .withPropertyName("input")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(file("${buildDir}/reports/${name}.out"))
            .withPropertyName("output")

    doLast {
        val headerLinesList = file(Files.HEADER).readLines()
        val numLine = headerLinesList.size
        val headerLines = headerLinesList.joinToString("\n")
        val dockerfileLines = file(Files.DOCKERFILE)
                .readLines()
                .subList(0, numLine)
                .joinToString("\n")
                .trimMargin("#")
                .trimIndent()
        if (headerLines != dockerfileLines) {
            throw GradleException("License violations were found: " + file(Files.DOCKERFILE).absolutePath)
        }
    }
}


tasks.getByName("licenseDocker") {
    dependsOn(licenseDockerfile)
}


project.tasks.matching { task ->
    task.name.startsWith("license") && task.name != "licenseCheckHeader"
}.forEach { task ->
    task.dependsOn(licenseCheckHeader)
}


tasks.test {
    useJUnitPlatform()
}


val testIntegration by tasks.registering {
    group = "Verification"
    description = "Run the integration tests."
    dependsOn(project.tasks.matching { task ->
        task.name.startsWith("testIntegration") && task != this
    })
}


// Create integration test task for each test project
file(Directory.INTEGRATION_TEST).listFiles(File::isDirectory)!!.forEach { test ->
    val root = "/project"
    val isClean = test.name.endsWith("clean")
    val isScope = test.name.startsWith("scope")
    val isGradle = test.name.startsWith("gradle")
    val isIdea = test.name.startsWith("idea") || isScope
    val isMaven = test.name.startsWith("maven")

    val inspectionProfile = if (isIdea) ".idea/inspectionProfiles/Project_Default.xml" else "inspectionProfile.xml"
    val project = root + when {
        isGradle -> "/build.gradle"
        isMaven -> "/pom.xml"
        else -> ""
    }

    val command = mutableListOf(
        "docker", "run",
        "--rm",
        "-v", "${test.absolutePath}:/project",
        Docker.IMAGE,
        project,
        "${root}/${inspectionProfile}"
    )
    if (isScope) {
        command.addAll(listOf("--scope", "Main"))
    }

    tasks.register<Exec>(getIntegrationTestTaskName(test.name)) {
        dependsOn(dockerBuild)

        group = "Verification"
        description = "Run integration test for ${test.name} project."
        commandLine = command
        isIgnoreExitValue = true
        standardOutput = ByteArrayOutputStream()
        errorOutput = ByteArrayOutputStream()

        inputs.files(fileTree(file(Directory.MAIN))
                .plus(fileTree(file(Directory.DOCKER)))
                .plus(fileTree(test)))
                .withPropertyName("input")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        val outputDir = file("${buildDir}/test/${test.name}")
        outputs.dir(outputDir)
                .withPropertyName("output")

        doFirst {
            logger.lifecycle("Testing ${test.name} project...")
        }

        doLast {
            val expected = parseExpected("${test.absolutePath}/src/test/resources/expected.json")
            val actual = parseOutput(standardOutput.toString())
            val actualString = JsonOutput.prettyPrint(JsonOutput.toJson(actual))
            if (expected == actual) {
                file("${outputDir.absolutePath}/actual.json").writeText(actualString)
            } else {
                throw GradleException(
                    """
                    |Expected:
                    |${JsonOutput.prettyPrint(JsonOutput.toJson(expected))}
                    |
                    |Actual:
                    |${actualString}
                    |
                    |Output:
                    |${standardOutput}
                    |
                    |Error:
                    |${errorOutput}
                    """.trimMargin()
                )
            }

            // NOTE: Benign errors printed by IntelliJ IDEA 2019+
            val stderr = errorOutput.toString()
            if (stderr.isNotEmpty()) {
                throw GradleException("Output on stderr:\n${stderr}")
            }

            val expectedExitCode = if (isClean) 0 else 1
            val actualExitCode = execResult!!.exitValue
            if (actualExitCode != expectedExitCode) {
                throw GradleException(
                    """
                    |Expected exit code ${expectedExitCode} but was ${actualExitCode}"
                    |
                    |Output:
                    |${standardOutput}
                    |
                    |Error:
                    |${errorOutput}
                    """.trimMargin()
                )
            }
        }
    }
}


fun getIntegrationTestTaskName(testName: String): String {
  return "testIntegration" + testName.split('-').joinToString("") { it.capitalize() }
}


fun parseExpected(expected: String): Map<String, Map<String, String>> {
    @Suppress("UNCHECKED_CAST")
    return JsonSlurper().parse(file(expected)) as Map<String, Map<String, String>>
}


fun parseOutput(output: String): Map<String, Map<String, String>> {
    val matchers = """([\w/.:-]+) -- (.+)"""
    val errorRegex = Regex("""\[ERROR\] ${matchers}""")
    val warningRegex = Regex("""\[WARNING\] ${matchers}""")
    val errors = "errors"
    val warnings = "warnings"
    val result = hashMapOf<String, MutableMap<String, String>>(
        errors to hashMapOf(),
        warnings to hashMapOf()
    )

    logger.info("Integration test output:\n${output}")

    for (line in output.lines()) {
        val errorMatchResult = errorRegex.matchEntire(line)
        if (errorMatchResult != null) {
            val (location, message) = errorMatchResult.destructured
            result[errors]?.set(location, message)
            continue
        }

        val warningMatchResult = warningRegex.matchEntire(line)
        if (warningMatchResult != null) {
            val (location, message) = warningMatchResult.destructured
            result[warnings]?.set(location, message)
            continue
        }
    }

    return result
}


val inspect by tasks.registering(Exec::class) {
    dependsOn(dockerBuild)

    group = "Verification"
    description = "Run IntelliJ inspection analysis."
    commandLine = listOf(
        "docker", "run",
        "--rm",
        "-v", "${rootDir.absolutePath}:/project",
        Docker.IMAGE,
        "/project/build.gradle.kts",
        "/project/.idea/inspectionProfiles/Project_Default.xml",
        "-d", "/project/src",
        "-o", "/project/build/inspect/"
    )

    inputs.files(fileTree(file(Directory.SOURCE)).plus(fileTree(file(Directory.DOCKER))))
            .withPropertyName("input")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(file("${buildDir}/inspect"))
            .withPropertyName("output")
}


tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(licenseCheck)
    dependsOn(testIntegration)
    dependsOn(inspect)
}


tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule { limit { counter = "BRANCH";      minimum = 0.88.toBigDecimal() } }
        rule { limit { counter = "COMPLEXITY";  minimum = 0.77.toBigDecimal() } }
        rule { limit { counter = "INSTRUCTION"; minimum = 0.85.toBigDecimal() } }
        rule { limit { counter = "LINE";        minimum = 0.88.toBigDecimal() } }
        rule { limit { counter = "METHOD";      minimum = 0.73.toBigDecimal() } }
    }
}
