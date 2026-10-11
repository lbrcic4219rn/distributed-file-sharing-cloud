package servent.failure_message;

import app.AppConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;
import servent.base_message.RingBroadcast;

import java.io.Serial;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TakeoverMessage extends BasicMessage implements RingBroadcast {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int originChordId;
    private final int oldOwnerChordId;
    private final int newOwnerChordId;

    public TakeoverMessage(int originChordId, int oldOwnerChordId, int newOwnerChordId) {
        super(MessageType.TAKEOVER, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.originChordId = originChordId;
        this.oldOwnerChordId = oldOwnerChordId;
        this.newOwnerChordId = newOwnerChordId;
    }
}
