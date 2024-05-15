package com.carrotsearch.gradle.dependencychecks.it

import org.assertj.core.api.Assertions

class GetResolvedVersionSpec extends AbstractIntegTest {
  def "Should resolve dependency version using explicit coordinates"() {
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
          api platform("junit:junit:4.13.2")
          api "junit:junit"
        }

        task doCheck() {
          dependsOn configurations.runtimeClasspath

          doFirst {
            logger.lifecycle("Hamcrest: " 
                + getResolvedVersion("org.hamcrest:hamcrest-core", configurations.runtimeClasspath))
          }
        }
        """)

    when:
    def result = gradleRunner()
        .withGradleVersion(gradleVersion)
        .withArguments(":doCheck")
        .build()

    then:
    Assertions.assertThat(result.output).contains("Hamcrest: 1.3")

    where:
    gradleVersion << CHECKED_GRADLE_VERSIONS
  }
}