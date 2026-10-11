package cli.storage_command;

import app.AppConfig;
import app.file_util.PullManager;
import cli.basic_command.CLICommand;

import java.io.File;

public class StoragePullCommand implements CLICommand {

    @Override
    public String commandName() {
        return "pull";
    }

    @Override
    public void execute(String args) {

        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for pull command. Should be pull path.");
            return;
        }

        String path = args.split(" ")[0].replace('/', File.separatorChar).replace('\\', File.separatorChar);

        PullManager.pull(path);
    }

}
