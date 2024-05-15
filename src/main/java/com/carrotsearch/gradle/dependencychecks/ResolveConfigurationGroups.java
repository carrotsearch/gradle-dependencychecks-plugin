package com.carrotsearch.gradle.dependencychecks;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class ResolveConfigurationGroups extends DefaultTask {
  public static final String TASK_NAME = "resolveConfigurationGroups";

  @Internal
  abstract NamedDomainObjectContainer<ConfigurationGroup> getConfigurationGroups();

  /** The location of an output file to write the configuration group report to. */
  private final RegularFileProperty output =
      getObjects()
          .fileProperty()
          .convention(
              getLayout()
                  .file(
                      getProvider(
                          () -> {
                            return getProject()
                                .file(getTemporaryDir() + "/resolved-configuration-groups.json");
                          })));

  @Input
  abstract Property<String> getResolvedConfiguration();

  @OutputFile
  public RegularFileProperty getOutput() {
    return output;
  }

  public ResolveConfigurationGroups() {
    getResolvedConfiguration()
        .set(
            getProvider(
                () -> {
                  try (var sw = new StringWriter()) {
                    computeDependencyGroups(getConfigurationGroups())
                        .writeTo("Internal resolved lock file, do not edit.", sw);
                    return sw.toString();
                  }
                }));

    dependsOn(
        getProvider(
            () -> {
              return getConfigurationGroups().stream()
                  .flatMap(it -> it.getIncludedConfigurations().stream())
                  .collect(Collectors.toList());
            }));
  }

  @TaskAction
  void action() throws IOException {
    Path depsFile = output.get().getAsFile().toPath();
    Files.createDirectories(depsFile.getParent());
    Files.writeString(depsFile, getResolvedConfiguration().get());
  }

  private DependencyGroups computeDependencyGroups(
      NamedDomainObjectContainer<ConfigurationGroup> configurationGroups) {
    var groups = new DependencyGroups();
    configurationGroups.forEach(
        configurationGroup -> {
          configurationGroup
              .getIncludedConfigurations()
              .forEach(
                  configuration ->
                      configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                          .filter(res -> res.getId() instanceof ModuleComponentIdentifier)
                          .map(
                              res -> {
                                DependencyInfo depInfo =
                                    new DependencyInfo((ModuleComponentIdentifier) res.getId());
                                depInfo.sources.add(
                                    new DependencyInfo.DependencySource(
                                        configuration, getProject()));
                                return depInfo;
                              })
                          .forEach(
                              depInfo -> {
                                groups.addOrMerge(configurationGroup.getName(), depInfo);
                              }));
        });
    return groups;
  }

  protected final <T> Provider<T> getProvider(Callable<T> value) {
    return getProject().provider(value);
  }

  @Internal
  protected final ProjectLayout getLayout() {
    return getProject().getLayout();
  }

  @Internal
  protected final ObjectFactory getObjects() {
    return getProject().getObjects();
  }
}
