package servent.base_message;

import java.io.Serial;
import lombok.Getter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import app.ChordState;

/**
 * A default message implementation. This should cover most situations.
 * If you want to add stuff, remember to think about the modificator methods.
 * If you don't override the modificators, you might drop stuff.
 * @author bmilojkovic
 *
 */
@Getter
public class BasicMessage implements Message {

	@Serial
	private static final long serialVersionUID = -9075856313609777945L;

	private final MessageType messageType;
	private final String senderIpAddress;
	private final int senderPort;
	private final String receiverIpAddress;
	private final int receiverPort;
	private final String messageText;

	//This gives us a unique id - incremented in every natural constructor.
	private static final AtomicInteger messageCounter = new AtomicInteger(0);
	private final int messageId;

	public BasicMessage(MessageType type, String senderIpAddress, int senderPort, String receiverIpAddress, int receiverPort) {
		this.messageType = type;
		this.senderIpAddress = senderIpAddress;
		this.senderPort = senderPort;
		this.receiverIpAddress = receiverIpAddress;
		this.receiverPort = receiverPort;
		this.messageText = "";

		this.messageId = messageCounter.getAndIncrement();
	}

	public BasicMessage(MessageType type, String senderIpAddress, int senderPort, String receiverIpAddress, int receiverPort, String messageText) {
		this.messageType = type;
		this.senderIpAddress = senderIpAddress;
		this.senderPort = senderPort;
		this.receiverIpAddress = receiverIpAddress;
		this.receiverPort = receiverPort;
		this.messageText = messageText;

		this.messageId = messageCounter.getAndIncrement();
	}

	/**
	 * Comparing messages is based on their unique id and the original sender port.
	 */
	@Override
	public boolean equals(Object obj) {
		return obj instanceof BasicMessage other
				&& getMessageId() == other.getMessageId()
				&& getSenderPort() == other.getSenderPort();
	}

	/**
	 * Hash needs to mirror equals, especially if we are gonna keep this object
	 * in a set or a map. So, this is based on message id and original sender id also.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(getMessageId(), getSenderPort());
	}

	/**
	 * Returns the message in the format: <code>[sender_id|sender_port|message_id|text|type|receiver_port|receiver_id]</code>
	 */
	@Override
	public String toString() {

		String senderIpAddressPort = getSenderIpAddress() + ":" + getSenderPort();
		String receiverIpAddressPort = getReceiverIpAddress() + ":" + getReceiverPort();

		return "[cHashSp:" + ChordState.chordHash(senderIpAddressPort) + "|sPort:" + senderIpAddressPort + "|mId:" +
				getMessageId() + "|mTxt:" + getMessageText() + "|mTyp:" + getMessageType() + "|rPort:" +
				receiverIpAddressPort + "|cHashRp:" + ChordState.chordHash(receiverIpAddressPort) + "]";

	}

}
