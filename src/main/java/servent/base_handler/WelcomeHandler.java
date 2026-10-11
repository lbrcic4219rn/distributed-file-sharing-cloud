package servent.base_handler;

import app.AppConfig;
import lombok.RequiredArgsConstructor;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.UpdateMessage;
import servent.base_message.WelcomeMessage;
import servent.base_message.util.MessageUtil;

@RequiredArgsConstructor
public class WelcomeHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() == MessageType.WELCOME) {
            WelcomeMessage welcomeMsg = (WelcomeMessage) clientMessage;

            AppConfig.chordState.init(welcomeMsg);

            UpdateMessage um = new UpdateMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                    AppConfig.chordState.getNextNodeIp(), AppConfig.chordState.getNextNodePort(), "");
            MessageUtil.sendMessage(um);

        } else {
            AppConfig.timestampedErrorPrint("Welcome handler got a message that is not WELCOME");
        }

    }

}
