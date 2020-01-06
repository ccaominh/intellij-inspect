![Travis (.org)](https://img.shields.io/travis/ccaominh/intellij-inspect)

# IntelliJ Inspect

Docker container for running [IntelliJ IDEA code inspections](https://www.jetbrains.com/help/idea/code-inspection.html). If the analysis detects inspection violations, a human-readable report is printed and an exit code of 1 is returned. Otherwise, an exit code of 0 is returned if the analysis does not detect any inspection violations.

## Usage

```
[OPTIONS] PROJECT PROFILE

Options:
  -d, --directory TEXT  Absolute path to directory within project to be
                        inspected
  -l, --levels TEXT     Inspection severity levels to analyze (default:
                        ERROR,WARNING)
  -o, --output TEXT     Absolute path to output inspection analysis results
                        (default: inspection-results)
  -s, --scope TEXT      Name of IntelliJ scope to use
  -h, --help            Show this message and exit

Arguments:
  PROJECT   Absolute path to IntelliJ project directory, pom.xml, or
            build.gradle, etc. to analyze
  PROFILE   Absolute path to inspection profile to use
```

Note that _absolute_ paths must be specified.

## Examples

These examples assume the working directory is the project to analyze.

1. Run analysis on IntelliJ IDEA project (i.e., contains `.idea` directory and `.iml` files):
   ```
   docker run --rm \
       -v $(pwd):/project \
       ccaominh/intellij-inspect:1.0.0 \
       /project \
       /project/.idea/inspectionProfiles/Project_Default.xml
   ```

2. Example analysis output with violations reported:
   ```
   Starting up IntelliJ IDEA 2018.3 (build IC-183.4284.148) ...done.
   Opening project...done.
   Initializing project...Loaded profile 'Project Default' from file '/project/.idea/inspectionProfiles/Project_Default.xml'
   done.
   Inspecting with profile 'Project Default'
   Scanning scope ...


   Processing project usages in .../src/main/java/com/company/Main.java [dirty]
   Processing project usages in .../src/test/resources/expected.json [dirty]
   Analyzing code in .../src/test/resources/expected.json [dirty]
   Analyzing code in .../src/main/java/com/company/Main.java [dirty]

   Done.

   [WARNING] src/main/java/com/company/Main.java:21 -- Unnecessary semicolon <code>;</code> #loc
   [WARNING] src/main/java/com/company/Main.java:19 -- Can be package-private
   [ERROR] src/main/java/com/company/Main.java:17 -- Package name 'org.company' does not correspond to the file path 'com.company'
   ```

3. Run analysis on Maven project (after doing `mvn install` on host):
   ```
   docker run --rm \
       -v "$(pwd):/project" \
       -v ~/.m2:/home/inspect/.m2 \
       ccaominh/intellij-inspect:1.0.0 \
       /project/pom.xml \
       /project/intellij_inspection_profile.xml
   ```

4. Run analysis on Gradle project:
   ```
   docker run --rm \
       -v "$(pwd):/project" \
       ccaominh/intellij-inspect:1.0.0 \
       /project/build.gradle \
       /project/intellij_inspection_profile.xml
   ```

5. Run analysis only for `ERROR` inspection severity level:
   ```
   docker run --rm \
       -v "$(pwd):/project" \
       ccaominh/intellij-inspect:1.0.0 \
       /project \
       /project/.idea/inspectionProfiles/Project_Default.xml \
       --levels ERROR
   ```

6. Run analysis for particular directory (e.g., `src/main/java`) within project:
   ```
   docker run --rm \
       -v "$(pwd):/project" \
       ccaominh/intellij-inspect:1.0.0 \
       /project \
       /project/.idea/inspectionProfiles/Project_Default.xml \
       --directory /project/src/main/java
   ```

7. Run analysis for a particular [scope](https://www.jetbrains.com/help/idea/settings-scopes.html):
   ```
   docker run --rm \
       -v "$(pwd):/project" \
       ccaominh/intellij-inspect:1.0.0 \
       /project \
       /project/.idea/inspectionProfiles/Project_Default.xml \
       --scope MY_SCOPE
   ```

8. Output raw inspection analysis results to `build/inspect` directory (e.g., for [viewing the results of an offline inspection](https://www.jetbrains.com/help/idea/command-line-code-inspector.html#offline) in IntelliJ IDEA later):
    ```
    docker run --rm \
        -v "$(pwd):/project" \
        -v "$(pwd)/build/inspect:/inspect" \
        ccaominh/intellij-inspect:1.0.0 \
        /project \
        /project/.idea/inspectionProfiles/Project_Default.xml \
        --output /inspect
    ```
