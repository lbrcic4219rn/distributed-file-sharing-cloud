package servent.failure_handler;

import app.AppConfig;
import app.ServentInfo;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.failure.FailureDetector;
import servent.failure_message.PongMessage;

@RequiredArgsConstructor
public class PongHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.PONG || !(clientMessage instanceof PongMessage message)) {
            AppConfig.timestampedErrorPrint("PongHandler got a message that is not PONG, discarding it");
            return;
        }

        ServentInfo sender = new ServentInfo(message.getSenderIpAddress(), message.getSenderPort());
        FailureDetector.pongReceived(sender.getChordId());
    }
}
