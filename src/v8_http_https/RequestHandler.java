package v8_http_https;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;

    public RequestHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        BufferedReader reader = null;

        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            reader = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                clientSocket.close();
                return;
            }

            String method = parts[0];
            String targetDestination = parts[1];

            if (method.equalsIgnoreCase("CONNECT")) {
                HttpsTunnel.handleSecureTunnel(requestLine, targetDestination, reader, clientIn, clientOut, threadName);
            } else {
                HttpProcessor.handlePlainTraffic(requestLine, method, targetDestination, reader, clientOut, threadName);
            }

        } catch (Exception e) {
            System.out.println("[" + threadName + "] Request Exception: " + e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            try {
                clientSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}