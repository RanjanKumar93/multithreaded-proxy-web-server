package v8_http_https;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HttpsTunnel {

    public static void handleSecureTunnel(String requestLine, String targetDestination, BufferedReader reader,
                                          InputStream clientIn, OutputStream clientOut, String threadName) {
        Socket serverSocket = null;
        try {
            System.out.println("\n🔒 [" + threadName + "] HTTPS Tunnel Requested: " + requestLine);

            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                // Draining remaining unneeded handshake headers
            }

            String host = targetDestination;
            int port = 443;
            if (host.contains(":")) {
                String[] hostAndPort = host.split(":");
                host = hostAndPort[0];
                port = Integer.parseInt(hostAndPort[1]);
            }

            System.out.println("   -> Establishing raw TCP pipeline to: " + host + " on port " + port);

            serverSocket = new Socket(host, port);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();

            String acknowledgement = "HTTP/1.1 200 Connection Established\r\n\r\n";
            clientOut.write(acknowledgement.getBytes(StandardCharsets.UTF_8));
            clientOut.flush();

            OutputStream finalServerOut = serverOut;
            Thread clientToRemoteThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = clientIn.read(buffer)) != -1) {
                        finalServerOut.write(buffer, 0, read);
                        finalServerOut.flush();
                    }
                } catch (IOException ignored) {
                }
            });
            clientToRemoteThread.start();

            byte[] buffer = new byte[8192];
            int read;
            while ((read = serverIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, read);
                clientOut.flush();
            }

            clientToRemoteThread.join();
            System.out.println("🔒 [" + threadName + "] HTTPS Tunnel safely closed for: " + host);

        } catch (Exception e) {
            System.out.println("[" + threadName + "] HTTPS Tunnel Error: " + e.getMessage());
        } finally {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}