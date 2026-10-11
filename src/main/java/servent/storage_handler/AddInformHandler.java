package servent.storage_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.storage_message.AddInformMessage;

@RequiredArgsConstructor
public class AddInformHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.ADD_INFORM || !(clientMessage instanceof AddInformMessage message)) {
            AppConfig.timestampedErrorPrint("AddInformHandler got a message that is not ADD_INFORM, discarding it");
            return;
        }

        if (RingBroadcaster.shouldSkip(message)) {
            return;
        }

        message.getEntries().forEach(AppConfig.storage::addEntry);

        MessageUtil.sendToSuccessor(message);
    }

}
