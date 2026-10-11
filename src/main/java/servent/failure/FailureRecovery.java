package servent.failure;

import app.AppConfig;
import app.file_util.FileInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import mutex.TokenMutex;
import servent.base_message.util.RingBroadcaster;
import servent.failure_message.TakeoverMessage;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FailureRecovery {

    public static void onNodeRemoved(int failedId) {
        if (AppConfig.storage.hasBackup(failedId)) {
            Thread takeover = new Thread(() -> takeOver(failedId));
            takeover.setDaemon(true);
            takeover.start();
        }
        BackupUtil.resync();
    }

    private static void takeOver(int failedId) {
        int myId = AppConfig.myServentInfo.getChordId();

        TokenMutex.lock();
        try {
            Map<String, FileInfo> backup = AppConfig.storage.removeBackup(failedId);
            if (backup == null || !AppConfig.storage.hasEntriesOwnedBy(failedId)) {
                return;
            }

            AppConfig.storage.adopt(backup);
            AppConfig.storage.reassignOwner(failedId, myId);
            AppConfig.timestampedStandardPrint("Took over the files of failed node " + failedId + ": " + backup.keySet());

            RingBroadcaster.broadcastAndWait(new TakeoverMessage(myId, failedId, myId));
            BackupUtil.resync();
        } finally {
            TokenMutex.unlock();
        }
    }
}
