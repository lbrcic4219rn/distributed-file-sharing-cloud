package servent.failure_message;

import app.AppConfig;
import servent.base_message.BasicMessage;
import servent.base_message.MessageType;

import java.io.Serial;


public class PongMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = 1L;


    public PongMessage() {
        super(MessageType.PONG, AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort());
    }
}
