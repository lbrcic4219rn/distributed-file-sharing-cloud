package servent.base_message;

import app.AppConfig;
import app.ServentInfo;
import app.file_util.FileInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LeaveMessage extends BasicMessage implements RingBroadcast {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int originChordId;
    private final ServentInfo leavingNode;
    private final int newOwnerChordId;
    private final Map<String, FileInfo> files;

    public LeaveMessage(int originChordId, ServentInfo leavingNode, int newOwnerChordId, Map<String, FileInfo> files) {
        super(MessageType.LEAVE, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.originChordId = originChordId;
        this.leavingNode = leavingNode;
        this.newOwnerChordId = newOwnerChordId;
        this.files = files;
    }
}
