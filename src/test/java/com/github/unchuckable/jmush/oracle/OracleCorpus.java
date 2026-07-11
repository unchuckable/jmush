package com.github.unchuckable.jmush.oracle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads/saves a corpus of mushcode snippets and their recorded oracle output from a JSON file under
 * {@code src/test/resources/oracle/corpus/}. Read by {@link CompatibilityOracleTest} (from the
 * classpath, no oracle connection required) and written by {@link OracleFixtureRecorder} (from the
 * filesystem, after querying a live oracle).
 */
public class OracleCorpus {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public static class Case {
    public String input;
    public String expected;
  }

  public static class File {
    public String notes;
    public List<Case> cases;
  }

  public static File loadFromClasspath(String resourceName) throws IOException {
    URL url = OracleCorpus.class.getResource("/oracle/corpus/" + resourceName);
    if (url == null) {
      throw new IOException("Corpus resource not found: " + resourceName);
    }
    try (InputStream in = url.openStream();
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, File.class);
    }
  }

  public static File loadFromFile(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, File.class);
    }
  }

  public static void save(Path path, File corpus) throws IOException {
    try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      GSON.toJson(corpus, writer);
      writer.write("\n");
    }
  }
}
