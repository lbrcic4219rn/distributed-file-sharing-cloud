package servent.storage_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.failure.BackupUtil;
import servent.storage_message.RemoveMessage;

@RequiredArgsConstructor
public class RemoveHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.REMOVE || !(clientMessage instanceof RemoveMessage message)) {
            AppConfig.timestampedErrorPrint("RemoveHandler got a message that is not REMOVE, discarding it");
            return;
        }

        if (RingBroadcaster.shouldSkip(message)) {
            return;
        }

        if (AppConfig.storage.removeEntries(message.getPaths())) {
            AppConfig.timestampedStandardPrint("Removed from this node: " + message.getPaths());

            BackupUtil.resync();
        }

        MessageUtil.sendToSuccessor(message);
    }

}
