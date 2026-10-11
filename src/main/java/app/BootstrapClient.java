package app;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BootstrapClient {

    public static void notify(String verb, ServentInfo node) {
        try (Socket bsSocket = new Socket(AppConfig.BOOTSTRAP_ADDRESS, AppConfig.BOOTSTRAP_PORT)) {
            PrintWriter bsWriter = new PrintWriter(bsSocket.getOutputStream());
            bsWriter.write(verb + "\n" + node.getIpAddress() + ":" + node.getListenerPort() + "\n");
            bsWriter.flush();
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't send " + verb + " to the bootstrap server", e);
        }
    }
}
