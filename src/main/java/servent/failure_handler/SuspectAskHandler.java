package servent.failure_handler;

import app.AppConfig;
import app.ServentInfo;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.failure.FailureDetector;
import servent.failure_message.PingMessage;
import servent.failure_message.SuspectAskMessage;
import servent.failure_message.SuspectReplyMessage;

@RequiredArgsConstructor
public class SuspectAskHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.SUSPECT_ASK || !(clientMessage instanceof SuspectAskMessage message)) {
            AppConfig.timestampedErrorPrint("SuspectAskHandler got a message that is not SUSPECT_ASK, discarding it");
            return;
        }

        ServentInfo asker = new ServentInfo(message.getSenderIpAddress(), message.getSenderPort());
        ServentInfo suspect = message.getSuspect();

        long askedAt = System.currentTimeMillis();
        MessageUtil.sendTo(new PingMessage(), suspect);
        MessageUtil.pause(AppConfig.WEAK_TIMEOUT);

        boolean alive = FailureDetector.lastPongFrom(suspect.getChordId()) >= askedAt;
        AppConfig.timestampedStandardPrint("Checked " + suspect + " for " + asker + " - " + (alive ? "alive" : "not responding"));
        MessageUtil.sendTo(new SuspectReplyMessage(suspect, alive), asker);
    }
}
