package app.file_util;

import app.AppConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import servent.base_message.util.MessageUtil;
import servent.base_message.util.RingBroadcaster;
import servent.storage_message.AskPullMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PullManager {

    private static final Timer timeoutTimer = new Timer("pull-timeout", true);

    private static final Map<String, Long> pending = new ConcurrentHashMap<>();

    public static void pull(String path) {
        if (!AppConfig.storage.contains(path)) {
            AppConfig.timestampedErrorPrint("Can't pull " + path + " - it doesn't exist in the system");
            return;
        }

        List<String> files = AppConfig.storage.collectFiles(path);
        if (files.isEmpty()) {
            AppConfig.timestampedErrorPrint("No files found to pull - " + path);
            return;
        }

        for (String file : files) {
            request(file);
        }
    }

    private static void request(String file) {
        int myId = AppConfig.myServentInfo.getChordId();
        FileInfo entry = AppConfig.storage.getEntry(file);
        if (entry == null) {
            return;
        }
        if (entry.getOgNode() == myId) {
            AppConfig.timestampedStandardPrint("File " + file + " is already on this node");
            return;
        }

        long askedAt = System.currentTimeMillis();
        pending.put(file, askedAt);
        MessageUtil.route(new AskPullMessage(entry.getOgNode(), myId, file));

        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (pending.remove(file, askedAt)) {
                    AppConfig.timestampedErrorPrint("Pull of " + file + " timed out, try again later");
                }
            }
        }, RingBroadcaster.broadcastTimeout());
    }

    public static void received(String path, FileInfo file) {
        pending.remove(path);

        if (file == null) {
            AppConfig.timestampedErrorPrint("Can't pull " + path + " - its owner doesn't have it");
            return;
        }

        Path target = Path.of(AppConfig.ROOT_DIR, path);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, file.getContent());
            AppConfig.timestampedStandardPrint("Pulled file " + path + " into " + target + "\n-----" + path + "-----\n" + file.getContent());
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't write pulled file " + target, e);
        }
    }
}
