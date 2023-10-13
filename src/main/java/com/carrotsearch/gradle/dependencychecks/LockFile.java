package com.carrotsearch.gradle.dependencychecks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;

@JsonPropertyOrder({"comment", "configurationGroups", "because"})
public class LockFile {
  @JsonProperty public String comment;

  @JsonProperty("because")
  public LinkedHashMap<String, List<String>> keyToSource = new LinkedHashMap<>();

  @JsonProperty("configurationGroups")
  public LinkedHashMap<String, LinkedHashMap<String, String>> configurationGroups =
      new LinkedHashMap<>();
}
