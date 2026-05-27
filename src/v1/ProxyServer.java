package v1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ProxyServer {
    public static void main(String[] args) {
        int port = 8080;
        int requestCount = 0;

        System.out.println("Initializing infinite loop server on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is online! Press Ctrl+C in your console to stop it.");

            while (true) {
                System.out.println("\n[Waiting for a new connection...]");

                Socket clientSocket = serverSocket.accept();
                requestCount++;
                System.out.println("🎉 Connection #" + requestCount + " accepted from: " + clientSocket.getRemoteSocketAddress());

                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line = reader.readLine();

                if (line != null) {
                    System.out.println("   Browser asked for: " + line);
                }

                OutputStream output = clientSocket.getOutputStream();
                String htmlBody = "<h1>Hello World! This is Request #" + requestCount + "</h1>";

                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + htmlBody.length() + "\r\n" +
                        "\r\n" +
                        htmlBody;

                output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();

                clientSocket.close();
                System.out.println("Connection #" + requestCount + " safely closed.");
            }

        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }
}