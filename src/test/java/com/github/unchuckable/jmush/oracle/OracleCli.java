package com.github.unchuckable.jmush.oracle;

/**
 * Interactive probe: evaluates a mushcode snippet against a running oracle server and
 * prints the result. Connection details are read from environment variables so the
 * common case (a local dev-built netmush on the default port) needs no arguments.
 */
public class OracleCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: oracle <mushcode snippet...>");
            System.exit(2);
        }

        String host = getEnv("ORACLE_HOST", "127.0.0.1");
        int port = Integer.parseInt(getEnv("ORACLE_PORT", "6250"));
        String user = getEnv("ORACLE_USER", "#1");
        String password = getEnv("ORACLE_PASS", "potrzebie");
        String snippet = String.join(" ", args);

        try (OracleClient client = new OracleClient(host, port)) {
            client.login(user, password);
            String result = client.eval(snippet);
            System.out.println(result);
            client.quit();
        }
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }
}
