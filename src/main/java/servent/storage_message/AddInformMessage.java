package servent.storage_message;

import app.AppConfig;
import app.file_util.FileInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;
import servent.base_message.RingBroadcast;

import java.io.Serial;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class AddInformMessage extends BasicMessage implements RingBroadcast {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int originChordId;
    private final List<FileInfo> entries;

    public AddInformMessage(int originChordId, List<FileInfo> entries) {
        super(MessageType.ADD_INFORM, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.originChordId = originChordId;
        this.entries = entries;
    }

}
