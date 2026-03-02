package com.carrotsearch.gradle.buildinfra.dependencychecks;

import static com.carrotsearch.gradle.buildinfra.dependencychecks.CheckLocks.fmt;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;

/** Parent class for lock file tasks. */
abstract class AbstractLockFileTask extends DefaultTask {
  @InputFiles
  public abstract ConfigurableFileCollection getResolvedConfigurationGroups();

  @OutputFile final RegularFileProperty lockFile = getProject().getObjects().fileProperty();

  @Internal
  protected DependencyGroups getMergedDependencyGroups() throws IOException {
    DependencyGroups merged = new DependencyGroups();
    for (var group : getResolvedConfigurationGroups()) {
      merged.merge(DependencyGroups.readFrom(group));
    }
    return merged;
  }

  protected void runValidationChecks(DependencyGroups mergedGroups) {
    checkConsistentVersions(mergedGroups);
  }

  /**
   * Check that there are no group:module pairs with different versions within each group, if so,
   * fail (inconsistent versions detected).
   */
  private void checkConsistentVersions(DependencyGroups mergedGroups) {
    mergedGroups
        .getDependencies()
        .forEach(
            (groupName, dependencies) -> {
              List<List<DependencyInfo>> inconsistentGroups =
                  dependencies.stream()
                      .collect(Collectors.groupingBy(DependencyInfo::idWithoutVersion))
                      .values()
                      .stream()
                      .filter(list -> list.size() > 1)
                      .collect(Collectors.toList());

              if (!inconsistentGroups.isEmpty()) {
                StringBuilder buf = new StringBuilder();
                buf.append(
                    fmt(
                        "Multiple versions of the same dependency found in group '%s':\n\n",
                        groupName));

                for (int index = 0; index < inconsistentGroups.size(); index++) {
                  List<DependencyInfo> list = inconsistentGroups.get(index);
                  String artifactCoords = list.get(0).idWithoutVersion();

                  buf.append(fmt("  %s) %s%n", index + 1, artifactCoords));
                  for (var dep : list) {
                    buf.append(
                        fmt(
                            "       - version %s used by:%n%s",
                            dep.getVersion(),
                            dep.sources.stream()
                                .map(v -> "           " + v)
                                .collect(Collectors.joining("\n"))));
                    buf.append("\n");
                  }

                  buf.append(fmt("     more insight into these dependencies:%n"));

                  for (var dep : list) {
                    dep.sources.forEach(
                        v -> {
                          buf.append(
                              fmt(
                                  "       gradlew %s --dependency \"%s\" --configuration \"%s\"%n",
                                  v.projectTask("dependencyInsight"),
                                  artifactCoords,
                                  v.configuration()));
                        });
                  }
                  buf.append("\n");
                }

                throw new GradleException(buf.toString());
              }
            });
  }
}
