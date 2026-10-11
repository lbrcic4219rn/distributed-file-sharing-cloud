package servent.failure;

import app.AppConfig;
import app.ServentInfo;
import app.file_util.FileInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.util.MessageUtil;
import servent.storage_message.BackupSyncMessage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BackupUtil {

    public static void resync() {
        Set<ServentInfo> buddies = new HashSet<>();
        Set<Integer> buddyIds = new HashSet<>();
        for (ServentInfo buddy : new ServentInfo[]{AppConfig.chordState.getPredecessor(), AppConfig.chordState.getSuccessor()}) {
            if (buddy != null && buddyIds.add(buddy.getChordId())) {
                buddies.add(buddy);
            }
        }

        Map<String, FileInfo> myFiles = AppConfig.storage.getOwnedSnapshot();
        long version = AppConfig.storage.nextBackupVersion();
        for (ServentInfo buddy : buddies) {
            MessageUtil.sendTo(new BackupSyncMessage(AppConfig.myServentInfo.getChordId(), version, myFiles), buddy);
        }

        Set<Integer> noLongerBuddies = new HashSet<>();
        for (Integer ownerId : AppConfig.storage.getBackupOwners()) {
            if (!buddyIds.contains(ownerId) && AppConfig.chordState.isMember(ownerId)) {
                noLongerBuddies.add(ownerId);
            }
        }
        AppConfig.storage.dropBackups(noLongerBuddies);
    }
}
