package servent.failure_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.failure.FailureDetector;
import servent.failure_message.SuspectReplyMessage;

@RequiredArgsConstructor
public class SuspectReplyHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.SUSPECT_REPLY || !(clientMessage instanceof SuspectReplyMessage message)) {
            AppConfig.timestampedErrorPrint("SuspectReplyHandler got a message that is not SUSPECT_REPLY, discarding it");
            return;
        }

        FailureDetector.verdictReceived(message.getSuspect().getChordId(), message.isAlive());
    }
}
