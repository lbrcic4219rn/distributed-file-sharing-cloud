# Distributed File Sharing Cloud: protocol and design

This document describes everything needed to write a new servent that can take part in the system:
the architecture, the transport, every message with its fields, the order in which messages are
exchanged, and the timing rules. The last sections map the protocol to the source code and list the
known limits.

## 1. Overview

The system consists of one bootstrap server and any number of nodes (servents). The bootstrap server
is not a node. It is used only to give a new node the address of one node that is already in the
system.

The nodes organise themselves into a Chord ring.

- Every node has a chord id: `SHA-256("ip:port") mod chord_size`. The digest is read as a signed big
  endian integer and the result of `mod` is never negative.
- A node knows its predecessor, its successor table (the finger table, with `log2(chord_size)`
  entries) and the list of the other nodes.
- Entry `i` of the successor table is the first node at or after `my id + 2^i`.
- Key `k` belongs to the first node at or after `k` on the ring. A node owns the keys in the range
  `(predecessor id, my id]`.

A message for one specific node is forwarded through the successor tables. Every hop at least halves
the remaining distance, so it arrives in at most `log2(chord_size)` hops. The unit test
`ChordStateTest` checks this bound for every pair of nodes on a ring of 16 nodes.

## 2. The virtual file system

The system stores ASCII text files in one virtual directory tree. Each node holds three things.

| Part        | Where it lives                  | Content                                                                                                                         |
|-------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| Index       | On every node, identical        | For each path: file or directory, the list of entries directly below it, and the chord id of the owner. Never the file content. |
| Owned files | On the owner only               | The content of the files the node added or took over.                                                                           |
| Backups     | On the two buddies of the owner | A full copy of the owned files of the node before it and the node after it on the ring.                                         |

The buddies of a node are its predecessor and its successor. Every file therefore exists on up to
three nodes, which is what lets the system survive two nodes failing at the same moment.

The index is changed only while holding the distributed mutex (section 8), so all nodes apply the
same changes in the same order.

Directories exist only in the index. When a file is added, entries for all of its parent
directories are created or updated on every node, so that operations on a directory can find
everything below it.

The virtual file system exists as long as at least one node is running. When the last node stops,
it is gone.

## 3. Configuration

Each node reads `servent_list.properties` when it starts.

| Property                | Meaning                                                       |
|-------------------------|---------------------------------------------------------------|
| `work_dirN`             | Working root of node N. Paths in commands are relative to it. |
| `serventN.port`         | Port node N listens on. Must be between 1000 and 2000.        |
| `servent.ip_address`    | Address the nodes use for themselves.                         |
| `bs_address`, `bs_port` | Address and port of the bootstrap server.                     |
| `weak_timeout`          | Weak failure limit in ms (section 9).                         |
| `strong_timeout`        | Strong failure limit in ms (section 9).                       |
| `chord_size`            | Number of positions on the ring. Must be a power of 2.        |
| `servent_count`         | Number of nodes the starter launches.                         |

Two more properties are read only by `MultipleServentStarter` and are meant for testing:
`start_gap` (ms to wait before starting the next node, 10000 by default) and `serventN.kill_after`
(kill node N abruptly that many ms after it started).

All nodes are assumed to have matching configuration files.

## 4. Commands

| Command          | Effect                                                                                  |
|------------------|-----------------------------------------------------------------------------------------|
| `add path`       | Adds a file, or a directory with everything in it, from the working root to the system. |
| `pull path`      | Fetches a file, or every file under a directory, into the working root.                 |
| `remove path`    | Removes a file, or a directory with everything in it, from the system.                  |
| `stop`           | Leaves the system in an orderly way and ends the program.                               |
| `info`           | Prints the neighbours, the index, the owned files and the backups of this node.         |
| `successor_info` | Prints the successor table.                                                             |
| `pause ms`       | Waits. Useful in scripted runs.                                                         |

File names never contain spaces.

## 5. Transport

### 5.1 Node to node

One TCP connection carries one message. The sender connects, writes one object with Java
serialization, and closes the connection. Nothing is sent back on the same connection.

Before every send the sender waits a random 500 to 1500 ms. This simulates a real network when
everything runs on one machine, and it means channels are not FIFO: two messages sent in a row may
arrive in the opposite order.

Anything that arrives and is not a message of this protocol (it cannot be deserialized, its type is
unknown, or its type and class do not match) is reported on the error output and discarded. The
node keeps running.

### 5.2 Common fields

Every message has these fields, in this order.

| Field               | Type          | Meaning                                                                                         |
|---------------------|---------------|-------------------------------------------------------------------------------------------------|
| `messageType`       | `MessageType` | The kind of message.                                                                            |
| `senderIpAddress`   | `String`      | Address of the node that created the message. It does not change when the message is forwarded. |
| `senderPort`        | `int`         | Port of the node that created the message.                                                      |
| `receiverIpAddress` | `String`      | Address of the node this copy is being sent to. Changes on every hop.                           |
| `receiverPort`      | `int`         | Port of the node this copy is being sent to.                                                    |
| `messageText`       | `String`      | Free text. Used only by `UPDATE`.                                                               |
| `messageId`         | `int`         | Sequence number of the message on the node that created it.                                     |

Two messages are equal when they have the same `messageId` and `senderPort`.

### 5.3 Three ways to send

| Way             | Used for                                                 | Rule                                                                                                                                                                                                                                                                  |
|-----------------|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Direct          | A neighbour, or any node while recovering from a failure | Sent straight to a known address.                                                                                                                                                                                                                                     |
| Routed          | One specific node                                        | The message carries `targetChordId`. A node that receives it handles it if the target is its own id. Otherwise it forwards it to the entry of its successor table that is closest to the target without passing it. If no node has that id, the message is discarded. |
| Around the ring | Every node                                               | The message carries `originChordId`. Each node applies it and passes it to its successor, until it comes back to the node that created it.                                                                                                                            |

Rules for messages that go around the ring:

- If the successor cannot be reached, the send is repeated every 1000 ms for up to three times
  `strong_timeout`. By then a dead successor has been removed and the message goes to the new one.
- A message whose origin is no longer in the system is discarded, so it cannot circle forever.
- The origin waits for the message to come back for at most `(n + 2) * 1500 + 5000` ms, where `n`
  is the number of other nodes. If it does not come back in time the origin reports it and carries
  on. The message may still complete later, and applying it twice is harmless.

## 6. Bootstrap server

A text protocol over TCP, one connection per request. The bootstrap server only keeps a list of
active nodes. It knows nothing about the shape of the system.

| Request               | Meaning                                                               | Answer                                                                             |
|-----------------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `Hail\n<ip>:<port>\n` | A new node asks for a contact.                                        | `-1\n` if it is the first node, otherwise `<ip>:<port>\n` of a random active node. |
| `New\n<ip>:<port>\n`  | A node finished joining. It is added to the list.                     | None.                                                                              |
| `Bye\n<ip>:<port>\n`  | A node is leaving, or was declared dead. It is removed from the list. | None.                                                                              |

A node talks to the bootstrap server twice when it joins (`Hail`, then `New`) and once when it
leaves (`Bye`). The node that declares another node dead sends one `Bye` for it, so that new nodes
are not sent to a dead contact.

## 7. Messages

Extra fields are listed in the order they appear in the class, after the common fields.

Helper types:

- `ServentInfo`: `ipAddress: String`, `listenerPort: int`, `chordId: int`
- `FileInfo`: `path: String`, `content: String`, `isDirectory: boolean`, `subFiles: List<String>`,
  `ogNode: int` (chord id of the owner)

### 7.1 Joining and leaving

| Type       | Extra fields                                                                                                            | Sent                                                   | Meaning                                                                                                                       |
|------------|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `NEW_NODE` | none                                                                                                                    | Routed by key, where the key is the id of the new node | The sender wants to join. Forwarded until it reaches the node that owns the key, which becomes the successor of the new node. |
| `SORRY`    | none                                                                                                                    | Direct                                                 | The id of the new node is already taken. The new node shuts down.                                                             |
| `WELCOME`  | `storageMap: Map<String, FileInfo>` (the whole index)                                                                   | Direct, from the successor to the new node             | The new node may join.                                                                                                        |
| `UPDATE`   | `messageText`: `ip:port,ip:port,...`                                                                                    | Around the ring, created by the new node               | Each node adds the new node to its view and appends its own address to the text.                                              |
| `JOINED`   | none                                                                                                                    | Direct, from the new node to its successor             | Joining is finished. The successor unlocks the mutex.                                                                         |
| `LEAVE`    | `originChordId: int`, `leavingNode: ServentInfo`, `newOwnerChordId: int`, `files: Map<String, FileInfo>` (with content) | Around the ring                                        | A node is leaving. Everyone removes it and moves its entries in the index to the new owner, who stores the files.             |

### 7.2 Mutex

| Type    | Extra fields                             | Sent                     | Meaning          |
|---------|------------------------------------------|--------------------------|------------------|
| `TOKEN` | `generation: int`, `creatorChordId: int` | Direct, to the successor | The mutex token. |

### 7.3 File system

| Type          | Extra fields                                                                        | Sent               | Meaning                                                                                        |
|---------------|-------------------------------------------------------------------------------------|--------------------|------------------------------------------------------------------------------------------------|
| `ADD_INFORM`  | `originChordId: int`, `entries: List<FileInfo>` (without content)                   | Around the ring    | New index entries. A file that already exists is left alone. Directories are merged.           |
| `REMOVE`      | `originChordId: int`, `paths: List<String>`                                         | Around the ring    | Paths to delete from the index. The owner also deletes the content.                            |
| `ASK_PULL`    | `targetChordId: int` (the owner), `requesterChordId: int`, `path: String`           | Routed             | Asks the owner for a file.                                                                     |
| `TELL_PULL`   | `targetChordId: int` (the requester), `path: String`, `fileInfo: FileInfo` or null  | Routed             | The file with its content, or null if the owner does not have it.                              |
| `BACKUP_SYNC` | `ownerChordId: int`, `version: long`, `files: Map<String, FileInfo>` (with content) | Direct, to a buddy | A full copy of the files of the sender. The receiver keeps only the highest version per owner. |

### 7.4 Failures

| Type            | Extra fields                                                         | Sent                            | Meaning                                                                           |
|-----------------|----------------------------------------------------------------------|---------------------------------|-----------------------------------------------------------------------------------|
| `PING`          | none                                                                 | Direct                          | Are you alive?                                                                    |
| `PONG`          | none                                                                 | Direct                          | The answer to `PING`.                                                             |
| `SUSPECT_ASK`   | `suspect: ServentInfo`                                               | Direct                          | The sender suspects this node. Please check it too.                               |
| `SUSPECT_REPLY` | `suspect: ServentInfo`, `alive: boolean`                             | Direct                          | Whether the suspect answered the ping of the helper.                              |
| `NODE_FAILED`   | `failedNode: ServentInfo`                                            | Direct, to every remaining node | The node is dead. Remove it.                                                      |
| `TAKEOVER`      | `originChordId: int`, `oldOwnerChordId: int`, `newOwnerChordId: int` | Around the ring                 | Everything the index says belonged to the old owner now belongs to the new owner. |

## 8. Distributed mutex

There is one token. It travels around the ring from successor to successor, and only the node
holding it may enter the critical section.

- `lock` waits until the token arrives and keeps it.
- `unlock` sends the token to the successor.
- A node that is not waiting passes the token on as soon as it gets it.
- A node that is alone keeps the token.
- If the successor cannot be reached, the send is repeated every 500 ms, each time to the current
  successor. A token is therefore never lost in transit.

The critical sections are: admitting a new node, `add`, `remove`, leaving, and taking over the files
of a dead node.

### Lost token

A token can be lost only when the node holding it fails. To recover:

1. The token is identified by the pair `(generation, creatorChordId)`. Pairs are compared by
   generation first and creator id second.
2. Every node remembers the highest pair it has seen and discards any token with a lower one.
3. The node that declared a failure watches for the token. If it does not see it within
   `2 * lap + broadcast timeout`, where `lap` is `(n + 2) * 1500` ms, it creates a new token with
   the generation increased by one and itself as the creator.

## 9. Failure detection

Every node watches its successor, so every node is watched by its predecessor. Detection has two
phases.

### Phase 1: weak limit

1. The watcher sends `PING` to its successor every `weak_timeout / 4` ms, but not more often than
   every 250 ms.
2. If no `PONG` has arrived for longer than `weak_timeout`, the successor is marked as suspicious.
   Nothing is removed yet.
3. The watcher sends `SUSPECT_ASK` to the node after the suspect. That helper pings the suspect,
   waits `weak_timeout`, and answers with `SUSPECT_REPLY`.
4. If the helper says the suspect is alive, or a `PONG` arrives in the meantime, the suspicion is
   dropped.

If there is no other node to ask, the word of the watcher is final.

### Phase 2: strong limit

The suspect is declared dead when it has been silent for longer than `strong_timeout` and either:

- the helper confirmed that it does not respond, or
- the helper did not answer at all and the silence has lasted `strong_timeout + weak_timeout`. This
  covers the case where the helper is dead too.

The watcher then:

1. removes the dead node from its own view,
2. sends `NODE_FAILED` directly to every remaining node, and they remove it too,
3. sends `Bye` for the dead node to the bootstrap server,
4. starts watching for the token (section 8).

Every node that removed the dead node recalculates its predecessor and successor table and sends
`BACKUP_SYNC` to its new buddies.

### Why the weak limit is 4000 ms in the examples

Every message is delayed by up to 1500 ms, so the answer to a ping can take 3000 ms. With a weak
limit below that, healthy nodes would be marked suspicious all the time. They would never be
removed, because the strong limit is not reached, but the logs and the network would be full of
needless checks.

## 10. Flows

### 10.1 Joining

1. The new node N sends `Hail` to the bootstrap server. If the answer is `-1`, N is the first node:
   it creates the token and is done.
2. N sends `NEW_NODE` to the contact it was given. The message is routed to the node S that owns the
   id of N.
3. S checks that the id is free (otherwise `SORRY`) and locks the mutex.
4. Holding the mutex, S checks again that it still owns the id, because the ring may have changed
   while it waited for the token. If it does not, it unlocks and routes `NEW_NODE` again.
5. S sets N as its predecessor and sends `WELCOME` with the index.
6. N sets S as its successor, stores the index, and sends `UPDATE` around the ring.
7. Each node that gets `UPDATE` adds N, recalculates its view, and sends `BACKUP_SYNC` to its new
   buddies.
8. When `UPDATE` returns to N, its text holds the addresses of all nodes. N builds its view, sends
   `New` to the bootstrap server, sends `BACKUP_SYNC`, and sends `JOINED` to S.
9. S unlocks the mutex.

Until step 8 is done, N holds back any `NEW_NODE`, `ASK_PULL` and `TELL_PULL` it receives. Several
nodes may join at the same time: the mutex admits them one by one.

### 10.2 add

1. The node reads the file, or the whole directory, from its working root.
2. It locks the mutex.
3. A file whose path is already in the index is reported as an error and skipped.
4. The content stays on the node. The entries, without content, go into its index together with
   the parent directories.
5. It sends `ADD_INFORM` around the ring and waits for it to come back.
6. It sends `BACKUP_SYNC` to its buddies and unlocks.

### 10.3 pull

1. If the path is not in the index, an error is reported. For a directory, all files below it are
   taken.
2. For each file owned by another node, the requester routes `ASK_PULL` to the owner. A file that
   is already on the requester is only reported.
3. The owner routes `TELL_PULL` back to the requester.
4. The requester writes the file into its working root, creating directories as needed, and prints
   it.

If no answer arrives within the broadcast timeout, the pull of that file is reported as timed out
and the command can be repeated. Pulling does not change who owns the file.

### 10.4 remove

Any node may remove any path.

1. The node locks the mutex. If the path is not in the index, an error is reported.
2. It collects the path and, for a directory, everything below it, and removes those entries
   locally.
3. It sends `REMOVE` around the ring and waits for it to come back.
4. Each owner of a removed file deletes the content and sends `BACKUP_SYNC`, so the buddies drop
   their copies.
5. It unlocks.

### 10.5 stop

1. The leaving node L locks the mutex.
2. It sends `LEAVE` around the ring with its files and its successor as the new owner, and waits
   for it to come back. The last node before L sends the message to L, not to its own new
   successor.
3. L sends `Bye` to the bootstrap server.
4. L hands the token directly to its successor and waits until it is delivered.
5. L shuts down.

If L is the only node, it just sends `Bye` and shuts down.

### 10.6 Taking over the files of a dead node

Each node that holds a backup of the dead node X does the following.

1. It locks the mutex.
2. If the index still has entries owned by X, the files from the backup become its own, it updates
   its index, and it sends `TAKEOVER` around the ring.
3. It sends `BACKUP_SYNC` to its buddies and unlocks.

Both buddies of X try this. Because it happens under the mutex, the second one finds that X no
longer owns anything and simply drops its backup.

## 11. Worked example

Five nodes with ids 17, 18, 27, 29 and 46. Node 18 added a directory and node 27 added a file. Both
are killed at the same moment. This is the `crash` scenario.

1. Node 17 watches 18. After 4 s of silence it marks 18 as suspicious and asks 27 to check. Node 27
   is dead, so no answer comes.
2. After 14 s of silence (`strong_timeout + weak_timeout`), node 17 declares 18 dead and tells 29
   and 46.
3. The successor of 17 is now 27. After 4 s node 17 marks it as suspicious and asks 29, which
   answers that 27 does not respond.
4. After 10 s of silence node 17 declares 27 dead. The ring is now 17, 29, 46.
5. Node 17 has the backup of 18 and node 29 has the backup of 27. Each waits for the token.
6. If the token died with one of the two nodes, node 17 creates a new one.
7. Node 17 takes over the directory, node 29 takes over the file, and both send `TAKEOVER`.
8. Other nodes can pull the directory and the file again, and new files can be added.

## 12. Timing summary

| What                                               | Value                                                                                     |
|----------------------------------------------------|-------------------------------------------------------------------------------------------|
| Delay before every send                            | Random, 500 to 1500 ms                                                                    |
| Ping interval                                      | `weak_timeout / 4`, at least 250 ms                                                       |
| Suspicious after                                   | `weak_timeout` without a `PONG`                                                           |
| Helper waits for the suspect                       | `weak_timeout`                                                                            |
| Dead after                                         | `strong_timeout` with confirmation, or `strong_timeout + weak_timeout` without any answer |
| Lap estimate                                       | `(n + 2) * 1500` ms, `n` being the number of other nodes                                  |
| Wait for a message around the ring, and for a pull | Lap estimate plus 5000 ms                                                                 |
| Retry to an unreachable successor                  | Every 1000 ms, for up to `3 * strong_timeout`                                             |
| Retry of the token                                 | Every 500 ms, until delivered                                                             |
| New token after a failure                          | If none is seen for two lap estimates plus the wait above                                 |

## 13. Where things are in the code

| Topic                               | Class                                                                        |
|-------------------------------------|------------------------------------------------------------------------------|
| Ring view and routing               | `app.ChordState`                                                             |
| Index, owned files, backups         | `app.file_util.Storage`                                                      |
| Reading files from the working root | `app.file_util.FileUtils`                                                    |
| Pull                                | `app.file_util.PullManager`                                                  |
| Sending and receiving               | `servent.base_message.util.MessageUtil`, `DelayedMessageSender`              |
| Messages around the ring            | `servent.base_message.util.RingBroadcaster`                                  |
| Mutex                               | `mutex.TokenMutex`                                                           |
| Failure detection                   | `servent.failure.FailureDetector`                                            |
| Takeover and backups                | `servent.failure.FailureRecovery`, `servent.failure.BackupUtil`              |
| Message classes                     | `servent.base_message`, `servent.storage_message`, `servent.failure_message` |
| Handlers, one per message type      | `servent.base_handler`, `servent.storage_handler`, `servent.failure_handler` |
| Commands                            | `cli.storage_command`, `cli.basic_command`                                   |
| Bootstrap server                    | `app.BootstrapServer`, `app.BootstrapClient`                                 |
| Starting several nodes              | `app.MultipleServentStarter`                                                 |

## 14. Known limits

- The token travels the ring one node at a time. The logarithmic number of hops holds for routed
  messages (joining and pull), not for the token or for messages that must reach every node.
- If two nodes create a new token at the same instant, two tokens exist for a short time, until the
  one with the higher pair has gone around the ring.
- If a node fails in the middle of its own joining, the mutex stays locked at its successor.
- If the owner of a file and both of its buddies fail before new backups are made, the file is
  lost. Its entry stays in the index and a pull of it times out.
- A node that was wrongly declared dead is not let back in. It reports this and has to be
  restarted.

## 15. How to show that it works

```bash
./gradlew build
```

Compiles the project and runs the unit tests.

```bash
./gradlew installDist
```

```bash
scripts/run_scenario.sh basic 200
```

Add, pull and remove of files and directories, an add of an existing name being refused, and every
node leaving in turn.

```bash
scripts/run_scenario.sh crash 225
```

Two neighbouring nodes failing at the same moment, the backups being taken over, and work
continuing.

```bash
scripts/check_scenario.sh crash <directory printed by the run>
```

Checks the logs of a finished run for the lines that prove each feature worked.

The scenarios are in `scenarios/<name>/`: the configuration, one input script per node, and the
working roots. The output of each node is written to `output/` and `error/` in the directory the
run prints.
