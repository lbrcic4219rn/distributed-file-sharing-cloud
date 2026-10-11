package app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This class implements the logic for starting multiple servent instances.
 * To use it, invoke startServentTest with a directory name as parameter.
 * This directory should include:
 * <ul>
 * <li>A <code>servent_list.properties</code> file (explained in {@link AppConfig} class</li>
 * <li>A directory called <code>output</code> </li>
 * <li>A directory called <code>error</code> </li>
 * <li>A directory called <code>input</code> with text files called
 * <code> servent0_in.txt </code>, <code>servent1_in.txt</code>, ... and so on for each servent.
 * These files should contain the commands for each servent, as they would be entered in console.</li>
 * </ul>
 *
 * @author bmilojkovic
 */
public class MultipleServentStarter {


    private static final String JAVA_EXECUTABLE = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

    /**
     * The parameter for this function should be the name of a directory that
     * contains a servent_list.properties file which will describe our distributed system.
     */
    private static void startServentTest(String testName) {
        List<Process> serventProcesses = new ArrayList<>();

        AppConfig.readConfig(testName + "/servent_list.properties", 0);

        AppConfig.timestampedStandardPrint("Starting multiple servent runner. "
                + "If servents do not finish on their own, type \"stop\" to finish them");

        try {
            Files.createDirectories(Path.of(testName, "output"));
            Files.createDirectories(Path.of(testName, "error"));
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't create the output directories in " + testName, e);
            return;
        }

        Process bsProcess;
        ProcessBuilder bsBuilder = new ProcessBuilder(JAVA_EXECUTABLE, "-cp", System.getProperty("java.class.path"), "app.BootstrapServer", String.valueOf(AppConfig.BOOTSTRAP_PORT));
        bsBuilder.redirectOutput(new File(testName + "/output/bootstrap_out.txt"));
        bsBuilder.redirectError(new File(testName + "/error/bootstrap_err.txt"));
        try {
            bsProcess = bsBuilder.start();
        } catch (IOException e1) {
            AppConfig.timestampedErrorPrint("Couldn't start the bootstrap server", e1);
            return;
        }

        //wait for bootstrap to start
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
        }

        int serventCount = AppConfig.SERVENT_COUNT;
        int startGap = AppConfig.intProperty("start_gap", 10000);
        Timer killTimer = new Timer("servent-killer", true);

        for (int i = 0; i < serventCount; i++) {
            try {
                ProcessBuilder builder = new ProcessBuilder(JAVA_EXECUTABLE, "-cp", System.getProperty("java.class.path"), "app.ServentMain",
                        testName + "/servent_list.properties", String.valueOf(i));

                //We use files to read and write.
                //System.out, System.err and System.in will point to these files.
                builder.redirectOutput(new File(testName + "/output/servent" + i + "_out.txt"));
                builder.redirectError(new File(testName + "/error/servent" + i + "_err.txt"));
                builder.redirectInput(new File(testName + "/input/servent" + i + "_in.txt"));

                //Starts the servent as a completely separate process.
                Process p = builder.start();
                serventProcesses.add(p);

                int killAfter = AppConfig.intProperty("servent" + i + ".kill_after", -1);
                if (killAfter >= 0) {
                    int serventId = i;
                    killTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            AppConfig.timestampedStandardPrint("Killing servent " + serventId);
                            p.destroyForcibly();
                        }
                    }, killAfter);
                }
            } catch (IOException e) {
                AppConfig.timestampedErrorPrint("Couldn't start servent " + i, e);
            }
            try {
                Thread.sleep(startGap);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Thread t = new Thread(new ServentCLI(serventProcesses, bsProcess));

        t.start(); //CLI thread waiting for user to type "stop".

        for (Process process : serventProcesses) {
            try {
                process.waitFor(); //Wait for graceful process finish.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        AppConfig.timestampedStandardPrint("All servent processes finished. Type \"stop\" to halt bootstrap.");
        try {
            bsProcess.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        startServentTest(args.length > 0 ? args[0] : "chord");
    }

    /**
     * We will wait for user stop in a separate thread.
     * The main thread is waiting for processes to end naturally.
     */
    private static class ServentCLI implements Runnable {

        private final List<Process> serventProcesses;
        private final Process bsProcess;

        public ServentCLI(List<Process> serventProcesses, Process bsProcess) {
            this.serventProcesses = serventProcesses;
            this.bsProcess = bsProcess;
        }

        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);

            while (true) {
                String line = sc.nextLine();

                if (line.equals("stop")) {
                    for (Process process : serventProcesses) {
                        process.destroy();
                    }
                    bsProcess.destroy();
                    break;
                }
            }

            sc.close();
        }
    }

}
