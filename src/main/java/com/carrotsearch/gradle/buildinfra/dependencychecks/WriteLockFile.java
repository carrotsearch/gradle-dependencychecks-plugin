package com.carrotsearch.gradle.buildinfra.dependencychecks;

import java.io.IOException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/**
 * Aggregate several resolved configuration groups and write a lock file, checking dependency sanity
 * along the way.
 */
public abstract class WriteLockFile extends AbstractLockFileTask {
  public static final String TASK_NAME = "writeLocks";

  @Optional
  @Input
  public abstract Property<String> getLockFileComment();

  @TaskAction
  public void action() throws IOException {
    DependencyGroups mergedGroups = getMergedDependencyGroups();
    runValidationChecks(mergedGroups);

    mergedGroups.writeTo(getLockFileComment().getOrElse(""), lockFile.get().getAsFile());
  }
}
