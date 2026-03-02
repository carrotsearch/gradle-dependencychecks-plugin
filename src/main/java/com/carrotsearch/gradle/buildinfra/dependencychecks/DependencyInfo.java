package com.carrotsearch.gradle.buildinfra.dependencychecks;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

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

  /** One or more "sources" of this dependency. Typically, project path and configuration name. */
  final List<DependencySource> sources;

  DependencyInfo(ModuleVersionIdentifier id, List<DependencySource> sources) {
    this.group = id.getGroup();
    this.module = id.getName();
    this.version = id.getVersion();
    this.sources = new ArrayList<>(sources);
  }

  DependencyInfo(String dependency, List<DependencySource> sources) {
    String[] coords = dependency.split(":");
    if (coords.length != 3) {
      throw new RuntimeException(
          "Something is not right with this dependency coords: " + dependency);
    }
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
