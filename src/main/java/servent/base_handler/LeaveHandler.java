package servent.base_handler;

import app.AppConfig;
import app.ServentInfo;
import lombok.RequiredArgsConstructor;
import servent.base_message.LeaveMessage;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.failure.BackupUtil;

@RequiredArgsConstructor
public class LeaveHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.LEAVE || !(clientMessage instanceof LeaveMessage message)) {
            AppConfig.timestampedErrorPrint("LeaveHandler got a message that is not LEAVE, discarding it");
            return;
        }

        if (RingBroadcaster.shouldSkip(message)) {
            return;
        }

        int leavingId = message.getLeavingNode().getChordId();

        ServentInfo nextNode = AppConfig.chordState.getSuccessor();

        AppConfig.chordState.removeNode(leavingId);
        AppConfig.storage.reassignOwner(leavingId, message.getNewOwnerChordId());
        AppConfig.storage.removeBackup(leavingId);
        if (message.getNewOwnerChordId() == AppConfig.myServentInfo.getChordId()) {
            AppConfig.storage.adopt(message.getFiles());
            AppConfig.timestampedStandardPrint("Took over the files of leaving node " + leavingId + ": " + message.getFiles().keySet());
        }
        AppConfig.timestampedStandardPrint("Node " + message.getLeavingNode() + " left the system");

        MessageUtil.sendToSuccessor(message, nextNode);
        BackupUtil.resync();
    }
}
