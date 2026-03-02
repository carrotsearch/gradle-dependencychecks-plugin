package com.carrotsearch.gradle.buildinfra.dependencychecks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/** Compare aggregated dependencies against a lock file. */
abstract class CheckLocks extends AbstractLockFileTask {
  public static final String TASK_NAME = "checkLocks";

  @Inject
  public CheckLocks() {}

  @TaskAction
  public void action() throws IOException {
    DependencyGroups current = getMergedDependencyGroups();
    runValidationChecks(current);

    var lockFileRef = lockFile.get().getAsFile();
    if (!lockFileRef.isFile()) {
      throw new GradleException(
          String.format(
              Locale.ROOT,
              "Lockfile does not exist: %s, create it using the '%s' task",
              lockFileRef.getAbsolutePath(),
              WriteLockFile.TASK_NAME));
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
                      errors.add(fmt("  - %s (new dependency)", dep.id()));
                    } else if (inCurrent == null) {
                      errors.add(fmt("  - %s (only in lockfile, no longer used)", dep.id()));
                    } else if (!Objects.equals(inLockFile.getVersion(), inCurrent.getVersion())) {
                      errors.add(
                          fmt(
                              "  - %s (version mismatch, lockfile: %s, current: %s)",
                              dep.idWithoutVersion(),
                              inLockFile.getVersion(),
                              inCurrent.getVersion()));
                    } else if (!Objects.equals(inLockFile.sources, inCurrent.sources)) {
                      var inLockFileBecause = new ArrayList<>(inLockFile.sources);
                      var inCurrentBecause = new ArrayList<>(inCurrent.sources);
                      var shared = new ArrayList<>(inLockFileBecause);
                      shared.retainAll(inCurrentBecause);
                      inLockFileBecause.removeAll(shared);
                      inCurrentBecause.removeAll(shared);

                      errors.add(fmt("  - %s (dependency sources different)%n", dep.id()));
                      if (!inLockFileBecause.isEmpty()) {
                        errors.add(
                            inLockFileBecause.stream()
                                    .map(it -> "        " + it + " (removed source)")
                                    .collect(Collectors.joining("\n"))
                                + "\n");
                      }
                      if (!inCurrentBecause.isEmpty()) {
                        errors.add(
                            inCurrentBecause.stream()
                                    .map(it -> "        " + it + " (new source)")
                                    .collect(Collectors.joining("\n"))
                                + "\n");
                      }
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
              buf.append("      " + err + "\n");
            }
          });

      buf.append("\n\nThe following steps may be helpful to resolve the problem:\n");
      buf.append(
          fmt(
              "  - regenerate the lockfile using 'gradlew %s', then use git diff to inspect the"
                  + " changes\n",
              WriteLockFile.TASK_NAME));
      buf.append(
          "  - run 'gradlew dependencyInsight --configuration someConf --dependency someDep' to"
              + " inspect dependencies");

      throw new GradleException(buf.toString());
    }
  }

  public static String fmt(String fmt, Object... args) {
    return String.format(Locale.ROOT, fmt, args);
  }
}
