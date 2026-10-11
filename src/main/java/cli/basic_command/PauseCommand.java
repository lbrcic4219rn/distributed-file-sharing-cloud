package cli.basic_command;

import app.AppConfig;

public class PauseCommand implements CLICommand {

	@Override
	public String commandName() {
		return "pause";
	}

	@Override
	public void execute(String args) {
		try {
			int timeToSleep = Integer.parseInt(args);
			
			if (timeToSleep < 0) {
				throw new NumberFormatException();
			}

			AppConfig.timestampedStandardPrint("Pausing for " + timeToSleep + " ms");
			sleep(timeToSleep);
		} catch (NumberFormatException e) {
			AppConfig.timestampedErrorPrint("Pause command should have one int argument, which is time in ms.");
		}
	}

	private void sleep(int timeToSleep) {
		try {
			Thread.sleep(timeToSleep);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
