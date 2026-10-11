package servent.storage_message;

import app.AppConfig;
import app.file_util.FileInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;

import java.io.Serial;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class BackupSyncMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int ownerChordId;
    private final long version;
    private final Map<String, FileInfo> files;

    public BackupSyncMessage(int ownerChordId, long version, Map<String, FileInfo> files) {
        super(MessageType.BACKUP_SYNC, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.ownerChordId = ownerChordId;
        this.version = version;
        this.files = files;
    }
}
