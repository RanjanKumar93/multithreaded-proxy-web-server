package v5;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProxyServer {
    public static Map<String, byte[]> lruCache;

    public static void main(String[] args) {
        int port = 8080;
        final int MAX_CACHE_ENTRIES = 3;

        lruCache = Collections.synchronizedMap(
                new LinkedHashMap<String, byte[]>(MAX_CACHE_ENTRIES, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                        if (size() > MAX_CACHE_ENTRIES) {
                            System.out.println("⚠️ [Cache Eviction] Cache full! Evicting least recently used page: " + eldest.getKey());
                            return true;
                        }
                        return false;
                    }
                }
        );

        System.out.println("Multithreaded Proxy Server with LRU Cache online on port " + port + "...");

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
        BufferedReader reader = null;
        Socket serverSocket = null;

        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            reader = new BufferedReader(new InputStreamReader(clientIn));
            String requestLine = reader.readLine();

            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
//                System.out.println("Request Header: " + headerLine);
            }

            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !parts[0].equalsIgnoreCase("GET")) {
                clientSocket.close();
                return;
            }

            String targetUrlStr = parts[1];
            if (targetUrlStr.startsWith("/")) {
                targetUrlStr = targetUrlStr.substring(1);
            }

            byte[] cachedResponse = ProxyServer.lruCache.get(targetUrlStr);

            if (cachedResponse != null) {
                System.out.println("⚡ [" + threadName + "] CACHE HIT! Serving " + targetUrlStr + " instantly from RAM.");
                clientOut.write(cachedResponse);
                clientOut.flush();
                clientSocket.close();
                return;
            }

            System.out.println("❌ [" + threadName + "] CACHE MISS! Fetching live data for: " + targetUrlStr);

            URL targetUrl = new URL(targetUrlStr);
            String host = targetUrl.getHost();
            int port = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort();

            serverSocket = new Socket(host, port);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();

            String path = targetUrl.getPath().isEmpty() ? "/" : targetUrl.getPath();
            String forwardedRequest = "GET " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";

            serverOut.write(forwardedRequest.getBytes(StandardCharsets.UTF_8));
            serverOut.flush();

            ByteArrayOutputStream cacheBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;

            boolean shouldCache = true;
            int totalBytesDownloaded = 0;
            final int MAX_ELEMENT_SIZE = 10 * 1024 * 1024;

            while ((bytesRead = serverIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, bytesRead);

                if (shouldCache) {
                    totalBytesDownloaded += bytesRead;
                    if (totalBytesDownloaded > MAX_ELEMENT_SIZE) {
                        shouldCache = false;
                        cacheBuffer.reset();
                        System.out.println("⚠️ [" + threadName + "] Response exceeds 10MB allocation window. Bypassing caching logic.");
                    } else {
                        cacheBuffer.write(buffer, 0, bytesRead);
                    }
                }
            }
            clientOut.flush();

            if (shouldCache && cacheBuffer.size() > 0) {
                ProxyServer.lruCache.put(targetUrlStr, cacheBuffer.toByteArray());
                System.out.println("💾 [" + threadName + "] Successfully saved to cache: " + targetUrlStr);
            }

        } catch (Exception e) {
            System.out.println("[" + threadName + "] Error: " + e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception ignored) {
            }
            try {
                clientSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}