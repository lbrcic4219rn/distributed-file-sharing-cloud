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
public class SuspectAskMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ServentInfo suspect;

    public SuspectAskMessage(ServentInfo suspect) {
        super(MessageType.SUSPECT_ASK, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.suspect = suspect;
    }
}
