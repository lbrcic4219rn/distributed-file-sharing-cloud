package servent.base_handler;

import app.AppConfig;
import app.BootstrapClient;
import app.ChordState;
import app.ServentInfo;
import lombok.RequiredArgsConstructor;
import servent.base_message.JoinedMessage;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.UpdateMessage;
import servent.base_message.util.MessageUtil;
import servent.failure.BackupUtil;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class UpdateHandler implements MessageHandler {

    private final Message clientMessage;

    @Override
    public void run() {
        if (clientMessage.getMessageType() == MessageType.UPDATE) {
            int senderServentId = ChordState.chordHash(clientMessage.getSenderIpAddress() + ":" + clientMessage.getSenderPort());
            int receiverServentId = ChordState.chordHash(AppConfig.myServentInfo.getIpAddress() + ":" + AppConfig.myServentInfo.getListenerPort());


            if (senderServentId != receiverServentId) {
                ServentInfo newNodeInfo = new ServentInfo(clientMessage.getSenderIpAddress(), clientMessage.getSenderPort());
                List<ServentInfo> newNodes = new ArrayList<>();
                newNodes.add(newNodeInfo);
                AppConfig.chordState.addNodes(newNodes);

                BackupUtil.resync();
                String newMessageText;
                if (clientMessage.getMessageText().isEmpty()) {
                    newMessageText = AppConfig.myServentInfo.getIpAddress() + ":" + AppConfig.myServentInfo.getListenerPort();
                } else {
                    newMessageText = clientMessage.getMessageText() + "," + AppConfig.myServentInfo.getIpAddress() + ":" + AppConfig.myServentInfo.getListenerPort();
                }
                Message nextUpdate = new UpdateMessage(clientMessage.getSenderIpAddress(), clientMessage.getSenderPort(),
                        AppConfig.chordState.getNextNodeIp(), AppConfig.chordState.getNextNodePort(), newMessageText);
                MessageUtil.sendMessage(nextUpdate);
            } else {
                String messageText = clientMessage.getMessageText();
                String[] servents = messageText.split(",");

                List<ServentInfo> allNodes = new ArrayList<>();
                for (String servent : servents) {
                    String serventIp = servent.substring(0, servent.indexOf(':'));
                    int serventPort = Integer.parseInt(servent.substring(serventIp.length() + 1));
                    allNodes.add(new ServentInfo(serventIp, serventPort));
                }
                AppConfig.chordState.addNodes(allNodes);
                AppConfig.chordState.markJoined();
                BootstrapClient.notify("New", AppConfig.myServentInfo);
                BackupUtil.resync();

                //Zavrsili smo ukljucivanje, javimo nasem sledbeniku da moze da pozove mutex unlock
                String successorIp = AppConfig.chordState.getNextNodeIp();
                int successorPort = AppConfig.chordState.getNextNodePort();
                Message joinedMessage = new JoinedMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(), successorIp, successorPort);
                MessageUtil.sendMessage(joinedMessage);
            }
        } else {
            AppConfig.timestampedErrorPrint("Update message handler got message that is not UPDATE");
        }
    }

}
