package servent.base_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servent.base_message.Message;
import servent.base_message.MessageType;

@RequiredArgsConstructor
public class SorryHandler implements MessageHandler {

	private final Message clientMessage;
	
	@Override
	public void run() {
		if (clientMessage.getMessageType() == MessageType.SORRY) {
			AppConfig.timestampedStandardPrint("Couldn't enter Chord system because of collision. Change my listener port, please.");
			System.exit(0);
		} else {
			AppConfig.timestampedErrorPrint("Sorry handler got a message that is not SORRY");
		}

	}

}
