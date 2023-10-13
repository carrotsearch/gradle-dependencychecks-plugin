package com.carrotsearch.gradle.dependencychecks;

import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;
import java.util.Arrays;

public abstract class ConfigurationGroup implements Named {
  @Nested
  public abstract NamedDomainObjectSet<Configuration> getIncludedConfigurations();

  @Inject
  public abstract Project getProject();

  public void include(NamedDomainObjectSet<Configuration> configurations) {
    getIncludedConfigurations().addAllLater(getProject().provider(() -> configurations));
  }

  public void include(Configuration... configurations) {
    getIncludedConfigurations().addAll(Arrays.asList(configurations));
  }
}
