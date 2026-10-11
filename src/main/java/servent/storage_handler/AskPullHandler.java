package servent.storage_handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.file_util.FileInfo;
import servent.base_handler.MessageHandler;
import servent.base_message.Message;
import servent.base_message.MessageType;
import servent.base_message.util.MessageUtil;
import servent.storage_message.AskPullMessage;
import servent.storage_message.TellPullMessage;


@RequiredArgsConstructor
public class AskPullHandler implements MessageHandler {

	private final Message clientMessage;
	
	@Override
	public void run() {
		if (clientMessage.getMessageType() == MessageType.ASK_PULL) {
			AskPullMessage askPullMessage = (AskPullMessage) clientMessage;

			if (askPullMessage.getFileInfo().getOgNode() == AppConfig.myServentInfo.getChordId()){//ako smo mi od koga treba da povucemo
				FileInfo fileToSendBack = AppConfig.chordState.getStorageMap().get(askPullMessage.getFileInfo().getPath());
				if (fileToSendBack != null) {
					Message tellMessage = new TellPullMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
							AppConfig.chordState.getNextNodeIp(), AppConfig.chordState.getNextNodePort(),
							askPullMessage.getSenderIpAddress(), askPullMessage.getRequesterId(), fileToSendBack);
					MessageUtil.sendMessage(tellMessage);
				}
			}
			else {
				Message askMessage = new AskPullMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
						AppConfig.chordState.getNextNodeIp(), AppConfig.chordState.getNextNodePort(),askPullMessage.getRequesterId(), askPullMessage.getFileInfo());
				MessageUtil.sendMessage(askMessage);
			}

		} else {
			AppConfig.timestampedErrorPrint("Ask get handler got a message that is not ASK_GET");
		}
	}

}