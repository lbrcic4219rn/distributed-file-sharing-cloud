package servent.base_message.util;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_message.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This worker sends a message asynchronously. Doing this in a separate thread
 * has the added benefit of being able to delay without blocking main or somesuch.
 *
 * @author bmilojkovic
 *
 */
@RequiredArgsConstructor
public class DelayedMessageSender implements Runnable {

    private final Message messageToSend;

    /**
     * It is important to take regular naps for health reasons.
     */
    public static void artificialDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean deliver(Message message) {
        if (MessageUtil.shouldPrint(message)) {
            AppConfig.timestampedStandardPrint("Sending message " + message);
        }

        try (Socket sendSocket = new Socket(message.getReceiverIpAddress(), message.getReceiverPort())) {
            ObjectOutputStream oos = new ObjectOutputStream(sendSocket.getOutputStream());
            oos.writeObject(message);
            oos.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void run() {
        artificialDelay();

        if (!deliver(messageToSend) && MessageUtil.shouldPrint(messageToSend)) {
            AppConfig.timestampedErrorPrint("Couldn't send message: " + messageToSend);
        }
    }
}
