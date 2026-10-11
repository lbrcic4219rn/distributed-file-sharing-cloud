package cli.basic_command;

import app.AppConfig;

public class InfoCommand implements CLICommand {

    @Override
    public String commandName() {
        return "info";
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("My info: " + AppConfig.myServentInfo
                + "\npredecessor: " + AppConfig.chordState.getPredecessor()
                + "\nsuccessor: " + AppConfig.chordState.getSuccessor()
                + "\nother nodes: " + AppConfig.chordState.getAllNodes()
                + "\n" + AppConfig.storage);
    }

}
