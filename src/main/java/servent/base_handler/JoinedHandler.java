package servent.base_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servent.base_message.Message;
import mutex.TokenMutex;
import servent.base_message.MessageType;

@RequiredArgsConstructor
public class JoinedHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {

        if (clientMessage.getMessageType() == MessageType.JOINED) {
            TokenMutex.unlock();//UNLOCK 1n
        } else {
            AppConfig.timestampedErrorPrint("Joined handler got message that's not of type JOINED.");
        }

    }

}
