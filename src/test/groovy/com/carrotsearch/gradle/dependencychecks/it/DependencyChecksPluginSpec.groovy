/* (C) 2023 */
package com.carrotsearch.gradle.dependencychecks.it


import org.gradle.testkit.runner.TaskOutcome

class DependencyChecksPluginSpec extends AbstractIntegTest {
  def "accepts no configurations on input"() {
    given:
    buildFile(
        """
        plugins {
          id 'com.carrotsearch.gradle.dependencychecks'
        }
    
        dependencyVersionChecks {
          configurationGroups {
          }
        }
        """)

    when:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":writeLocks")
        .run()

    then:
    result.task(":writeLocks").outcome == TaskOutcome.SUCCESS
    lockFileEquals(
        """
        {
          "comment" : "",
          "configurationGroups" : { },
          "because" : { }
        }
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "lockfile collects dependencies into two groups"() {
    given:
    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          api "org.slf4j:slf4j-api:2.0.9"
          testImplementation "junit:junit:4.13.2"
        }

        dependencyVersionChecks {
          configurationGroups {
            group1 {
              include project.configurations.matching { it.name == "compileClasspath" } 
            }
            group2 {
              include project.configurations.matching { it.name in [ "compileClasspath", "testCompileClasspath" ] }
            }
          }
        }
        """)

    when:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":writeLocks", ":checkLocks")
        .build()

    then:
    result.task(":writeLocks").outcome == TaskOutcome.SUCCESS
    lockFileEquals(
        """
        {
          "comment" : "",
          "configurationGroups" : {
            "group1" : {
              "org.slf4j:slf4j-api:2.0.9" : "S000"
            },
            "group2" : {
              "junit:junit:4.13.2" : "S001",
              "org.hamcrest:hamcrest-core:1.3" : "S001",
              "org.slf4j:slf4j-api:2.0.9" : "S002"
            }
          },
          "because" : {
            "S000" : [
              "Configuration compileClasspath"
            ],
            "S001" : [
              "Configuration testCompileClasspath"
            ],
            "S002" : [
              "Configuration compileClasspath",
              "Configuration testCompileClasspath"
            ]
          }
        }
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "lock check fails on removed dependency"() {
    given:
    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          api "org.slf4j:slf4j-api:2.0.9"
        }

        dependencyVersionChecks {
          configurationGroups {
            group {
              include project.configurations.matching { it.name == "compileClasspath" } 
            }
          }
        }
        """)

    lockFile(
        """
        {
          "comment" : "",
          "configurationGroups" : {
            "group" : {
              "org.slf4j:slf4j-api:2.0.9" : "S000",
              "junit:junit:4.13.2" : "S001"
            }
          },
          "because" : {
            "S000" : [
              "Configuration compileClasspath"
            ],
            "S001" : [
              "Configuration compileClasspath",
              "Configuration testCompileClasspath"
            ]
          }
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":checkLocks")
        .forwardOutput()
        .buildAndFail()

    result.task(":checkLocks").outcome == TaskOutcome.FAILED

    containsSubstring(result.output,
        """
        > Dependencies are inconsistent with the lockfile.
            Configuration group: group
                  - junit:junit:4.13.2 (only in lockfile, no longer used)
        """)

    containsSubstring(result.output,
        """
          The following steps may be helpful to resolve the problem:
            - regenerate the lockfile using 'gradlew writeLocks', then use git diff to inspect the changes
            - run 'gradlew dependencyInsight --configuration someConf --dependency someDep' to inspect dependencies
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "lock check fails on added dependency"() {
    given:
    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          api "org.slf4j:slf4j-api:2.0.9"
        }

        dependencyVersionChecks {
          configurationGroups {
            group {
              include project.configurations.matching { it.name == "compileClasspath" } 
            }
          }
        }
        """)

    lockFile(
        """
        {
          "comment" : "",
          "configurationGroups" : {},
          "because" : {}
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":checkLocks")
        .forwardOutput()
        .buildAndFail()

    result.task(":checkLocks").outcome == TaskOutcome.FAILED
    containsSubstring(result.output, """
            > Dependencies are inconsistent with the lockfile.
                Configuration group: group
                      - org.slf4j:slf4j-api:2.0.9 (new dependency)
            """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "lock check fails on dependency with changed configurations"() {
    given:
    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          api "org.slf4j:slf4j-api:2.0.9"
        }

        dependencyVersionChecks {
          configurationGroups {
            group {
              include project.configurations.matching { it.name == "compileClasspath" } 
            }
          }
        }
        """)

    lockFile(
        """
        {
          "comment" : "",
          "configurationGroups" : {
            "group" : {
              "org.slf4j:slf4j-api:2.0.9" : "S000"
            }
          },
          "because" : {
            "S000" : [
              "Configuration runtimeClasspath"
            ]
          }
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":checkLocks")
        .forwardOutput()
        .buildAndFail()

    result.task(":checkLocks").outcome == TaskOutcome.FAILED
    containsSubstring(result.output,
        """
        > Dependencies are inconsistent with the lockfile.
            Configuration group: group
                  - org.slf4j:slf4j-api:2.0.9 (dependency sources different)
                        Configuration runtimeClasspath (removed source)
                        Configuration compileClasspath (new source)
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "should not write inconsistent lock file"() {
    given:

    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          compileOnly "org.slf4j:slf4j-api:2.0.9"
          runtimeOnly "org.slf4j:slf4j-api:2.0.8"
        }

        dependencyVersionChecks {
          configurationGroups {
            group {
              include project.configurations.matching { it.name in [ "compileClasspath", "runtimeClasspath" ]  } 
            }
          }
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":writeLocks")
        .forwardOutput()
        .buildAndFail()

    result.task(":writeLocks").outcome == TaskOutcome.FAILED
    containsSubstring(result.output,
        """
        > Multiple versions of the same dependency found in group 'group':
          
            1) org.slf4j:slf4j-api
                 - version 2.0.8 used by:
                     Configuration runtimeClasspath       
                 - version 2.0.9 used by:
                     Configuration compileClasspath
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "should collect dependencies from subprojects"() {
    given:

    settingsFile(
        """
        rootProject.name = 'test'
        include 'subproject-a'
        include 'subproject-b' 
        """)

    buildFile(
        """
        plugins {
          id 'java-library'
          id 'com.carrotsearch.gradle.dependencychecks' apply false
        }

        allprojects {
            apply plugin: 'java-library'
            apply plugin: 'com.carrotsearch.gradle.dependencychecks'

            repositories {
              mavenCentral()
            }

            dependencyVersionChecks {
              configurationGroups {
                group {
                  include project.configurations.matching { it.name in [ "compileClasspath", "runtimeClasspath" ]  } 
                }
              }
            }
        }

        configure(project(":subproject-a")) {
            dependencies {
              api "org.slf4j:slf4j-api:2.0.9"
            }
        }

        configure(project(":subproject-b")) {
            dependencies {
              runtimeOnly "org.slf4j:slf4j-api:2.0.8"
            }
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":writeLocks")
        .forwardOutput()
        .buildAndFail()

    result.task(":writeLocks").outcome == TaskOutcome.FAILED
    containsSubstring(result.output,
        """
        > Multiple versions of the same dependency found in group 'group':
          
            1) org.slf4j:slf4j-api
                 - version 2.0.8 used by:
                     Configuration runtimeClasspath in :subproject-b
                 - version 2.0.9 used by:
                     Configuration compileClasspath in :subproject-a
                     Configuration runtimeClasspath in :subproject-a
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }
}