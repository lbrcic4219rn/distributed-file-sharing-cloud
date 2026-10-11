package cli.storage_command;

import app.AppConfig;
import app.file_util.FileInfo;
import app.file_util.FileUtils;
import cli.basic_command.CLICommand;
import mutex.TokenMutex;
import servent.base_message.util.RingBroadcaster;
import servent.failure.BackupUtil;
import servent.storage_message.AddInformMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StorageAddCommand implements CLICommand {

    @Override
    public String commandName() {
        return "add";
    }

    @Override
    public void execute(String args) {

        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for add command. Should be add path.");
            return;
        }

        String path = args.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        List<FileInfo> found = new ArrayList<>();
        if (FileUtils.isPathFile(AppConfig.ROOT_DIR, path)) {
            FileInfo fileInfo = FileUtils.getFileInfoFromPath(AppConfig.ROOT_DIR, path);
            if (fileInfo != null) {
                found.add(fileInfo);
            }
        } else {
            found.addAll(FileUtils.getDirectoryInfoFromPath(AppConfig.ROOT_DIR, path));
        }
        if (found.isEmpty()) {
            return;
        }

        TokenMutex.lock();//LOCK 1a
        try {
            List<FileInfo> newEntries = new ArrayList<>();
            for (FileInfo fileInfo : found) {
                if (fileInfo.isDirectory()) {
                    AppConfig.storage.addEntry(fileInfo);
                    newEntries.add(fileInfo);
                } else if (AppConfig.storage.contains(fileInfo.getPath())) {
                    AppConfig.timestampedErrorPrint("Can't add " + fileInfo.getPath() + " - it already exists in the system");
                } else {
                    AppConfig.storage.addOwned(fileInfo);
                    newEntries.add(fileInfo.withoutContent());
                    AppConfig.timestampedStandardPrint("File " + fileInfo.getPath() + " stored successfully.");
                }
            }

            if (newEntries.stream().anyMatch(FileInfo::isFile)) {
                RingBroadcaster.broadcastAndWait(new AddInformMessage(AppConfig.myServentInfo.getChordId(), newEntries));
                BackupUtil.resync();
            }
        } finally {
            TokenMutex.unlock(); //UNLOCK 1a
        }
    }
}
