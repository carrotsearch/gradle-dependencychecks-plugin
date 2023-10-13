/* (C) 2023 */
package com.carrotsearch.gradle.dependencychecks.it

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

abstract class AbstractIntegTest extends Specification {
  final static CHECKED_GRADLE_VERSIONS = [
    "8.4"
  ]

  @TempDir
  protected File testProjectDir

  private File settingsFile
  private File buildFile
  private File lockFile

  private GradleRunner runner

  protected void setup() {
    settingsFile = new File(testProjectDir, 'settings.gradle')
    settingsFile("rootProject.name = 'test'")

    buildFile = new File(testProjectDir, 'build.gradle')
    lockFile = new File(testProjectDir, 'versions.lock')
  }

  void settingsFile(String text) {
    settingsFile.setText(text.stripLeading(), "UTF-8")
  }

  void buildFile(String text) {
    buildFile.setText(text.stripLeading(), "UTF-8")
  }

  void lockFile(String text) {
    lockFile.setText(text.stripLeading(), "UTF-8")
  }

  GradleRunner gradleRunner() {
    if (runner == null) {
      runner = GradleRunner.create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
    }
    return runner
  }

  void lockFileEquals(String expected) {
    Assertions.assertThat(lockFile)
        .content()
        .isEqualToIgnoringWhitespace(expected)
  }

  void containsSubstring(String result, String substring) {
    Assertions.assertThat(result)
        .containsIgnoringWhitespaces(substring)
  }
}
