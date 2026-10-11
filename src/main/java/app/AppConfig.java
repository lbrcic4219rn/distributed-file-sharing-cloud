package app;

import app.file_util.Storage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * This class contains all the global application configuration stuff.
 *
 * @author bmilojkovic
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppConfig {

    private static final Properties properties = new Properties();
    /**
     * Convenience access for this servent's information
     */
    public static ServentInfo myServentInfo;
    public static int BOOTSTRAP_PORT;
    public static String BOOTSTRAP_ADDRESS;
    public static int SERVENT_COUNT;
    public static ChordState chordState;
    public static Storage storage = new Storage();
    public static String ROOT_DIR;
    public static int WEAK_TIMEOUT;
    public static int STRONG_TIMEOUT;

    /**
     * Print a message to stdout with a timestamp
     *
     * @param message message to print
     */
    public static void timestampedStandardPrint(String message) {
        log.info(message);
    }

    /**
     * Print a message to stderr with a timestamp
     *
     * @param message message to print
     */
    public static void timestampedErrorPrint(String message) {
        log.error(message);
    }

    public static void timestampedErrorPrint(String message, Throwable cause) {
        log.error(message, cause);
    }

    public static int intProperty(String name, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(name, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading " + name + ", using " + defaultValue);
            return defaultValue;
        }
    }

    public static void readConfig(String configName, int serventId) {
        try (FileInputStream configStream = new FileInputStream(configName)) {
            properties.load(configStream);
        } catch (IOException e) {
            timestampedErrorPrint("Couldn't open properties file. Exiting...");
            System.exit(0);
        }


        BOOTSTRAP_ADDRESS = properties.getProperty("bs_address");
        if (BOOTSTRAP_ADDRESS == null) {
            timestampedErrorPrint("Problem reading bs_address. Exiting...");
            System.exit(0);
        }

        try {
            BOOTSTRAP_PORT = Integer.parseInt(properties.getProperty("bs_port"));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading bs_port. Exiting...");
            System.exit(0);
        }

        try {
            SERVENT_COUNT = Integer.parseInt(properties.getProperty("servent_count"));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading servent_count. Exiting...");
            System.exit(0);
        }

        ROOT_DIR = properties.getProperty("work_dir" + serventId);
        if (ROOT_DIR != null) {
            ROOT_DIR = new File(ROOT_DIR.replace('\\', '/')).getPath();
        }
        if (ROOT_DIR == null) {
            timestampedErrorPrint("Problem reading work_directory property. Exiting...");
            System.exit(0);
        }

        try {
            WEAK_TIMEOUT = Integer.parseInt(properties.getProperty("weak_timeout"));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading weak_timeout property. Exiting...");
            System.exit(0);
        }

        try {
            STRONG_TIMEOUT = Integer.parseInt(properties.getProperty("strong_timeout"));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading strong_timeout property. Exiting...");
            System.exit(0);
        }

        try {
            SERVENT_COUNT = Integer.parseInt(properties.getProperty("servent_count"));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading servent_count. Exiting...");
            System.exit(0);
        }

        try {
            ChordState.CHORD_SIZE = Integer.parseInt(properties.getProperty("chord_size"));
            chordState = new ChordState();
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading chord_size. Must be a number that is a power of 2. Exiting...");
            System.exit(0);
        }

        String ipAddressProperty = "servent.ip_address";
        String ipAddress = properties.getProperty(ipAddressProperty);
        if (ipAddress == null) {
            timestampedErrorPrint("Problem reading ip_address property. Exiting...");
            System.exit(0);
        }

        String portProperty = "servent" + serventId + ".port";
        int serventPort = -1;
        try {
            serventPort = Integer.parseInt(properties.getProperty(portProperty));
        } catch (NumberFormatException e) {
            timestampedErrorPrint("Problem reading " + portProperty + ". Exiting...");
            System.exit(0);
        }

        myServentInfo = new ServentInfo(ipAddress, serventPort);
    }
}
