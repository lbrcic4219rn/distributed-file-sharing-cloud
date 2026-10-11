package servent.storage_message;

import app.AppConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;
import servent.base_message.Routed;

import java.io.Serial;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class AskPullMessage extends BasicMessage implements Routed {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int targetChordId;
    private final int requesterChordId;
    private final String path;

    public AskPullMessage(int targetChordId, int requesterChordId, String path) {
        super(MessageType.ASK_PULL, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.targetChordId = targetChordId;
        this.requesterChordId = requesterChordId;
        this.path = path;
    }
}
