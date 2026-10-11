package servent.storage_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import mutex.TokenMutex;

@RequiredArgsConstructor
public class TokenHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {

        if (clientMessage.getMessageType() == MessageType.TOKEN) {
            TokenMutex.receiveToken();
        } else {
            AppConfig.timestampedErrorPrint("Token handler got message that's not of type TOKEN.");
        }

    }

}
