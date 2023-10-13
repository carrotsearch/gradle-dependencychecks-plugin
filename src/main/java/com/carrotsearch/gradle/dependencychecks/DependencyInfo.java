package com.carrotsearch.gradle.dependencychecks;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

  /** One or more "sources" of this dependency. Typically, project path and configuration name. */
  List<String> because = new ArrayList<>();

  DependencyInfo(ModuleComponentIdentifier id) {
    this.group = id.getGroup();
    this.module = id.getModule();
    this.version = id.getVersion();
  }

  DependencyInfo(String dependency, List<String> because) {
    String[] coords = dependency.split(":");
    this.group = coords[0];
    this.module = coords[1];
    this.version = coords[2];
    this.because = because;
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
