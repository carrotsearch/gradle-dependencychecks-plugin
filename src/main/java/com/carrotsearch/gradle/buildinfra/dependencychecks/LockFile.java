package com.carrotsearch.gradle.buildinfra.dependencychecks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

@JsonPropertyOrder({"comment", "configurationGroups", "because"})
class LockFile {
  @JsonProperty public String comment;

  @JsonProperty("because")
  public TreeMap<String, List<DependencySource>> keyToSource = new TreeMap<>();

  @JsonProperty("configurationGroups")
  public LinkedHashMap<String, LinkedHashMap<String, String>> configurationGroups =
      new LinkedHashMap<>();
}
