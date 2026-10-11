package servent.failure_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.failure_message.TakeoverMessage;

@RequiredArgsConstructor
public class TakeoverHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.TAKEOVER || !(clientMessage instanceof TakeoverMessage message)) {
            AppConfig.timestampedErrorPrint("TakeoverHandler got a message that is not TAKEOVER, discarding it");
            return;
        }

        if (RingBroadcaster.shouldSkip(message)) {
            return;
        }

        AppConfig.storage.reassignOwner(message.getOldOwnerChordId(), message.getNewOwnerChordId());

        AppConfig.storage.removeBackup(message.getOldOwnerChordId());

        MessageUtil.sendToSuccessor(message);
    }
}
