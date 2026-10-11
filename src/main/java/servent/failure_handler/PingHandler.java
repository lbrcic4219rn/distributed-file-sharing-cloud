package servent.failure_handler;

import app.AppConfig;
import app.ServentInfo;
import lombok.RequiredArgsConstructor;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.failure_message.PingMessage;
import servent.failure_message.PongMessage;

@RequiredArgsConstructor
public class PingHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() != MessageType.PING || !(clientMessage instanceof PingMessage message)) {
            AppConfig.timestampedErrorPrint("PingHandler got a message that is not PING, discarding it");
            return;
        }

        ServentInfo sender = new ServentInfo(message.getSenderIpAddress(), message.getSenderPort());
        MessageUtil.sendTo(new PongMessage(), sender);
    }
}
