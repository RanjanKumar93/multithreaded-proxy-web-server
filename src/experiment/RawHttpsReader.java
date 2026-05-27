package experiment;

import java.io.InputStream;
import java.net.Socket;

public class RawHttpsReader {

    public static void main(String[] args) {
        try {

            // Connect directly to HTTPS port
            Socket socket = new Socket("neverssl.com", 80);

            InputStream in = socket.getInputStream();

            byte[] buffer = new byte[64];

            int bytesRead = in.read(buffer);

            System.out.println("Bytes read: " + bytesRead);

            // Print raw bytes
            for (int i = 0; i < bytesRead; i++) {
                System.out.printf("%02X ", buffer[i]);
            }

            System.out.println("\n");

            // Try converting to String (will look like garbage)
            String rawText = new String(buffer);

            System.out.println("Raw HTTPS Data:");
            System.out.println(rawText);

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}