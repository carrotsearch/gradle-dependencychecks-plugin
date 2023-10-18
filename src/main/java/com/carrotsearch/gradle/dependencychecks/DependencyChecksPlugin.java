package com.carrotsearch.gradle.dependencychecks;

import groovy.lang.Closure;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;

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

    // Add getResolvedVersion.
    project
        .getExtensions()
        .getExtraProperties()
        .set(
            "getResolvedVersion",
            new Closure<String>(project) {
              public String doCall(String groupModulePair, Configuration configuration) {
                String[] splits = groupModulePair.split(":");
                if (splits.length != 2) {
                  throw new GradleException(
                      String.format(
                          Locale.ROOT, "Expected 'group:name' notation: %s", groupModulePair));
                }
                return doCall(splits[0], splits[1], configuration);
              }

              public String doCall(String group, String module, Configuration configuration) {
                List<ModuleVersionIdentifier> moduleVersionList =
                    configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .map(ResolvedComponentResult::getModuleVersion)
                        .filter(
                            item ->
                                Objects.equals(item.getGroup(), group)
                                    && Objects.equals(item.getName(), module))
                        .collect(Collectors.toList());

                switch (moduleVersionList.size()) {
                  case 0:
                    throw new GradleException(
                        String.format(
                            Locale.ROOT,
                            "Configuration %s does not contain any reference to %s:%s",
                            configuration.getName(),
                            group,
                            module));

                  case 1:
                    return moduleVersionList.get(0).getVersion();

                  default:
                    throw new GradleException(
                        String.format(
                            Locale.ROOT,
                            "Configuration %s contains multiple modules matching %s:%s",
                            configuration.getName(),
                            group,
                            module));
                }
              }

              public String doCall(
                  ModuleVersionSelector moduleSelector, Configuration configuration) {
                return doCall(moduleSelector.getModule(), configuration);
              }

              public String doCall(ModuleIdentifier moduleSelector, Configuration configuration) {
                return doCall(moduleSelector.getGroup(), moduleSelector.getName(), configuration);
              }
            });

    // We can't force writeLocks to run, but we can make --write-locks fail if it's not scheduled.
    project
        .getGradle()
        .getTaskGraph()
        .whenReady(
            graph -> {
              StartParameter startParameter = project.getGradle().getStartParameter();
              if (startParameter.isWriteDependencyLocks()
                  && !graph.hasTask(":" + WriteLockFile.TASK_NAME)) {
                throw new GradleException(
                    "Use the ':"
                        + WriteLockFile.TASK_NAME
                        + "' task to write the lock file, "
                        + "'--write-locks' along is not sufficient.");
              }
            });

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
