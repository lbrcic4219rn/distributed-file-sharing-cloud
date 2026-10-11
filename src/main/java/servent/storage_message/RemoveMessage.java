package servent.storage_message;

import app.AppConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;
import servent.base_message.RingBroadcast;

import java.io.Serial;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RemoveMessage extends BasicMessage implements RingBroadcast {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int originChordId;
    private final List<String> paths;

    public RemoveMessage(int originChordId, List<String> paths) {
        super(MessageType.REMOVE, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.originChordId = originChordId;
        this.paths = paths;
    }

}
