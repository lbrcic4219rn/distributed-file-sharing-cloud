package servent.storage_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;

@RequiredArgsConstructor
public class RemoveHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {

        if (clientMessage.getMessageType() == MessageType.REMOVE) {
            AppConfig.chordState.removeFileFromStorage(clientMessage.getMessageText());
        }
        else {
            AppConfig.timestampedErrorPrint("Remove handler got message that's not of type REMOVE.");
        }

    }

}
