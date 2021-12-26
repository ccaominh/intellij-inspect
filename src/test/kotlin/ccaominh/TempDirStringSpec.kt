/*
 * Copyright 2020-2021 Chi Cao Minh
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
import io.kotlintest.TestResult
import io.kotlintest.specs.StringSpec
import java.io.File

/**
 * Utility string spec that provides a temporary directory for each test
 */
open class TempDirStringSpec : StringSpec() {
    lateinit var tempDir: File

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        tempDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)
        tempDir.deleteRecursively()
    }

    /**
     * Create file in temporary directory. Necessary subdirectories are created if needed.
     */
    fun createFile(name: String, contents: String = ""): File {
        val file = File(tempDir, name)
        file.parentFile.mkdirs()
        file.writeText(contents)
        return file
    }

    /**
     * Create empty directory in temporary directory. Necessary subdirectories are created if needed.
     */
    fun createDir(name: String): File {
        val dir = File(tempDir, name)
        dir.mkdirs()
        return dir
    }
}
