package com.carrotsearch.gradle.dependencychecks;

import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * This plugin adds dependency-tracking functionality similar to palantir-consistent-versions, but:
 *
 * <p>- global resolution of dependencies across all configurations (gradle mechanisms must be used
 * to make versions consistent),
 *
 * <p>- the "lock file" (versions.lock) is for tracking unintended changes (it does not lock
 * versions but will fail if the actual dependencies are different from the ones in the lockfile).
 */
public final class DependencyChecksPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    // Apply the extension.
    var extension =
        project
            .getExtensions()
            .create(
                DependencyVersionChecksExtension.EXTENSION_NAME,
                DependencyVersionChecksExtension.class);

    // Register internal resolution tasks,
    project
        .getTasks()
        .register(ResolveConfigurationGroups.TASK_NAME, ResolveConfigurationGroups.class)
        .configure(
            (task) -> {
              // link up internal tasks with the project's extension configuration.
              task.getConfigurationGroups()
                  .addAllLater(project.provider(extension::getConfigurationGroups));
            });

    if (project.getRootProject() != project) {
      project.getRootProject().getPlugins().apply(DependencyChecksPlugin.class);
    } else {
      var resolutionTasks =
          project.getAllprojects().stream()
              .map(
                  prj ->
                      prj.getTasks()
                          .matching(
                              task -> task.getName().equals(ResolveConfigurationGroups.TASK_NAME)))
              .collect(Collectors.toList());

      // register lock file - related tasks and link them up to the default resolution tasks.
      var depCheckExt = project.getExtensions().getByType(DependencyVersionChecksExtension.class);
      var writeLocksTask =
          project.getTasks().register(WriteLockFile.TASK_NAME, WriteLockFile.class);
      writeLocksTask.configure(
          (task) -> {
            task.getLockFileComment().convention(depCheckExt.getLockFileComment());
            task.getResolvedConfigurationGroups().from(resolutionTasks);
            task.lockFile.value(project.getLayout().getProjectDirectory().file("versions.lock"));
          });

      var checkLocksTask = project.getTasks().register(CheckLocks.TASK_NAME, CheckLocks.class);
      checkLocksTask.configure(
          (task) -> {
            task.mustRunAfter(writeLocksTask);
            task.getResolvedConfigurationGroups().from(resolutionTasks);
            task.lockFile.value(project.getLayout().getProjectDirectory().file("versions.lock"));
          });

      project
          .getTasks()
          .matching(it -> it.getName().equals("check"))
          .configureEach(it -> it.dependsOn(checkLocksTask));
    }
  }
}
