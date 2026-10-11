package servent.base_message;

import app.AppConfig;
import app.ChordState;
import app.ServentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import servent.storage_message.AskPullMessage;
import servent.storage_message.TokenMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BasicMessageTest {

    @BeforeEach
    void setUp() {
        ChordState.CHORD_SIZE = 64;
        AppConfig.myServentInfo = new ServentInfo("localhost", 1000);
    }

    @Test
    void everyMessageGetsItsOwnId() {
        assertNotEquals(new TokenMessage(0, 1), new TokenMessage(0, 1));
    }

    @Test
    void redirectedMessageIsTheSameMessageForAnotherReceiver() {
        AskPullMessage original = new AskPullMessage(5, 7, "a.txt");

        BasicMessage redirected = original.redirectTo("otherhost", 1234);

        assertEquals(original, redirected);
        assertEquals(original.hashCode(), redirected.hashCode());
        assertEquals("otherhost", redirected.getReceiverIpAddress());
        assertEquals(1234, redirected.getReceiverPort());
        assertEquals("a.txt", ((AskPullMessage) redirected).getPath());

        assertEquals(1000, original.getReceiverPort());
        assertEquals(1000, redirected.getSenderPort());
    }
}
