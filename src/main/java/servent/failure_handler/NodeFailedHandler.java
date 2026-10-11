package servent.failure_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.failure.FailureRecovery;
import servent.failure_message.NodeFailedMessage;

@RequiredArgsConstructor
public class NodeFailedHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.NODE_FAILED || !(clientMessage instanceof NodeFailedMessage message)) {
            AppConfig.timestampedErrorPrint("NodeFailedHandler got a message that is not NODE_FAILED, discarding it");
            return;
        }

        int failedId = message.getFailedNode().getChordId();

        if (failedId == AppConfig.myServentInfo.getChordId()) {
            AppConfig.timestampedErrorPrint("I was declared dead by " + message.getSenderIpAddress() + ":" + message.getSenderPort());
            return;
        }
        if (AppConfig.chordState.removeNode(failedId)) {
            AppConfig.timestampedStandardPrint("Node " + message.getFailedNode() + " failed, removed it from the system");
            FailureRecovery.onNodeRemoved(failedId);
        }
    }
}
