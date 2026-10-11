package servent.base_message.util;

import app.AppConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.BasicMessage;
import servent.base_message.RingBroadcast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RingBroadcaster {

    private static final Map<Integer, CountDownLatch> pending = new ConcurrentHashMap<>();

    public static long lapEstimate() {
        return (AppConfig.chordState.getAllNodes().size() + 2) * 1500L;
    }

    public static long broadcastTimeout() {
        return lapEstimate() + 5000;
    }

    public static void broadcastAndWait(BasicMessage message) {
        if (AppConfig.chordState.getSuccessor() == null) {
            return;
        }

        CountDownLatch done = new CountDownLatch(1);
        pending.put(message.getMessageId(), done);
        MessageUtil.sendToSuccessor(message);

        try {
            if (!done.await(broadcastTimeout(), TimeUnit.MILLISECONDS)) {
                AppConfig.timestampedErrorPrint("Broadcast didn't come back in time: " + message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pending.remove(message.getMessageId());
        }
    }

    public static boolean shouldSkip(RingBroadcast message) {
        int origin = message.getOriginChordId();

        if (origin == AppConfig.myServentInfo.getChordId()) {
            CountDownLatch done = pending.get(message.getMessageId());
            if (done != null) {
                done.countDown();
            }
            return true;
        }
        if (!AppConfig.chordState.isMember(origin)) {
            AppConfig.timestampedErrorPrint("Discarding broadcast of a node that is not in the system: " + message);
            return true;
        }
        return false;
    }
}
