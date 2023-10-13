package com.carrotsearch.gradle.dependencychecks;

import com.carrotsearch.randomizedtesting.LifecycleScope;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import org.assertj.core.api.Assertions;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class DepSpecPluginTest extends RandomizedTest {
  private static final String gradleVersion = "8.4";
  
  @Test
  public void testMinimal() throws IOException {
    Path projectDir =
        createProjectFiles(
            Map.ofEntries(
                Map.entry(
                    "settings.gradle",
                    """
                    rootProject.name = 'test'
                    """),
                Map.entry(
                    "build.gradle",
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
                    """)));

    BuildResult outcome =
        GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":writeLocks")
            .build();

    Assertions.assertThat(outcome.task(":writeLocks").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
  }

  private static Path createProjectFiles(Map<String, String> projectFiles) throws IOException {
    var dir = RandomizedTest.newTempDir(LifecycleScope.TEST);
    for (var e : projectFiles.entrySet()) {
      Path file = dir.resolve(e.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, e.getValue());
    }
    return dir;
  }
}
