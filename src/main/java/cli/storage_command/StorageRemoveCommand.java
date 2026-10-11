package cli.storage_command;

import app.AppConfig;
import cli.basic_command.CLICommand;
import mutex.TokenMutex;
import servent.base_message.util.RingBroadcaster;
import servent.failure.BackupUtil;
import servent.storage_message.RemoveMessage;

import java.io.File;
import java.util.List;

public class StorageRemoveCommand implements CLICommand {

    @Override
    public String commandName() {
        return "remove";
    }

    @Override
    public void execute(String args) {

        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for remove command. Should be remove path.");
            return;
        }

        String path = args.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        TokenMutex.lock();//LOCK 1r
        try {
            if (!AppConfig.storage.contains(path)) {
                AppConfig.timestampedErrorPrint("Can't remove " + path + " - it doesn't exist in the system");
                return;
            }

            List<String> paths = AppConfig.storage.collectPaths(path);
            boolean removedFromThisNode = AppConfig.storage.removeEntries(paths);
            AppConfig.timestampedStandardPrint("Removing " + paths + " from the system");

            RingBroadcaster.broadcastAndWait(new RemoveMessage(AppConfig.myServentInfo.getChordId(), paths));
            if (removedFromThisNode) {
                BackupUtil.resync();
            }
        } finally {
            TokenMutex.unlock();//UNLOCK 1r
        }
    }

}
