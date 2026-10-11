package app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * This class implements the logic for starting multiple servent instances.
 * <p>
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

	/**
	 * Absolute path of the java executable we are running on, so we don't depend on PATH.
	 */
	private static final String JAVA_EXECUTABLE = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

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
			
			while(true) {
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
	
	/**
	 * The parameter for this function should be the name of a directory that
	 * contains a servent_list.properties file which will describe our distributed system.
	 */
	private static void startServentTest(String testName) {
		List<Process> serventProcesses = new ArrayList<>();
		
		AppConfig.readConfig(testName+"/servent_list.properties", 0);
		
		AppConfig.timestampedStandardPrint("Starting multiple servent runner. "
				+ "If servents do not finish on their own, type \"stop\" to finish them");
		
		Process bsProcess;
		ProcessBuilder bsBuilder = new ProcessBuilder(JAVA_EXECUTABLE, "-cp", System.getProperty("java.class.path"), "app.BootstrapServer", String.valueOf(AppConfig.BOOTSTRAP_PORT));
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
		
		for(int i = 0; i < serventCount; i++) {
			try {
				ProcessBuilder builder = new ProcessBuilder(JAVA_EXECUTABLE, "-cp", System.getProperty("java.class.path"), "app.ServentMain",
						testName+"/servent_list.properties", String.valueOf(i));
				
				//We use files to read and write.
				//System.out, System.err and System.in will point to these files.
				builder.redirectOutput(new File(testName+"/output/servent" + i + "_out.txt"));
				builder.redirectError(new File(testName+"/error/servent" + i + "_err.txt"));
				builder.redirectInput(new File(testName+"/input/servent" + i + "_in.txt"));
				
				//Starts the servent as a completely separate process.
				Process p = builder.start();
				serventProcesses.add(p);
				
			} catch (IOException e) {
				AppConfig.timestampedErrorPrint("Couldn't start servent " + i, e);
			}
			try { //give each node 10s to start up
				Thread.sleep(10000);
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
		//the test directory can be given as the first argument, "chord" is the default
		startServentTest(args.length > 0 ? args[0] : "chord");
	}

}
