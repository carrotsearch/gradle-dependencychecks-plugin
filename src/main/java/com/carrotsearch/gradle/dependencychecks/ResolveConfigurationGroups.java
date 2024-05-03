package com.carrotsearch.gradle.dependencychecks;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
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

  @OutputFile
  public RegularFileProperty getOutput() {
    return output;
  }

  public ResolveConfigurationGroups() {
    var confProvider =
        getProvider(
            () -> {
              return getConfigurationGroups().stream()
                  .map(
                      it -> {
                        return it.getIncludedConfigurations();
                      })
                  .collect(Collectors.toList());
            });

    getInputs().files(confProvider);
    getInputs()
        .property(
            "configuration-group-names",
            getProvider(
                () -> {
                  return getConfigurationGroups().getNames();
                }));

    dependsOn(confProvider);
  }

  @TaskAction
  void action() throws IOException {
    File depsFile = output.get().getAsFile();
    depsFile.getParentFile().mkdirs();
    computeDependencyGroups(getConfigurationGroups())
        .writeTo("Internal resolved lock file, do not edit.", depsFile);
  }

  private DependencyGroups computeDependencyGroups(
      NamedDomainObjectContainer<ConfigurationGroup> configurationGroups) {
    var groups = new DependencyGroups();
    configurationGroups.forEach(
        configurationGroup -> {
          configurationGroup
              .getIncludedConfigurations()
              .forEach(
                  configuration -> {
                    getLogger().debug("Resolving configuration group {}", configuration.getName());
                    configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .peek(
                            res ->
                                getLogger()
                                    .debug(
                                        "  Component: {}, ModuleComponentIdentifier?: {}",
                                        res.getId(),
                                        res.getId() instanceof ModuleComponentIdentifier))
                        .filter(res -> res.getId() instanceof ModuleComponentIdentifier)
                        .map(
                            res -> {
                              DependencyInfo depInfo =
                                  new DependencyInfo((ModuleComponentIdentifier) res.getId());
                              depInfo.sources.add(
                                  new DependencyInfo.DependencySource(configuration, getProject()));
                              return depInfo;
                            })
                        .forEach(
                            depInfo -> {
                              getLogger().lifecycle("  Adding: {}", depInfo.toString());
                              groups.addOrMerge(configurationGroup.getName(), depInfo);
                            });
                  });
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
