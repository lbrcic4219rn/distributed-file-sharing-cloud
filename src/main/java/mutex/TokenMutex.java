package mutex;

import app.AppConfig;
import app.ServentInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.util.DelayedMessageSender;
import servent.base_message.util.MessageUtil;
import servent.storage_message.TokenMessage;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TokenMutex {

    private static final Object MONITOR = new Object();

    private static boolean haveToken = false;
    private static boolean inUse = false;
    private static int waiting = 0;

    private static int generation = 0;
    private static int creator = -1;
    private static long lastSeen = 0;

    //Poziva ga samo prvi cvor u sistemu
    public static void init() {
        synchronized (MONITOR) {
            haveToken = true;
            lastSeen = System.currentTimeMillis();
        }
    }

    public static void lock() {
        synchronized (MONITOR) {
            waiting++;
            try {
                while (!haveToken || inUse) {
                    MONITOR.wait();
                }
                inUse = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the token", e);
            } finally {
                waiting--;
            }
        }
    }

    public static void unlock() {
        synchronized (MONITOR) {
            if (!inUse) {
                return;
            }
            inUse = false;
        }
        passTokenOn();
    }

    public static void receiveToken(int tokenGeneration, int tokenCreator) {
        synchronized (MONITOR) {
            int order = compare(tokenGeneration, tokenCreator, generation, creator);
            if (order < 0 || (order == 0 && haveToken)) {
                AppConfig.timestampedErrorPrint("Discarding old token (" + tokenGeneration + ", " + tokenCreator + ")");
                return;
            }
            generation = tokenGeneration;
            creator = tokenCreator;
            haveToken = true;
            lastSeen = System.currentTimeMillis();

            if (inUse || waiting > 0) {
                MONITOR.notifyAll();
                return;
            }
        }
        passTokenOn();
    }

    public static void regenerate() {
        synchronized (MONITOR) {
            generation++;
            creator = AppConfig.myServentInfo.getChordId();
            AppConfig.timestampedStandardPrint("Token was lost, created a new one (" + generation + ", " + creator + ")");
        }
        receiveToken(generation, creator);
    }

    public static boolean seenSince(long timestamp) {
        synchronized (MONITOR) {
            return haveToken || lastSeen >= timestamp;
        }
    }

    public static void handOver(ServentInfo receiver) {
        TokenMessage token;
        synchronized (MONITOR) {
            inUse = false;
            haveToken = false;
            token = new TokenMessage(generation, creator);
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            if (DelayedMessageSender.deliver(token.redirectTo(receiver.getIpAddress(), receiver.getListenerPort()))) {
                return;
            }
            MessageUtil.pause(500);
        }
        AppConfig.timestampedErrorPrint("Couldn't hand the token over to " + receiver);
    }

    private static int compare(int generationA, int creatorA, int generationB, int creatorB) {
        return generationA != generationB ? Integer.compare(generationA, generationB) : Integer.compare(creatorA, creatorB);
    }

    private static void passTokenOn() {
        TokenMessage token;
        synchronized (MONITOR) {
            if (!haveToken || inUse) {
                return;
            }
            if (AppConfig.chordState.getSuccessor() == null) {
                MONITOR.notifyAll();
                return;
            }
            haveToken = false;
            token = new TokenMessage(generation, creator);
        }

        Thread sender = new Thread(() -> {
            DelayedMessageSender.artificialDelay();
            while (true) {
                ServentInfo successor = AppConfig.chordState.getSuccessor();
                if (successor == null) {
                    receiveToken(token.getGeneration(), token.getCreatorChordId());
                    return;
                }
                if (DelayedMessageSender.deliver(token.redirectTo(successor.getIpAddress(), successor.getListenerPort()))) {
                    return;
                }
                MessageUtil.pause(500);
            }
        });
        sender.setDaemon(true);
        sender.start();
    }

}
