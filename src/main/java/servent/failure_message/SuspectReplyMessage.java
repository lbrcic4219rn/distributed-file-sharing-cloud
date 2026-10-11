package servent.failure_message;

import app.AppConfig;
import app.ServentInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;

import java.io.Serial;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SuspectReplyMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ServentInfo suspect;
    private final boolean alive;

    public SuspectReplyMessage(ServentInfo suspect, boolean alive) {
        super(MessageType.SUSPECT_REPLY, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.suspect = suspect;
        this.alive = alive;
    }
}
