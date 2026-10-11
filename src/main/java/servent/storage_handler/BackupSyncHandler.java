package servent.storage_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.storage_message.BackupSyncMessage;

@RequiredArgsConstructor
public class BackupSyncHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.BACKUP_SYNC || !(clientMessage instanceof BackupSyncMessage message)) {
            AppConfig.timestampedErrorPrint("BackupSyncHandler got a message that is not BACKUP_SYNC, discarding it");
            return;
        }

        AppConfig.storage.putBackup(message.getOwnerChordId(), message.getVersion(), message.getFiles());
    }
}
