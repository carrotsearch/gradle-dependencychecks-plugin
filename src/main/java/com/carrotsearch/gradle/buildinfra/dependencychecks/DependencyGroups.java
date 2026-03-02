package com.carrotsearch.gradle.buildinfra.dependencychecks;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;

/** An ordered set of named groups of {@link DependencyInfo}s. */
public class DependencyGroups implements Serializable {
  private final HashMap<String, DependencyInfo> dependencyByGroupAndId = new HashMap<>();
  private final TreeMap<String, TreeSet<DependencyInfo>> dependencies = new TreeMap<>();

  public static ObjectMapper objectMapper = getObjectMapper();

  public Map<String, TreeSet<DependencyInfo>> getDependencies() {
    return dependencies;
  }

  public DependencyGroups(Map<String, ? extends Set<DependencyInfo>> groups) {
    mergeInternal(groups);
  }

  public DependencyGroups() {
    this(Collections.emptyMap());
  }

  public void writeTo(String comment, File file) throws IOException {
    try (var writer = Files.newBufferedWriter(file.toPath())) {
      writeTo(comment, writer);
    }
  }

  public void writeTo(String comment, Writer writer) throws IOException {
    LockFile lockFile = new LockFile();
    lockFile.comment = comment;

    // Collect unique sources.
    TreeMap<String, List<DependencySource>> keyToSource = lockFile.keyToSource;
    LinkedHashMap<List<DependencySource>, String> sourceToKey = new LinkedHashMap<>();
    getDependencies().values().stream()
        .flatMap(it -> it.stream().map(it2 -> it2.sources))
        .forEach(
            source -> {
              sourceToKey.computeIfAbsent(
                  source,
                  unused -> {
                    String key = String.format(Locale.ROOT, "%08x", source.hashCode());
                    // Add synthetic padding in case hash codes are not unique.
                    while (keyToSource.containsKey(key)) {
                      key = key + "P";
                    }

                    keyToSource.put(key, source);
                    return key;
                  });
            });

    LinkedHashMap<String, LinkedHashMap<String, String>> configurationGroups =
        lockFile.configurationGroups;
    getDependencies()
        .forEach(
            (name, depInfos) -> {
              var value =
                  depInfos.stream()
                      .collect(
                          Collectors.toMap(
                              DependencyInfo::getDependency,
                              (e -> sourceToKey.get(e.sources) + ",refs=" + e.sources.size()),
                              ((a, b) -> {
                                throw new RuntimeException();
                              }),
                              LinkedHashMap::new));

              configurationGroups.put(name, value);
            });

    objectMapper.writeValue(writer, lockFile);
  }

  public static DependencyGroups readFrom(File file) throws IOException {
    LockFile lockFile;
    try {
      lockFile = objectMapper.readValue(file, LockFile.class);
    } catch (MismatchedInputException e) {
      throw new GradleException(
          "Existing lock file cannot be read, recreate it using writeLocks: " + file);
    }

    var depGroups = new DependencyGroups();
    lockFile.configurationGroups.forEach(
        (name, depList) -> {
          depList.forEach(
              (dependency, source) -> {
                String sourceKey = source.substring(0, source.indexOf(","));
                depGroups.addOrMerge(
                    name, new DependencyInfo(dependency, lockFile.keyToSource.get(sourceKey)));
              });
        });

    return depGroups;
  }

  /**
   * Add or merge the provided dependency information.
   *
   * @param groupName The group this dependency is added to.
   * @param dependencyInfo The dependency info to be added or merged.
   */
  public void addOrMerge(String groupName, DependencyInfo dependencyInfo) {
    var lookupKey = groupName + " -> " + dependencyInfo.id();
    var owned = dependencyByGroupAndId.get(lookupKey);
    if (owned != null) {
      owned.sources.addAll(dependencyInfo.sources);
    } else {
      owned =
          new DependencyInfo(
              dependencyInfo.getDependency(), new ArrayList<>(dependencyInfo.sources));
      dependencyByGroupAndId.put(lookupKey, owned);
      dependencies
          .computeIfAbsent(
              groupName, key -> new TreeSet<>(DependencyInfo.COMPARE_BY_GROUP_MODULE_THEN_ID))
          .add(owned);
    }
  }

  /**
   * Merge with another dependency group.
   *
   * @param other the other dependency group to merge with.
   */
  public void merge(DependencyGroups other) {
    mergeInternal(other.dependencies);
  }

  private void mergeInternal(Map<String, ? extends Set<DependencyInfo>> other) {
    other.forEach(
        (groupName, entries) -> {
          entries.forEach(
              entry -> {
                addOrMerge(groupName, entry);
              });
        });
  }

  DependencyInfo getIfExists(String groupName, DependencyInfo other) {
    var lookupKey = groupName + " -> " + other.id();
    return dependencyByGroupAndId.get(lookupKey);
  }

  private static ObjectMapper getObjectMapper() {
    DefaultPrettyPrinter twoSpaceLfPrettyPrinter = new DefaultPrettyPrinter();
    DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
    twoSpaceLfPrettyPrinter.indentArraysWith(indenter);
    twoSpaceLfPrettyPrinter.indentObjectsWith(indenter);

    return JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
        .disable(MapperFeature.AUTO_DETECT_GETTERS)
        .disable(MapperFeature.AUTO_DETECT_SETTERS)
        .disable(MapperFeature.AUTO_DETECT_FIELDS)
        .disable(MapperFeature.AUTO_DETECT_CREATORS)
        .defaultPrettyPrinter(twoSpaceLfPrettyPrinter)
        .build();
  }
}
