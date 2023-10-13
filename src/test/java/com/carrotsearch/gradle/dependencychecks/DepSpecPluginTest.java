package com.carrotsearch.gradle.dependencychecks;

import com.carrotsearch.randomizedtesting.LifecycleScope;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

public class DepSpecPluginTest extends RandomizedTest {
  @Test
  public void testSpec() throws IOException {
    var testProjectDir = RandomizedTest.newTempDir(LifecycleScope.TEST);
    Files.writeString(
        testProjectDir.resolve("settings.gradle"),
        """
        rootProject.name = 'test'
        """);

    Files.writeString(
        testProjectDir.resolve("build.gradle"),
        """
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
        """);

    GradleRunner.create()
        // .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath()
        .withArguments(":writeLocks")
        .build();
  }
}
