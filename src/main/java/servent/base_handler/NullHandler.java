package servent.base_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servent.base_message.Message;

/**
 * This will be used if no proper handler is found for the message.
 * @author bmilojkovic
 *
 */
@RequiredArgsConstructor
public class NullHandler implements MessageHandler {

	private final Message clientMessage;
	
	@Override
	public void run() {
		AppConfig.timestampedErrorPrint("Couldn't handle message: " + clientMessage);
	}

}
