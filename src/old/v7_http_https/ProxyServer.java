package old.v7_http_https;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    public static Map<String, byte[]> lruCache;

    public static void main(String[] args) {
        int port = 8080;
        final int MAX_CACHE_ENTRIES = 3;
        final int MAX_CLIENTS = 400;

        lruCache = Collections.synchronizedMap(new LinkedHashMap<String, byte[]>(MAX_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                if (size() > MAX_CACHE_ENTRIES) {
                    System.out.println("⚠️ [Cache Eviction] Cache full! Evicting: " + eldest.getKey());
                    return true;
                }
                return false;
            }
        });

        System.out.println("Dual HTTP/HTTPS Proxy Server online on port " + port + "...");
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ProxyWorker(clientSocket));
            }
        } catch (Exception e) {
            System.out.println("Main server crashed: " + e.getMessage());
        } finally {
            threadPool.shutdown();
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
        BufferedReader reader = null;
        Socket serverSocket = null;

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
                System.out.println("\n🔒 [" + threadName + "] HTTPS Tunnel Requested: " + requestLine);

                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                    // Draining buffer
                }

                String host = targetDestination;
                int port = 443;
                if (host.contains(":")) {
                    String[] hostAndPort = host.split(":");
                    host = hostAndPort[0];
                    port = Integer.parseInt(hostAndPort[1]);
                }

                System.out.println("   -> Establishing raw TCP pipeline to: " + host + " on port " + port);

                try {
                    serverSocket = new Socket(host, port);
                    InputStream serverIn = serverSocket.getInputStream();
                    OutputStream serverOut = serverSocket.getOutputStream();

                    String acknowledgement = "HTTP/1.1 200 Connection Established\r\n\r\n";
                    clientOut.write(acknowledgement.getBytes(StandardCharsets.UTF_8));
                    clientOut.flush();

                    Thread clientToRemoteThread = new Thread(() -> {
                        try {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = clientIn.read(buffer)) != -1) {
                                serverOut.write(buffer, 0, read);
                                serverOut.flush();
                            }
                        } catch (IOException ignored) {}
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
                    System.out.println("   -> Tunnel Connection failed: " + e.getMessage());
                }
                return;
            }

            StringBuilder accumulatedHeaders = new StringBuilder();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("host:") || headerLine.toLowerCase().startsWith("connection:")) {
                    continue;
                }
                accumulatedHeaders.append(headerLine).append("\r\n");
            }

            System.out.println("\n🌐 [" + threadName + "] HTTP Intercepted Request: " + requestLine);

            if (!method.equalsIgnoreCase("GET")) {
                System.out.println("   -> Error: Non-GET HTTP methods are unsupported.");
                clientSocket.close();
                return;
            }

            String tempUrl = targetDestination;
            if (tempUrl.contains("://")) {
                tempUrl = tempUrl.substring(tempUrl.indexOf("://") + 3);
            }
            if (tempUrl.startsWith("/")) {
                tempUrl = tempUrl.substring(1);
            }

            String host;
            String path;
            int port = 80;

            int firstSlash = tempUrl.indexOf("/");
            if (firstSlash != -1) {
                host = tempUrl.substring(0, firstSlash);
                path = tempUrl.substring(firstSlash);
            } else {
                host = tempUrl;
                path = "/";
            }

            if (host.contains(":")) {
                String[] hostAndPort = host.split(":");
                host = hostAndPort[0];
                port = Integer.parseInt(hostAndPort[1]);
            }

            String cacheKey = host + ":" + port + path;

            byte[] cachedResponse = ProxyServer.lruCache.get(cacheKey);
            if (cachedResponse != null) {
                System.out.println("⚡ [" + threadName + "] CACHE HIT! Serving " + cacheKey + " instantly from RAM.");
                clientOut.write(cachedResponse);
                clientOut.flush();
                clientSocket.close();
                return;
            }

            System.out.println("❌ [" + threadName + "] CACHE MISS! Fetching live data for: " + cacheKey);

            serverSocket = new Socket(host, port);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();

            String forwardedRequest = method + " " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: close\r\n" +
                    accumulatedHeaders.toString() + "\r\n";

            serverOut.write(forwardedRequest.getBytes(StandardCharsets.UTF_8));
            serverOut.flush();

            ByteArrayOutputStream cacheBuffer = new ByteArrayOutputStream();
            byte[] writeBuffer = new byte[4096];
            int bytesRead;

            boolean shouldCache = true;
            int totalBytesDownloaded = 0;
            final int MAX_ELEMENT_SIZE = 10 * 1024 * 1024;

            while ((bytesRead = serverIn.read(writeBuffer)) != -1) {
                clientOut.write(writeBuffer, 0, bytesRead);
                if (shouldCache) {
                    totalBytesDownloaded += bytesRead;
                    if (totalBytesDownloaded > MAX_ELEMENT_SIZE) {
                        shouldCache = false;
                        cacheBuffer.reset();
                        System.out.println("⚠️ [" + threadName + "] Payload over 10MB safety window. Bypassing cache.");
                    } else {
                        cacheBuffer.write(writeBuffer, 0, bytesRead);
                    }
                }
            }
            clientOut.flush();

            if (shouldCache && cacheBuffer.size() > 0) {
                ProxyServer.lruCache.put(cacheKey, cacheBuffer.toByteArray());
                System.out.println("💾 [" + threadName + "] Successfully saved to cache: " + cacheKey);
            }

        } catch (Exception e) {
            System.out.println("[" + threadName + "] Error: " + e.getMessage());
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }
}