package cli;

import app.AppConfig;
import app.Cancellable;
import cli.basic_command.*;
import cli.storage_command.StorageAddCommand;
import cli.storage_command.StoragePullCommand;
import cli.storage_command.StorageRemoveCommand;
import servent.SimpleServentListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * A simple CLI parser. Each command has a name and arbitrary arguments.
 * Currently supported commands:
 *
 * <ul>
 * <li><code>pause [ms]</code> - pauses exection given number of ms - useful when scripting</li>
 * </ul>
 *
 * @author bmilojkovic
 */
public class CLIParser implements Runnable, Cancellable {

    private final List<CLICommand> commandList;
    private volatile boolean working = true;

    public CLIParser(SimpleServentListener listener, Cancellable failureDetector) {
        this.commandList = new ArrayList<>();

        commandList.add(new InfoCommand());
        commandList.add(new PauseCommand());
        commandList.add(new SuccessorInfo());
        commandList.add(new StopCommand(this, listener, failureDetector));

        commandList.add(new StorageAddCommand());
        commandList.add(new StoragePullCommand());
        commandList.add(new StorageRemoveCommand());
    }

    @Override
    public void run() {
        Scanner sc = new Scanner(System.in);

        while (working) {
            if (!sc.hasNextLine()) {
                break;
            }
            String commandLine = sc.nextLine();

            int spacePos = commandLine.indexOf(" ");

            String commandName;
            String commandArgs = null;
            if (spacePos != -1) {
                commandName = commandLine.substring(0, spacePos);
                commandArgs = commandLine.substring(spacePos + 1);
            } else {
                commandName = commandLine;
            }

            boolean found = false;

            for (CLICommand cliCommand : commandList) {
                if (cliCommand.commandName().equals(commandName)) {
                    cliCommand.execute(commandArgs);
                    found = true;
                    break;
                }
            }

            if (!found) {
                AppConfig.timestampedErrorPrint("Unknown command: " + commandName);
            }
        }

        sc.close();
    }

    @Override
    public void stop() {
        this.working = false;

    }
}
