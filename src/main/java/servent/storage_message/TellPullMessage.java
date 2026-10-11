package servent.storage_message;

import app.AppConfig;
import app.file_util.FileInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;
import servent.base_message.Routed;

import java.io.Serial;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TellPullMessage extends BasicMessage implements Routed {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int targetChordId;
    private final String path;
    private final FileInfo fileInfo;

    public TellPullMessage(int targetChordId, String path, FileInfo fileInfo) {
        super(MessageType.TELL_PULL, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.targetChordId = targetChordId;
        this.path = path;
        this.fileInfo = fileInfo;
    }

}
