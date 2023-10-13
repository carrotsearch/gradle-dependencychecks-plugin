package com.carrotsearch.gradle.dependencychecks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

/** Compare aggregated dependencies against a lock file. */
abstract class CheckLocks extends AbstractLockFileTask {
  public static final String TASK_NAME = "checkLocks";

  @Inject
  public CheckLocks() {
  }

  @TaskAction
  public void action() throws IOException {
    DependencyGroups current = getMergedDependencyGroups();
    runValidationChecks(current);

    var lockFileRef = lockFile.get().getAsFile();
    if (!lockFileRef.isFile()) {
      throw new GradleException(
          "Lockfile does not exist: ${lockFileRef}, create it using the '${WriteLockFile.TASK_NAME}' task");
    }
    DependencyGroups fromLockFile = DependencyGroups.readFrom(lockFileRef);
    runValidationChecks(fromLockFile);

    // Compare actual and expected.
    DependencyGroups combined = new DependencyGroups();
    combined.merge(current);
    combined.merge(fromLockFile);

    TreeMap<String, List<String>> groupErrors = new TreeMap<>();
    combined
        .getDependencies()
        .forEach(
            (groupName, deps) -> {
              List<String> errors = new ArrayList<>();

              deps.forEach(
                  dep -> {
                    DependencyInfo inLockFile = fromLockFile.getIfExists(groupName, dep);
                    DependencyInfo inCurrent = current.getIfExists(groupName, dep);

                    if (inLockFile == null) {
                      errors.add("  - ${dep.id()} (new dependency)");
                    } else if (inCurrent == null) {
                      errors.add("  - ${dep.id()} (only in lockfile, no longer used)");
                    } else if (!Objects.equals(inLockFile.getVersion(), inCurrent.getVersion())) {
                      errors.add(
                          "  - ${dep.idWithoutVersion()} (version mismatch, lockfile: ${inLockFile.version}, current: ${inCurrent.version})");
                    } else if (!Objects.equals(inLockFile.because, inCurrent.because)) {
                      var inLockFileBecause = new ArrayList<>(inLockFile.because);
                      var inCurrentBecause = new ArrayList<>(inCurrent.because);
                      var shared = new ArrayList<>(inLockFileBecause);
                      shared.retainAll(inCurrentBecause);
                      inLockFileBecause.removeAll(shared);
                      inCurrentBecause.removeAll(shared);

                      errors.add(
                          "  - ${dep.id()} (dependency sources different)\n"
                              + inLockFileBecause.stream()
                                  .map(it -> "            ${it} (removed source)")
                                  .collect(Collectors.joining("\n"))
                              + inCurrentBecause.stream()
                                  .map(it -> "            ${it} (new source)")
                                  .collect(Collectors.joining("\n")));
                    }
                  });

              if (!errors.isEmpty()) {
                groupErrors.put(groupName, errors);
              }
            });

    if (!groupErrors.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append("Dependencies are inconsistent with the lockfile.\n");
      groupErrors.forEach(
          (groupName, errors) -> {
            buf.append("  Configuration group: " + groupName + "\n");
            for (var err : errors) {
              buf.append("      " + err);
            }
          });

      buf.append("\n\nThe following steps may be helpful to resolve the problem:\n");
      buf.append(
          "  - regenerate the lockfile using 'gradlew ${WriteLockFile.TASK_NAME}, then use git diff to inspect the changes\n");
      buf.append(
          "  - run 'gradlew dependencyInsight --configuration someConf --dependency someDep' to inspect dependencies");

      throw new GradleException(buf.toString());
    }
  }
}
