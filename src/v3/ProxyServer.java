package v3;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ProxyServer {
    public static void main(String[] args) {
        int port = 8080;
        System.out.println("True Proxy Server Online on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ProxyWorker(clientSocket)).start();
            }
        } catch (Exception e) {
            System.out.println("Main server crashed: " + e.getMessage());
        }
    }
}

class ProxyWorker implements Runnable {
    private final Socket clientSocket;

    public ProxyWorker(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn));
            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            System.out.println("[" + threadName + "] Proxying request for: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !parts[0].equalsIgnoreCase("GET")) {
                clientSocket.close();
                return;
            }

            String targetUrlStr = parts[1];

            if (targetUrlStr.startsWith("/")) {
                targetUrlStr = targetUrlStr.substring(1);
            }

            URL targetUrl = new URL(targetUrlStr);
            String host = targetUrl.getHost();
            int port = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort();

            System.out.println("   -> Connecting to remote server: " + host + " on port " + port);

            Socket serverSocket = new Socket(host, port);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();

            String path = targetUrl.getPath().isEmpty() ? "/" : targetUrl.getPath();
            String forwardedRequest = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";

            serverOut.write(forwardedRequest.getBytes(StandardCharsets.UTF_8));
            serverOut.flush();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = serverIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, bytesRead);
            }
            clientOut.flush();

            serverSocket.close();
            clientSocket.close();
            System.out.println("   -> [" + threadName + "] Successfully transferred website data.");

        } catch (Exception e) {
            System.out.println("[" + threadName + "] Error proxying request: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}