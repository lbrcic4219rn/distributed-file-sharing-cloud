package app;

import java.io.Serial;
import lombok.Getter;

import java.io.Serializable;

/**
 * This is an immutable class that holds all the information for a servent.
 *
 * @author bmilojkovic
 */
@Getter
public class ServentInfo implements Serializable {

	@Serial
	private static final long serialVersionUID = 4971234960037495511L;
	private final String ipAddress;
	private final int listenerPort;
	private final int chordId;
	
	public ServentInfo(String ipAddress, int listenerPort) {
		this.ipAddress = ipAddress;
		this.listenerPort = listenerPort;
		this.chordId = ChordState.chordHash(ipAddress + ":" + listenerPort);
	}

	@Override
	public String toString() {
		return "[cId: " + chordId + "|ipA: " + ipAddress + "|lP: " + listenerPort + "]";
	}

}
