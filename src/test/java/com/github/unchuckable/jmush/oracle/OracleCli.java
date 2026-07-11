package com.github.unchuckable.jmush.oracle;

/**
 * Interactive probe: evaluates one or more mushcode snippets against a running oracle server over a
 * single connection/session and prints each result. Connection details are read from environment
 * variables so the common case (a local dev-built netmush on the default port) needs no arguments.
 *
 * <p>Each CLI argument is its own snippet (quote each one separately) -- batching them into one
 * session avoids paying for a fresh TCP connect + login handshake per probe, which matters when
 * probing many small cases in a row.
 */
public class OracleCli {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("usage: oracle <mushcode snippet> [<mushcode snippet> ...]");
      System.exit(2);
    }

    String host = getEnv("ORACLE_HOST", "127.0.0.1");
    int port = Integer.parseInt(getEnv("ORACLE_PORT", "6250"));
    String user = getEnv("ORACLE_USER", "#1");
    String password = getEnv("ORACLE_PASS", "potrzebie");

    try (OracleClient client = new OracleClient(host, port)) {
      client.login(user, password);
      for (String snippet : args) {
        if (args.length > 1) {
          System.out.println("=== " + snippet + " ===");
        }
        System.out.println(client.eval(snippet));
      }
      client.quit();
    }
  }

  private static String getEnv(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null ? value : defaultValue;
  }
}
