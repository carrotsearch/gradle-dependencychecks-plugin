package com.carrotsearch.gradle.buildinfra.dependencychecks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import java.util.Objects;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public final class DependencySource {
  @JsonProperty final String configuration;
  @JsonProperty final String projectPath;

  @JsonCreator
  public DependencySource(
      @JsonProperty("configuration") String configuration,
      @JsonProperty("projectPath") String projectPath) {
    this.configuration = configuration;
    this.projectPath = projectPath;
  }

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

  @JsonProperty
  public String configuration() {
    return configuration;
  }

  @JsonProperty
  public String projectPath() {
    return projectPath;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (DependencySource) obj;
    return Objects.equals(this.configuration, that.configuration)
        && Objects.equals(this.projectPath, that.projectPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(configuration) * 31 + Objects.hashCode(projectPath);
  }
}
