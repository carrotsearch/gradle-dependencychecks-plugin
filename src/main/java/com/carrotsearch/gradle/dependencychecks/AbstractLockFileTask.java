package com.carrotsearch.gradle.dependencychecks;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
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

              if (inconsistentGroups.size() > 1) {
                StringBuilder buf = new StringBuilder();
                for (int index = 0; index < inconsistentGroups.size(); index++) {
                  var list = inconsistentGroups.get(index);
                  buf.append(
                      String.format(
                          Locale.ROOT, "  %s) %s%n", index + 1, list.get(0).idWithoutVersion()));
                  for (var dep : list) {
                    buf.append(
                        String.format(
                            Locale.ROOT,
                            "       - version %s used by:%n%s",
                            dep.getVersion(),
                            dep.because.stream()
                                .map(v -> "           ${it}")
                                .collect(Collectors.joining("\n"))));
                    buf.append("%n");
                  }
                }
              }
            });
  }
}
