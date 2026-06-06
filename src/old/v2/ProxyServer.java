package old.v2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ProxyServer {
    public static void main(String[] args) {
        int port = 8080;
        System.out.println("Initializing Multithreaded Server on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Manager Thread is standing at the door listening...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("\n[Manager] 🎉 Someone connected! Spawning a worker thread...");

                ClientHandler workerTask = new ClientHandler(clientSocket);

                Thread workerThread = new Thread(workerTask);
                workerThread.start();
            }

        } catch (Exception e) {
            System.out.println("Manager encountered an error: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        System.out.println("   [" + threadName + "] 👷 Worker started processing client.");

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                System.out.println("   [" + threadName + "] Browser requested: " + line);
            }

            Thread.sleep(3000);

            OutputStream output = clientSocket.getOutputStream();
            String htmlBody = "<h1>Hello from Worker Thread: " + threadName + "</h1>";

            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: " + htmlBody.length() + "\r\n" +
                    "\r\n" +
                    htmlBody;

            output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            output.flush();

            clientSocket.close();
            System.out.println("   [" + threadName + "] ✅ Worker finished task and terminated safely.");

        } catch (Exception e) {
            System.out.println("   [" + threadName + "] ❌ Error: " + e.getMessage());
        }
    }
}