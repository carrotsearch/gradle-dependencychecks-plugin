package com.carrotsearch.gradle.dependencychecks.it

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class DependencyChecksPluginSpec extends Specification {
  // static final CHECKED_GRADLE_VERSIONS = ["7.6.3", "8.4"]
  static final CHECKED_GRADLE_VERSIONS = ["8.4"]

  @TempDir
  File testProjectDir
  File settingsFile
  File buildFile

  void setup() {
    settingsFile = new File(testProjectDir, 'settings.gradle')
    settingsFile << """
      rootProject.name = 'test'
    """.stripLeading()

    buildFile = new File(testProjectDir, 'build.gradle')
  }
  
  def "extension accepts configuration groups"() {
    given:
    buildFile << """
        plugins {
          id 'com.carrotsearch.gradle.dependencychecks'
        }

        configurations {
          foo
          bar
        }

        dependencyVersionChecks {
          configurationGroups {
            api {
              include configurations.matching { it.name == "foo" }
            }
          }
        }
        """.stripLeading()

    when:
    def result = GradleRunner.create()
        // .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(':writeLocks')
        .build()

    then:
    result.task(":writeLocks").outcome == TaskOutcome.SUCCESS
    Assertions.assertThat(testProjectDir.toPath().resolve("versions.lock"))
            .content()
    .isEqualToIgnoringWhitespace("""
foo
    """)

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }
}