package com.carrotsearch.gradle.buildinfra.dependencychecks;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
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
    var configurationsContainer = getProject().getConfigurations();
    var projectPath = getProject().getPath();

    for (var configurationGroup : configurationGroups) {
      var includedConfigurationNames = configurationGroup.getIncludedConfigurations().get();
      configurationsContainer
          .matching(conf -> includedConfigurationNames.contains(conf.getName()))
          .forEach(
              configuration -> {
                var configurationName = configuration.getName();
                var graphRoot =
                    configuration.getIncoming().getResolutionResult().getRootComponent().get();
                collectAllResolved(graphRoot).stream()
                    .map(
                        moduleVersionIdentifier -> {
                          return new DependencyInfo(
                              moduleVersionIdentifier,
                              List.of(new DependencySource(configurationName, projectPath)));
                        })
                    .forEach(depInfo -> groups.addOrMerge(configurationGroup.getName(), depInfo));
              });
    }

    return groups;
  }

  private Set<ModuleVersionIdentifier> collectAllResolved(ResolvedComponentResult graphRoot) {
    HashSet<ResolvedDependencyResult> allResolved = new HashSet<>();
    ArrayDeque<DependencyResult> queue = new ArrayDeque<>(graphRoot.getDependencies());

    while (!queue.isEmpty()) {
      var dep = queue.removeFirst();
      if (dep instanceof ResolvedDependencyResult resolvedDep) {
        if (allResolved.add(resolvedDep)) {
          queue.addAll(resolvedDep.getSelected().getDependencies());
        }
      } else {
        throw new GradleException("Unresolved dependency, can't apply forbidden APIs: " + dep);
      }
    }

    Comparator<? super ModuleVersionIdentifier> comp =
        Comparator.comparing(ModuleVersionIdentifier::getGroup)
            .thenComparing(ModuleVersionIdentifier::getName)
            .thenComparing(ModuleVersionIdentifier::getVersion);

    return allResolved.stream()
        .filter(
            dep ->
                dep.getSelected().getId()
                    instanceof org.gradle.api.artifacts.component.ModuleComponentIdentifier)
        .map(
            dep -> {
              return dep.getSelected().getModuleVersion();
            })
        .collect(Collectors.toCollection(() -> new TreeSet<>(comp)));
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
