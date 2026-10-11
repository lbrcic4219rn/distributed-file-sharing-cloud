package mutex;

import app.AppConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.Message;
import servent.base_message.util.MessageUtil;
import servent.storage_message.TokenMessage;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TokenMutex {

    private static final Object MONITOR = new Object();

    private static boolean haveToken = false;
    private static boolean wantLock = false;

    //Poziva ga samo prvi cvor u sistemu
    public static void init() {
        synchronized (MONITOR) {
            haveToken = true;
        }
    }

    public static void lock() {
        synchronized (MONITOR) {
            wantLock = true;

            //receiveToken() wakes us up once the token arrives
            while (!haveToken) {
                try {
                    MONITOR.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the token", e);
                }
            }
        }
    }

    public static void unlock() {
        synchronized (MONITOR) {
            haveToken = false;
            wantLock = false;
        }
        sendToken();
    }

    public static void receiveToken() {
        synchronized (MONITOR) {
            if (wantLock) {
                haveToken = true;
                MONITOR.notifyAll();
                return;
            }
        }
        sendToken();
    }

    private static void sendToken() {
        String nextNodeIp = AppConfig.chordState.getNextNodeIp();
        int nextNodePort = AppConfig.chordState.getNextNodePort();
        Message tokenMessage = new TokenMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(), nextNodeIp, nextNodePort);
        MessageUtil.sendMessage(tokenMessage);
    }

}
