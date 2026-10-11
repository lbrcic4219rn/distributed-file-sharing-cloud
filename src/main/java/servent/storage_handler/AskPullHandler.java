package servent.storage_handler;

import app.AppConfig;
import app.file_util.FileInfo;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.storage_message.AskPullMessage;
import servent.storage_message.TellPullMessage;

@RequiredArgsConstructor
public class AskPullHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.ASK_PULL || !(clientMessage instanceof AskPullMessage message)) {
            AppConfig.timestampedErrorPrint("AskPullHandler got a message that is not ASK_PULL, discarding it");
            return;
        }

        if (MessageUtil.forwardedToTarget(message)) {
            return;
        }

        FileInfo file = AppConfig.storage.getOwned(message.getPath());
        MessageUtil.route(new TellPullMessage(message.getRequesterChordId(), message.getPath(), file));
    }

}
