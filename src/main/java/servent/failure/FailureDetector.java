package servent.failure;

import app.AppConfig;
import app.BootstrapClient;
import app.Cancellable;
import app.ServentInfo;
import mutex.TokenMutex;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.failure_message.NodeFailedMessage;
import servent.failure_message.PingMessage;
import servent.failure_message.SuspectAskMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FailureDetector implements Runnable, Cancellable {

    private static final Map<Integer, Long> lastPong = new ConcurrentHashMap<>();

    private static final Map<Integer, Boolean> verdicts = new ConcurrentHashMap<>();

    private static final String NODE = "Node ";

    private volatile boolean working = true;

    private ServentInfo watched = null;
    private long watchedSince = 0;
    private boolean suspicious = false;

    private long tokenWatchStart = -1;
    private long tokenWatchDeadline = -1;

    public static void pongReceived(int chordId) {
        lastPong.put(chordId, System.currentTimeMillis());
    }

    public static long lastPongFrom(int chordId) {
        return lastPong.getOrDefault(chordId, 0L);
    }

    public static void verdictReceived(int suspectId, boolean alive) {
        verdicts.put(suspectId, alive);
    }

    @Override
    public void run() {
        long pingInterval = Math.max(250, AppConfig.WEAK_TIMEOUT / 4);

        while (working) {
            MessageUtil.pause(pingInterval);
            try {
                checkSuccessor();
                checkToken();
            } catch (RuntimeException e) {
                AppConfig.timestampedErrorPrint("Problem in failure detector", e);
            }
        }
    }

    private void checkSuccessor() {
        long now = System.currentTimeMillis();
        ServentInfo successor = AppConfig.chordState.getSuccessor();

        if (successor == null) {
            watched = null;
            return;
        }
        if (watched == null || watched.getChordId() != successor.getChordId()) {
            watched = successor;
            watchedSince = now;
            suspicious = false;
            verdicts.remove(successor.getChordId());
        }

        MessageUtil.sendTo(new PingMessage(), watched);

        int watchedId = watched.getChordId();
        long silentFor = now - Math.max(watchedSince, lastPongFrom(watchedId));

        if (silentFor <= AppConfig.WEAK_TIMEOUT) {
            if (suspicious) {
                AppConfig.timestampedStandardPrint(NODE + watched + " is responding again, no longer suspicious");
                suspicious = false;
                verdicts.remove(watchedId);
            }
            return;
        }

        if (!suspicious) {
            suspicious = true;
            AppConfig.timestampedStandardPrint(NODE + watched + " is suspicious - silent for " + silentFor + " ms");
            askForConfirmation();
            return;
        }

        Boolean alive = verdicts.get(watchedId);
        if (Boolean.TRUE.equals(alive)) {
            AppConfig.timestampedStandardPrint(NODE + watched + " answered to another node, no longer suspicious");
            watchedSince = now;
            suspicious = false;
            verdicts.remove(watchedId);
            return;
        }

        long limit = alive == null ? AppConfig.STRONG_TIMEOUT + AppConfig.WEAK_TIMEOUT : AppConfig.STRONG_TIMEOUT;
        if (silentFor > limit) {
            declareFailed(watched);
            watched = null;
        }
    }

    private void askForConfirmation() {
        List<ServentInfo> others = AppConfig.chordState.getAllNodes();
        others.removeIf(node -> node.getChordId() == watched.getChordId());

        if (others.isEmpty()) {
            verdicts.put(watched.getChordId(), false);
            return;
        }
        ServentInfo helper = others.getFirst();
        AppConfig.timestampedStandardPrint("Asking " + helper + " to check " + watched);
        MessageUtil.sendTo(new SuspectAskMessage(watched), helper);
    }

    private void declareFailed(ServentInfo failed) {
        AppConfig.timestampedStandardPrint(NODE + failed + " is declared dead, removing it from the system");

        AppConfig.chordState.removeNode(failed.getChordId());
        for (ServentInfo node : AppConfig.chordState.getAllNodes()) {
            MessageUtil.sendTo(new NodeFailedMessage(failed), node);
        }

        BootstrapClient.notify("Bye", failed);

        FailureRecovery.onNodeRemoved(failed.getChordId());

        tokenWatchStart = System.currentTimeMillis();
        tokenWatchDeadline = tokenWatchStart + 2 * RingBroadcaster.lapEstimate() + RingBroadcaster.broadcastTimeout();
    }

    private void checkToken() {
        if (tokenWatchDeadline < 0 || System.currentTimeMillis() < tokenWatchDeadline) {
            return;
        }
        if (!TokenMutex.seenSince(tokenWatchStart)) {
            TokenMutex.regenerate();
        }
        tokenWatchDeadline = -1;
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
