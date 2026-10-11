package servent.storage_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import mutex.TokenMutex;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.storage_message.TokenMessage;

@RequiredArgsConstructor
public class TokenHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.TOKEN || !(clientMessage instanceof TokenMessage message)) {
            AppConfig.timestampedErrorPrint("TokenHandler got a message that is not TOKEN, discarding it");
            return;
        }

        TokenMutex.receiveToken(message.getGeneration(), message.getCreatorChordId());
    }

}
