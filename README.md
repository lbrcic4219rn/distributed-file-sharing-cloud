# Distributed File Sharing Cloud

A distributed system for sharing ASCII text files, built on a Chord ring.
The assignment is in `KiDS Projekat 2023.pdf`; the full protocol is documented in
`PROTOCOL.md`.

## What is implemented

| Requirement                                     | How                                                                                          |
|-------------------------------------------------|----------------------------------------------------------------------------------------------|
| `add`, `pull`, `remove` (files and directories) | Files stay on the node that added them; every node keeps an index of the virtual file system |
| `stop`                                          | Orderly leave: files are handed to the successor and the ring restructures                   |
| Architecture                                    | Chord; directed messages are routed through the successor table in O(log n) hops             |
| Failure detection                               | Two phases (weak / strong limit), confirmed by a second node                                 |
| Backup                                          | Every node's files are copied to its predecessor and successor, who take over if it dies     |
| Distributed mutex                               | Token ring, with the token regenerated if it is lost in a failure                            |

## Build and test

```bash
./gradlew build
```

This compiles the project and runs the unit tests (`src/test`).

## Run

```bash
./gradlew run
```

This starts the bootstrap server and the five servents described in `chord/servent_list.properties`,
feeding each one the commands in `chord/input/`. Output goes to `chord/output/` and `chord/error/`.
Type `stop` to end the run. Another configuration directory can be given with `--args="<dir>"`.

Commands a servent understands: `add <path>`, `pull <path>`, `remove <path>`, `stop`, `info`,
`successor_info`, `pause <ms>`. Paths are relative to the servent's working root.

## Scenarios

`scenarios/` holds scripted full system runs. They are run in a scratch copy, so logs and pulled
files stay out of the repository.

```bash
./gradlew installDist
```

```bash
scripts/run_scenario.sh basic 200
```

```bash
scripts/run_scenario.sh crash 225
```

Each run prints the directory with its logs. To check that every feature showed up in them:

```bash
scripts/check_scenario.sh basic <printed directory>
```

- **basic**: add a directory and a file, refuse a duplicate name, pull both from other nodes,
  remove the directory from a third node, then every node leaves in turn while the file follows the
  ring to the last one.
- **crash**: two neighboring nodes are killed at the same moment. The others detect it, restructure,
  take over the dead nodes' files from backups, create a new token, and keep working.

## Configuration

`servent_list.properties`: `work_dirN`, `serventN.port`, `servent.ip_address`, `bs_address`, `bs_port`,
`weak_timeout`, `strong_timeout`, `chord_size`, `servent_count`.
Every message is delayed by 0.5 to 1.5 s on purpose, so `weak_timeout` should be above 3000 ms.
