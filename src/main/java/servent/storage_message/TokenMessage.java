package servent.storage_message;

import app.AppConfig;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;

import java.io.Serial;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TokenMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int generation;
    private final int creatorChordId;

    public TokenMessage(int generation, int creatorChordId) {
        super(MessageType.TOKEN, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
        this.generation = generation;
        this.creatorChordId = creatorChordId;
    }

}
