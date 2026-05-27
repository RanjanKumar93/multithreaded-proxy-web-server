package v8_http_https;

import java.net.ServerSocket;
import java.net.Socket;
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

        System.out.println("Proxy Server online on port " + port + "...");
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new RequestHandler(clientSocket));
            }
        } catch (Exception e) {
            System.out.println("Main server crashed: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
}