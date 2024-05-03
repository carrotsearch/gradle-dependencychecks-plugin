package com.carrotsearch.gradle.dependencychecks.it

import java.util.stream.Stream
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
        .isEqualToNormalizingNewlines(expected.stripIndent().trim())
  }

  void containsLines(String result, String substring) {
    if (!normalizeLines(result).contains( normalizeLines(substring.trim()))) {
      Assertions.fail(String.format(Locale.ROOT,
          "Expecting:%n%n%s%n%nin the following:%n%n%s", substring, result))
    }
  }

  private static String normalizeLines(String input) {
    return input.split().collect { line -> line.trim() }.join("\n")
  }
}
