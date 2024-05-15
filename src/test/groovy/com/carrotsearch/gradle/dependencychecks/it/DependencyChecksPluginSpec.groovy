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
        .withArguments(":writeLocks", ":checkLocks", "--stacktrace")
        .build()

    then:
    result.task(":writeLocks").outcome == TaskOutcome.SUCCESS
    lockFileEquals(
        """
        {
          "comment" : "",
          "configurationGroups" : {
            "group1" : {
              "org.slf4j:slf4j-api:2.0.9" : "4ab9f4ef,refs=1"
            },
            "group2" : {
              "junit:junit:4.13.2" : "c34b667d,refs=1",
              "org.hamcrest:hamcrest-core:1.3" : "c34b667d,refs=1",
              "org.slf4j:slf4j-api:2.0.9" : "cfd00f4f,refs=2"
            }
          },
          "because" : {
            "4ab9f4ef" : [
              {
                "configuration" : "compileClasspath",
                "projectPath" : ":"
              }
            ],
            "c34b667d" : [
              {
                "configuration" : "testCompileClasspath",
                "projectPath" : ":"
              }
            ],
            "cfd00f4f" : [
              {
                "configuration" : "compileClasspath",
                "projectPath" : ":"
              },
              {
                "configuration" : "testCompileClasspath",
                "projectPath" : ":"
              }
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
              "org.slf4j:slf4j-api:2.0.9" : "S000,ref=1",
              "junit:junit:4.13.2" : "S001,ref=2",
              "org:foo:1.0.0" : "S001,ref=2"
            }
          },
          "because" : {
            "S000" : [
              {
                "configuration" : "compileClasspath",
                "projectPath" : ":"
              }
            ],
            "S001" : [
              {
                "configuration" : "compileClasspath",
                "projectPath" : ":"
              },
              {
                "configuration" : "testCompileClasspath",
                "projectPath" : ":"
              }
            ]
          }
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":checkLocks", "--stacktrace")
        .forwardOutput()
        .buildAndFail()

    result.task(":checkLocks").outcome == TaskOutcome.FAILED

    containsLines(result.output,
        """
        > Dependencies are inconsistent with the lockfile.
            Configuration group: group
                  - junit:junit:4.13.2 (only in lockfile, no longer used)
                  - org:foo:1.0.0 (only in lockfile, no longer used)
        """)

    containsLines(result.output,
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
    containsLines(result.output, """
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
              "org.slf4j:slf4j-api:2.0.9" : "S000,refs=1"
            }
          },
          "because" : {
            "S000" : [
              {
                "configuration" : "runtimeClasspath",
                "projectPath" : ":"
              }
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
    containsLines(result.output,
        """
        > Dependencies are inconsistent with the lockfile.
            Configuration group: group
                  - org.slf4j:slf4j-api:2.0.9 (dependency sources different)
                        Configuration runtimeClasspath in root project (removed source)
                        Configuration compileClasspath in root project (new source)
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
    containsLines(result.output,
        """
        > Multiple versions of the same dependency found in group 'group':
          
            1) org.slf4j:slf4j-api
                 - version 2.0.8 used by:
                     Configuration runtimeClasspath in root project
                 - version 2.0.9 used by:
                     Configuration compileClasspath in root project
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "should display version conflicts in subprojects"() {
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
              api "org.apache.lucene:lucene-core:9.10.0"
            }
        }

        configure(project(":subproject-b")) {
            dependencies {
              runtimeOnly "org.slf4j:slf4j-api:2.0.8"
              api "org.apache.lucene:lucene-core:9.9.0"
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
    containsLines(result.output,
        """
        > Multiple versions of the same dependency found in group 'group':
          
            1) org.apache.lucene:lucene-core
                 - version 9.10.0 used by:
                     Configuration compileClasspath in :subproject-a
                     Configuration runtimeClasspath in :subproject-a
                 - version 9.9.0 used by:
                     Configuration compileClasspath in :subproject-b
                     Configuration runtimeClasspath in :subproject-b
               more insight into these dependencies:
                 gradlew :subproject-a:dependencyInsight --dependency "org.apache.lucene:lucene-core" --configuration "compileClasspath"
                 gradlew :subproject-a:dependencyInsight --dependency "org.apache.lucene:lucene-core" --configuration "runtimeClasspath"
                 gradlew :subproject-b:dependencyInsight --dependency "org.apache.lucene:lucene-core" --configuration "compileClasspath"
                 gradlew :subproject-b:dependencyInsight --dependency "org.apache.lucene:lucene-core" --configuration "runtimeClasspath"
          
            2) org.slf4j:slf4j-api
                 - version 2.0.8 used by:
                     Configuration runtimeClasspath in :subproject-b
                 - version 2.0.9 used by:
                     Configuration compileClasspath in :subproject-a
                     Configuration runtimeClasspath in :subproject-a
               more insight into these dependencies:
                 gradlew :subproject-b:dependencyInsight --dependency "org.slf4j:slf4j-api" --configuration "runtimeClasspath"
                 gradlew :subproject-a:dependencyInsight --dependency "org.slf4j:slf4j-api" --configuration "compileClasspath"
                 gradlew :subproject-a:dependencyInsight --dependency "org.slf4j:slf4j-api" --configuration "runtimeClasspath"
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }

  def "should fail on --write-locks without writeLocks"() {
    given:
    buildFile(
        """
        plugins {
          id 'com.carrotsearch.gradle.dependencychecks'
        }
        """)

    expect:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments("--write-locks")
        .forwardOutput()
        .buildAndFail()

    containsLines(result.output,
        """
        Use the ':writeLocks' task to write the lock file
        """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }
}