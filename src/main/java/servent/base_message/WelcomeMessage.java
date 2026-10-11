package servent.base_message;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import app.file_util.FileInfo;

import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class WelcomeMessage extends BasicMessage {

	@Serial
	private static final long serialVersionUID = -8981406250652693908L;

	private final Map<String, FileInfo> storageMap;
	
	public WelcomeMessage(String senderIpAddress, int senderPort, String receiverIpAddress, int receiverPort, Map<String, FileInfo> storageMap) {
		super(MessageType.WELCOME, senderIpAddress, senderPort, receiverIpAddress, receiverPort);
		this.storageMap = storageMap;
	}

}
