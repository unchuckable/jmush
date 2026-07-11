package com.github.unchuckable.jmush.oracle;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regenerates the corpus fixture files under {@code src/test/resources/oracle/corpus/} by querying
 * a live oracle for every snippet's expected output. Not named {@code *Test}/{@code *Tests}/{@code
 * *TestCase}, so Surefire's default include globs skip it during normal {@code mvn test}/{@code mvn
 * clean install} runs; invoke it explicitly with {@code mvn test -Dtest=OracleFixtureRecorder}
 * whenever a corpus file gains new snippets or oracle behavior needs re-verification. Requires a
 * live TinyMUSH server, same connection parameters
 * (ORACLE_HOST/ORACLE_PORT/ORACLE_USER/ORACLE_PASS) as the differential test itself.
 *
 * <p>By default every corpus file is re-recorded. To limit a run to specific files (e.g. after a
 * small edit to just one category), pass their names -- with or without the {@code .json} extension
 * -- as a comma-separated {@code oracle.corpus} system property: {@code mvn test
 * -Dtest=OracleFixtureRecorder -Doracle.corpus=math-functions,logic-functions}.
 */
public class OracleFixtureRecorder {

  private static final String HOST = getEnv("ORACLE_HOST", "127.0.0.1");
  private static final int PORT = Integer.parseInt(getEnv("ORACLE_PORT", "6250"));
  private static final String USER = getEnv("ORACLE_USER", "#1");
  private static final String PASSWORD = getEnv("ORACLE_PASS", "potrzebie");

  private static final Path CORPUS_DIR = Paths.get("src/test/resources/oracle/corpus");

  private static OracleClient oracle;

  @BeforeAll
  static void connect() {
    assumeTrue(isReachable(HOST, PORT), "No oracle server reachable at " + HOST + ":" + PORT);
    try {
      oracle = new OracleClient(HOST, PORT);
      oracle.login(USER, PASSWORD);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  static void disconnect() throws IOException {
    if (oracle != null) {
      oracle.quit();
      oracle.close();
    }
  }

  @Test
  void recordCorpusFiles() throws IOException {
    Set<String> only = requestedCorpusNames();
    try (DirectoryStream<Path> files = Files.newDirectoryStream(CORPUS_DIR, "*.json")) {
      for (Path path : files) {
        if (only.isEmpty() || only.contains(path.getFileName().toString())) {
          record(path);
        }
      }
    }
  }

  /**
   * Parses the {@code oracle.corpus} system property (comma-separated file names, with or without
   * {@code .json}) into the exact file names to restrict recording to. Empty means "all files".
   */
  private static Set<String> requestedCorpusNames() {
    String property = System.getProperty("oracle.corpus", "");
    if (property.trim().isEmpty()) {
      return new HashSet<>();
    }
    return Arrays.stream(property.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> name.endsWith(".json") ? name : name + ".json")
        .collect(Collectors.toCollection(HashSet::new));
  }

  private void record(Path path) throws IOException {
    OracleCorpus.File corpus = OracleCorpus.loadFromFile(path);
    for (OracleCorpus.Case c : corpus.cases) {
      c.expected = oracle.eval(c.input);
    }
    OracleCorpus.save(path, corpus);
  }

  private static boolean isReachable(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 500);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static String getEnv(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null ? value : defaultValue;
  }
}
