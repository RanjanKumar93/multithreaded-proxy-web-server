package v8_http_https;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HttpProcessor {

    public static void handlePlainTraffic(String requestLine, String method, String targetDestination,
                                          BufferedReader reader, OutputStream clientOut, String threadName) {
        Socket serverSocket = null;
        try {
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
            System.out.println("[" + threadName + "] HTTP Processor Error: " + e.getMessage());
        } finally {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}