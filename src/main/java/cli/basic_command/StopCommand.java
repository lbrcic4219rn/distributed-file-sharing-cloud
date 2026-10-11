package cli.basic_command;

import app.AppConfig;
import app.BootstrapClient;
import app.Cancellable;
import app.ServentInfo;
import cli.CLIParser;
import lombok.RequiredArgsConstructor;
import mutex.TokenMutex;
import servent.SimpleServentListener;
import servent.base_message.LeaveMessage;
import servent.base_message.util.RingBroadcaster;

@RequiredArgsConstructor
public class StopCommand implements CLICommand {

    private final CLIParser parser;
    private final SimpleServentListener listener;
    private final Cancellable failureDetector;

    @Override
    public String commandName() {
        return "stop";
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("Stopping...");

        if (AppConfig.chordState.getSuccessor() != null) {
            TokenMutex.lock();

            ServentInfo successor = AppConfig.chordState.getSuccessor();
            if (successor != null) {
                RingBroadcaster.broadcastAndWait(new LeaveMessage(AppConfig.myServentInfo.getChordId(), AppConfig.myServentInfo,
                        successor.getChordId(), AppConfig.storage.getOwnedSnapshot()));
                TokenMutex.handOver(successor);
            }
        }
        BootstrapClient.notify("Bye", AppConfig.myServentInfo);

        failureDetector.stop();
        parser.stop();
        listener.stop();
        AppConfig.timestampedStandardPrint("Stopped.");
    }

}
