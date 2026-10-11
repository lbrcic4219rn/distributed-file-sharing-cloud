package servent.base_message.util;

import app.AppConfig;
import app.ServentInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.BasicMessage;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.Routed;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author bmilojkovic
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageUtil {

    /**
     * Normally this should be true, because it helps with debugging.
     * Flip this to false to disable printing every message send / receive.
     */
    public static final boolean MESSAGE_UTIL_PRINTING = true;

    private static final Set<MessageType> QUIET_TYPES = EnumSet.of(MessageType.PING, MessageType.PONG);

    public static boolean shouldPrint(Message message) {
        return MESSAGE_UTIL_PRINTING && message != null && !QUIET_TYPES.contains(message.getMessageType());
    }

    public static Message readMessage(Socket socket) {
        Message clientMessage = null;

        try {
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            clientMessage = (Message) ois.readObject();

            socket.close();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            AppConfig.timestampedErrorPrint("Discarding something that is not a message of our protocol, from " +
                    socket.getInetAddress() + ":" + socket.getPort());
        }

        if (shouldPrint(clientMessage)) {
            AppConfig.timestampedStandardPrint("Got message " + clientMessage);
        }

        return clientMessage;
    }

    public static void sendMessage(Message message) {
        Thread delayedSender = new Thread(new DelayedMessageSender(message));

        delayedSender.start();
    }

    public static void sendTo(BasicMessage message, ServentInfo receiver) {
        sendMessage(message.redirectTo(receiver.getIpAddress(), receiver.getListenerPort()));
    }

    public static void route(BasicMessage message) {
        int target = ((Routed) message).getTargetChordId();
        ServentInfo nextNode = AppConfig.chordState.getNextNodeForKey(target);

        if (nextNode == null || nextNode.getChordId() == AppConfig.myServentInfo.getChordId()) {
            AppConfig.timestampedErrorPrint("Node " + target + " is not in the system, discarding " + message);
            return;
        }
        sendTo(message, nextNode);
    }

    public static boolean forwardedToTarget(BasicMessage message) {
        if (((Routed) message).getTargetChordId() == AppConfig.myServentInfo.getChordId()) {
            return false;
        }
        route(message);
        return true;
    }

    public static void sendToSuccessor(BasicMessage message) {
        sendToSuccessor(message, null);
    }

    public static void sendToSuccessor(BasicMessage message, ServentInfo firstTarget) {
        Thread sender = new Thread(() -> {
            DelayedMessageSender.artificialDelay();

            ServentInfo target = firstTarget;
            long giveUpAt = System.currentTimeMillis() + 3L * AppConfig.STRONG_TIMEOUT;
            while (System.currentTimeMillis() < giveUpAt) {
                if (target == null) {
                    target = AppConfig.chordState.getSuccessor();
                }
                if (target == null) {
                    return;
                }
                if (DelayedMessageSender.deliver(message.redirectTo(target.getIpAddress(), target.getListenerPort()))) {
                    return;
                }
                target = null;
                pause(1000);
            }
            AppConfig.timestampedErrorPrint("Couldn't send message to successor: " + message);
        });
        sender.start();
    }

    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
