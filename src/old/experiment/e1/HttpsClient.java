package old.experiment.e1;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class HttpsClient {
    public static void main(String[] args) {
        String host = "www.facebook.com";
        int port = 443; // The universal secure HTTPS port

        // Factory pattern pulls down Java's built-in OS trusted root keys
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {

            // 1. This command natively executes the TLS Handshake under the hood!
            // It downloads the certificate, checks it, maps the session key, and seals the pipe.
            socket.startHandshake();

            // 2. Now that the pipe is safely encrypted, we can use standard streams!
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Send a standard plain HTTP request line through the secure factory tube
            out.print("GET / HTTP/1.1\r\n");
            out.print("Host: " + host + "\r\n");
            out.print("Connection: close\r\n\r\n");
            out.print("User-Agent: JavaSocketClient\r\n");
            out.flush();

            // Read the response. The SSLSocket automatically decrypts the live binary
            // frames into human-readable text characters on the fly!
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line); // Prints clean, readable HTML data
            }

        } catch (Exception e) {
            System.out.println("Handshake or network error occurred: " + e.getMessage());
        }
    }
}