package com.github.unchuckable.jmush.oracle;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

/**
 * A minimal telnet client for driving a running TinyMUSH server (the compatibility oracle).
 * Not thread-safe; one client per session.
 */
public class OracleClient implements Closeable {

    private static final Charset MUSH_CHARSET = Charset.forName("ISO-8859-1");
    private static final int IDLE_TIMEOUT_MILLIS = 2000;
    private static final int RESPONSE_TIMEOUT_MILLIS = 3000;

    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;

    public OracleClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), MUSH_CHARSET));
        out = socket.getOutputStream();
        drainUntilIdle(IDLE_TIMEOUT_MILLIS);
    }

    public void login(String name, String password) throws IOException {
        sendLine("connect " + name + " " + password);
        drainUntilIdle(IDLE_TIMEOUT_MILLIS);
    }

    /**
     * Evaluates a mushcode snippet via {@code think} and returns its raw output (CRLFs
     * preserved as-is), with exactly the one trailing CRLF that {@code notify()} itself
     * appends stripped off. Uses a unique sentinel command, read as raw characters (not
     * line-by-line) to detect the end of output without losing embedded line-ending
     * information -- compatibility bugs live in exactly that detail (e.g. mushcode's %r).
     */
    public String eval(String mushcode) throws IOException {
        String marker = "ORACLE-" + System.nanoTime();
        String markerLine = marker + "\r\n";
        sendLine("think " + mushcode);
        sendLine("think " + marker);

        StringBuilder raw = new StringBuilder();
        socket.setSoTimeout(RESPONSE_TIMEOUT_MILLIS);
        char[] buf = new char[1024];
        int n;
        while ((n = readRawOrEnd(buf)) != -1) {
            raw.append(buf, 0, n);
            int idx = raw.indexOf(markerLine);
            if (idx >= 0) {
                raw.setLength(idx);
                break;
            }
        }

        if (endsWith(raw, "\r\n")) {
            raw.setLength(raw.length() - 2);
        } else if (raw.length() > 0 && (raw.charAt(raw.length() - 1) == '\n' || raw.charAt(raw.length() - 1) == '\r')) {
            raw.setLength(raw.length() - 1);
        }
        return raw.toString();
    }

    private static boolean endsWith(StringBuilder sb, String suffix) {
        int len = suffix.length();
        return sb.length() >= len && sb.substring(sb.length() - len).equals(suffix);
    }

    private int readRawOrEnd(char[] buf) throws IOException {
        try {
            return in.read(buf);
        } catch (SocketTimeoutException e) {
            return -1;
        }
    }

    public void quit() throws IOException {
        sendLine("QUIT");
    }

    private void sendLine(String line) throws IOException {
        out.write((line + "\r\n").getBytes(MUSH_CHARSET));
        out.flush();
    }

    private String readLineOrNull() throws IOException {
        try {
            return in.readLine();
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    private void drainUntilIdle(int idleMillis) throws IOException {
        socket.setSoTimeout(idleMillis);
        while (readLineOrNull() != null) {
            // discard banner/connect noise
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
