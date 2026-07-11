package com.github.unchuckable.jmush.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;

/**
 * Differential test: evaluates a corpus of mushcode snippets against both jmush and a
 * running reference oracle server, asserting identical output. Skips (rather than fails)
 * when no oracle is reachable, since most dev machines and CI won't have one running by
 * default -- see DESIGN.md's Phase 0 compatibility-oracle spike.
 *
 * Requires a TinyMUSH server on ORACLE_HOST:ORACLE_PORT (default 127.0.0.1:6250), logged
 * in as ORACLE_USER/ORACLE_PASS (default #1/potrzebie) matching the object identity used
 * for %# substitution below.
 */
public class CompatibilityOracleTest {

    private static final String HOST = getEnv("ORACLE_HOST", "127.0.0.1");
    private static final int PORT = Integer.parseInt(getEnv("ORACLE_PORT", "6250"));
    private static final String USER = getEnv("ORACLE_USER", "#1");
    private static final String PASSWORD = getEnv("ORACLE_PASS", "potrzebie");
    private static final String CALLER_NAME = getEnv("ORACLE_USER_NAME", "Wizard");
    private static final String CALLER_DBREF = USER;

    private static boolean oracleAvailable;
    private static OracleClient oracle;

    @BeforeAll
    static void connect() {
        oracleAvailable = isReachable(HOST, PORT);
        assumeTrue(oracleAvailable, "No oracle server reachable at " + HOST + ":" + PORT + " -- skipping");
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
    void substitutionsMatchOracle() throws IOException {
        assertMatchesOracle("Hello,%t%#.");
        assertMatchesOracle("Hello,  vexy");
        assertMatchesOracle("%r");
    }

    private void assertMatchesOracle(String mushcode) throws IOException {
        MushcodeParser parser = new MushcodeParser(Collections.emptyMap());
        ExecutionContext ctx = new ExecutionContext()
                .withCaller(new MushObject().withName(CALLER_NAME).withDbRefString(CALLER_DBREF));
        String jmushResult = parser.parse(mushcode).evaluateExpression(ctx).toString();
        String oracleResult = oracle.eval(mushcode);
        assertEquals(oracleResult, jmushResult, () -> "mismatch for: " + mushcode);
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
