package old.experiment;

import java.io.InputStream;
import java.net.Socket;

public class HttpClientReader {

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("neverssl.com", 80);

            InputStream in = socket.getInputStream();

            byte[] buffer = new byte[64];

            int bytesRead = in.read(buffer);

            System.out.println("Bytes read: " + bytesRead);

            for (int i = 0; i < bytesRead; i++) {
                System.out.printf("%02X ", buffer[i]);
            }

            System.out.println("\n");

            String rawText = new String(buffer);

            System.out.println("Raw HTTPS Data:");
            System.out.println(rawText);

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}