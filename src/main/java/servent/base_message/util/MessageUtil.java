package servent.base_message.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

import app.AppConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.Message;

/**
 * For now, just the read and send implementation, based on Java serializing.
 * Not too smart. Doesn't even check the neighbor list, so it actually allows cheating.
 * <p>
 * Sending is delegated to a {@link DelayedMessageSender} in a new thread (non-FIFO).
 * @author bmilojkovic
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageUtil {

	/**
	 * Normally this should be true, because it helps with debugging.
	 * Flip this to false to disable printing every message send / receive.
	 */
	public static final boolean MESSAGE_UTIL_PRINTING = true;
	
	public static Message readMessage(Socket socket) {
		
		Message clientMessage = null;
			
		try {
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
	
			clientMessage = (Message) ois.readObject();
			
			socket.close();
		} catch (IOException e) {
			AppConfig.timestampedErrorPrint("Error in reading socket on " +
					socket.getInetAddress() + ":" + socket.getPort());
		} catch (ClassNotFoundException e) {
			AppConfig.timestampedErrorPrint("Received a message of an unknown class", e);
		}
		
		if (MESSAGE_UTIL_PRINTING) {
			AppConfig.timestampedStandardPrint("Got message " + clientMessage);
		}
				
		return clientMessage;
	}
	
	public static void sendMessage(Message message) {
		Thread delayedSender = new Thread(new DelayedMessageSender(message));
		
		delayedSender.start();
	}
}
