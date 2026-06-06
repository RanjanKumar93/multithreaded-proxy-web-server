package old.v4;

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

        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn));
            String requestLine = reader.readLine();

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

            if (ProxyServer.lruCache.containsKey(targetUrlStr)) {
                System.out.println("⚡ [" + threadName + "] CACHE HIT! Serving " + targetUrlStr + " instantly from RAM.");
                byte[] cachedResponse = ProxyServer.lruCache.get(targetUrlStr);
                clientOut.write(cachedResponse);
                clientOut.flush();
                clientSocket.close();
                return;
            }

            System.out.println("❌ [" + threadName + "] CACHE MISS! Fetching live data for: " + targetUrlStr);

            URL targetUrl = new URL(targetUrlStr);
            String host = targetUrl.getHost();
            int port = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort();

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

            ByteArrayOutputStream cacheBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = serverIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, bytesRead);
                cacheBuffer.write(buffer, 0, bytesRead);
            }
            clientOut.flush();

            byte[] completeResponse = cacheBuffer.toByteArray();
            if (completeResponse.length > 0) {
                ProxyServer.lruCache.put(targetUrlStr, completeResponse);
                System.out.println("💾 [" + threadName + "] Successfully saved to cache: " + targetUrlStr);
            }

            serverSocket.close();
            clientSocket.close();

        } catch (Exception e) {
            System.out.println("[" + threadName + "] Error: " + e.getMessage());
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }
}