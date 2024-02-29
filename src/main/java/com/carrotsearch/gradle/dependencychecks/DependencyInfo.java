package com.carrotsearch.gradle.dependencychecks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

/**
 * Information about a single (module) dependency: group, module, version and the reasons the
 * dependency is included in the dependency checks (origin configuration and project).
 */
class DependencyInfo implements Serializable {
  public static Comparator<DependencyInfo> COMPARE_BY_GROUP_MODULE_THEN_ID =
      Comparator.comparing(DependencyInfo::idWithoutVersion).thenComparing(u -> u.version);

  private String group;
  private String module;
  private String version;

  record DependencySource(@JsonProperty String configuration, @JsonProperty String projectPath) {
    @JsonCreator
    public DependencySource {}

    public DependencySource(Configuration configuration, Project project) {
      this(configuration.getName(), project.getPath());
    }

    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "Configuration %s in %s",
          configuration,
          projectPath.equals(":") ? "root project" : projectPath);
    }

    public String projectTask(String taskName) {
      return (projectPath.equals(":") ? "" : projectPath) + ":" + taskName;
    }
  }

  /** One or more "sources" of this dependency. Typically, project path and configuration name. */
  final List<DependencySource> sources;

  DependencyInfo(ModuleComponentIdentifier id) {
    this.group = id.getGroup();
    this.module = id.getModule();
    this.version = id.getVersion();
    this.sources = new ArrayList<>();
  }

  DependencyInfo(String dependency, List<DependencySource> sources) {
    String[] coords = dependency.split(":");
    this.group = coords[0];
    this.module = coords[1];
    this.version = coords[2];
    this.sources = new ArrayList<>(sources);
  }

  String idWithoutVersion() {
    return group + ":" + module;
  }

  String id() {
    return getDependency();
  }

  String getVersion() {
    return version;
  }

  String getDependency() {
    return group + ":" + module + ":" + version;
  }
}
