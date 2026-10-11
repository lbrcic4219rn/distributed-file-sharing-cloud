package servent.storage_handler;

import app.AppConfig;
import app.file_util.PullManager;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.storage_message.TellPullMessage;

@RequiredArgsConstructor
public class TellPullHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.TELL_PULL || !(clientMessage instanceof TellPullMessage message)) {
            AppConfig.timestampedErrorPrint("TellPullHandler got a message that is not TELL_PULL, discarding it");
            return;
        }

        if (MessageUtil.forwardedToTarget(message)) {
            return;
        }

        PullManager.received(message.getPath(), message.getFileInfo());
    }

}
