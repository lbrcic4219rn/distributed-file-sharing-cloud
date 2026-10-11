package cli.basic_command;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import cli.CLIParser;
import servent.SimpleServentListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

@RequiredArgsConstructor
public class StopCommand implements CLICommand {

	private final CLIParser parser;
	private final SimpleServentListener listener;
	
	@Override
	public String commandName() {
		return "stop";
	}

	@Override
	public void execute(String args) {
		AppConfig.timestampedStandardPrint("Stopping...");
		parser.stop();
		listener.stop();

		String bsAddress = AppConfig.BOOTSTRAP_ADDRESS;
		int bsPort = AppConfig.BOOTSTRAP_PORT;

		try (Socket bsSocket = new Socket(bsAddress, bsPort)) {
			PrintWriter bsWriter = new PrintWriter(bsSocket.getOutputStream());
			bsWriter.write("Bye\n" + AppConfig.myServentInfo.getIpAddress() + ":" + AppConfig.myServentInfo.getListenerPort() + "\n");
			bsWriter.flush();
		} catch (IOException e) {
			AppConfig.timestampedErrorPrint("Couldn't say bye to the bootstrap server", e);
		}
	}

}
