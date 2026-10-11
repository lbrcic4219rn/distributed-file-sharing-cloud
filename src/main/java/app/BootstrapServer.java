package app;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootstrapServer {

    private final List<String> activeServents;
    private final ExecutorService threadPool = Executors.newWorkStealingPool();
    private final Random rand = new Random(System.currentTimeMillis());
    private volatile boolean working = true;

    public BootstrapServer() {
        activeServents = new CopyOnWriteArrayList<>();
    }

    /**
     * Expects one command line argument - the port to listen on.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            AppConfig.timestampedErrorPrint("Bootstrap started without port argument.");
        }

        int bsPort = 0;
        try {
            bsPort = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            AppConfig.timestampedErrorPrint("Bootstrap port not valid: " + args[0]);
            System.exit(0);
        }

        AppConfig.timestampedStandardPrint("Bootstrap server started on port: " + bsPort);

        BootstrapServer bs = new BootstrapServer();
        bs.doBootstrap(bsPort);

    }

    public void doBootstrap(int bsPort) {

        Thread cliThread = new Thread(new CLIWorker());
        cliThread.start();

        try (ServerSocket listenerSocket = new ServerSocket(bsPort)) {
            listenerSocket.setSoTimeout(1000);

            while (working) {
                acceptServent(listenerSocket);
            }
        } catch (IOException e1) {
            AppConfig.timestampedErrorPrint("Problem while opening listener socket.");
            System.exit(0);
        }

    }

    private void acceptServent(ServerSocket listenerSocket) {
        try {
            Socket newServentSocket = listenerSocket.accept();
            Scanner socketScanner = new Scanner(newServentSocket.getInputStream());
            String message = socketScanner.nextLine();

            threadPool.submit(new MessageHandler(message, newServentSocket, socketScanner));
        } catch (IOException e) {
            if (!(e instanceof SocketTimeoutException)) {
                AppConfig.timestampedErrorPrint("Problem while accepting a servent", e);
            }
        }
    }

    private class CLIWorker implements Runnable {
        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);

            String line;
            while (sc.hasNextLine()) {
                line = sc.nextLine();

                if (line.equals("stop")) {
                    working = false;
                    break;
                }
            }

            sc.close();
        }
    }

    @RequiredArgsConstructor
    private class MessageHandler implements Runnable {

        private final String message;
        private final Socket newServentSocket;
        private final Scanner socketScanner;

        @Override
        public void run() {

            switch (message) {
                case "Hail" -> {
                    String newIpPort = socketScanner.nextLine();
                    AppConfig.timestampedStandardPrint("got " + newIpPort);

                    try {
                        PrintWriter socketWriter = new PrintWriter(newServentSocket.getOutputStream());

                        if (activeServents.isEmpty()) {
                            AppConfig.timestampedStandardPrint("adding " + newIpPort);
                            socketWriter.write("-1\n");
                            activeServents.add(newIpPort); //first one doesn't need to confirm
                        } else {
                            String randServent = activeServents.get(rand.nextInt(activeServents.size()));
                            socketWriter.write(randServent + "\n");
                        }

                        socketWriter.flush();
                    } catch (IOException e) {
                        AppConfig.timestampedErrorPrint("Couldn't reply to " + newIpPort, e);
                    }
                }
                case "New" -> {
                    String newIpPort = socketScanner.nextLine();
                    AppConfig.timestampedStandardPrint("adding " + newIpPort);
                    activeServents.add(newIpPort);
                }
                case "Bye" -> {
                    String newIpPort = socketScanner.nextLine();
                    AppConfig.timestampedStandardPrint("removing " + newIpPort);
                    activeServents.remove(newIpPort);
                }
                default -> AppConfig.timestampedErrorPrint("Unknown bootstrap message: " + message);
            }

            try {
                newServentSocket.close();
            } catch (IOException e) {
                AppConfig.timestampedErrorPrint("Couldn't close servent socket", e);
            }

        }

    }
}
