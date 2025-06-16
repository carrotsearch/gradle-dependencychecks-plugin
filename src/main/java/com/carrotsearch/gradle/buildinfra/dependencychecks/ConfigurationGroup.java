package com.carrotsearch.gradle.buildinfra.dependencychecks;

import java.util.stream.Stream;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;

public abstract class ConfigurationGroup implements Named {
  @Nested
  public abstract SetProperty<String> getIncludedConfigurations();

  public void include(Provider<? extends Iterable<String>> provider) {
    getIncludedConfigurations().addAll(provider);
  }

  public void include(Iterable<String> configurationNames) {
    getIncludedConfigurations().addAll(configurationNames);
  }

  public void include(Configuration... configurations) {
    getIncludedConfigurations().addAll(Stream.of(configurations).map(Named::getName).toList());
  }

  public void include(NamedDomainObjectSet<Configuration> configurations) {
    configurations.all(
        configuration -> {
          getIncludedConfigurations().add(configuration.getName());
        });
  }
}
