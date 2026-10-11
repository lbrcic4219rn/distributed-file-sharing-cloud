package servent;

import app.AppConfig;
import app.Cancellable;
import servent.base_handler.*;
import servent.base_message.Message;
import servent.base_message.util.MessageUtil;
import servent.failure_handler.*;
import servent.storage_handler.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServentListener implements Runnable, Cancellable {

    private final ExecutorService threadPool = Executors.newCachedThreadPool(runnable -> {
        Thread handlerThread = new Thread(runnable);
        handlerThread.setDaemon(true);
        return handlerThread;
    });
    private volatile boolean working = true;

    @Override
    public void run() {
        try (ServerSocket listenerSocket = new ServerSocket(AppConfig.myServentInfo.getListenerPort(), 100)) {
            /*
             * If there is no connection after 1s, wake up and see if we should terminate.
             */
            listenerSocket.setSoTimeout(1000);

            while (working) {
                handleNextMessage(listenerSocket);
            }
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't open listener socket on: " + AppConfig.myServentInfo.getListenerPort());
            System.exit(0);
        }
    }

    private void handleNextMessage(ServerSocket listenerSocket) {
        try {
            Socket clientSocket = listenerSocket.accept();

            //GOT A MESSAGE! <3
            Message clientMessage = MessageUtil.readMessage(clientSocket);
            if (clientMessage == null || clientMessage.getMessageType() == null) {
                return;
            }

            MessageHandler messageHandler = switch (clientMessage.getMessageType()) {
                case NEW_NODE -> new NewNodeHandler(clientMessage);
                case WELCOME -> new WelcomeHandler(clientMessage);
                case SORRY -> new SorryHandler(clientMessage);
                case UPDATE -> new UpdateHandler(clientMessage);
                case JOINED -> new JoinedHandler(clientMessage);
                case LEAVE -> new LeaveHandler(clientMessage);
                case TOKEN -> new TokenHandler(clientMessage);
                case ADD_INFORM -> new AddInformHandler(clientMessage);
                case ASK_PULL -> new AskPullHandler(clientMessage);
                case TELL_PULL -> new TellPullHandler(clientMessage);
                case REMOVE -> new RemoveHandler(clientMessage);
                case BACKUP_SYNC -> new BackupSyncHandler(clientMessage);
                case PING -> new PingHandler(clientMessage);
                case PONG -> new PongHandler(clientMessage);
                case SUSPECT_ASK -> new SuspectAskHandler(clientMessage);
                case SUSPECT_REPLY -> new SuspectReplyHandler(clientMessage);
                case NODE_FAILED -> new NodeFailedHandler(clientMessage);
                case TAKEOVER -> new TakeoverHandler(clientMessage);
            };

            boolean needsFullView = switch (clientMessage.getMessageType()) {
                case ASK_PULL, TELL_PULL -> true;
                default -> false;
            };
            threadPool.submit(() -> {
                if (needsFullView) {
                    AppConfig.chordState.awaitJoined();
                }
                messageHandler.run();
            });
        } catch (IOException e) {
            if (!(e instanceof SocketTimeoutException)) {
                AppConfig.timestampedErrorPrint("Problem while accepting a message", e);
            }
        }
    }

    @Override
    public void stop() {
        this.working = false;
    }

}
