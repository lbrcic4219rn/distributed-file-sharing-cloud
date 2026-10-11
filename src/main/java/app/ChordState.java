package app;

import lombok.Getter;

import servent.base_message.WelcomeMessage;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChordState {

    public static int CHORD_SIZE;
    @Getter
    private final ServentInfo[] successorTable;
    private final List<ServentInfo> allNodeInfo;
    private final CountDownLatch joined = new CountDownLatch(1);
    private int chordLevel; //log_2(CHORD_SIZE)
    private ServentInfo predecessorInfo;

    public ChordState() {
        this.chordLevel = 1;
        int tmp = CHORD_SIZE;
        while (tmp != 2) {
            if (tmp % 2 != 0) { //not a power of 2
                throw new NumberFormatException();
            }
            tmp /= 2;
            this.chordLevel++;
        }

        successorTable = new ServentInfo[chordLevel];
        predecessorInfo = null;
        allNodeInfo = new ArrayList<>();
    }

    public static int chordHash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(value.getBytes());
            BigInteger hash = new BigInteger(messageDigest.digest());
            hash = hash.mod(BigInteger.valueOf(CHORD_SIZE));
            return hash.intValue();
        } catch (NoSuchAlgorithmException e) {
            AppConfig.timestampedErrorPrint("SHA-256 is not available", e);
            return -1;
        }
    }

    public static int distance(int from, int to) {
        return Math.floorMod(to - from, CHORD_SIZE);
    }

    public synchronized void init(WelcomeMessage welcomeMsg) {
        //set a temporary pointer to next node, for sending of update message
        successorTable[0] = new ServentInfo(welcomeMsg.getSenderIpAddress(), welcomeMsg.getSenderPort());
        AppConfig.storage.replaceIndex(welcomeMsg.getStorageMap());

        AppConfig.timestampedStandardPrint("# MY PREDECESSOR IS " + predecessorInfo);
        AppConfig.timestampedStandardPrint("# MY SUCCESSORS ARE " + Arrays.toString(successorTable));
    }

    public void markJoined() {
        joined.countDown();
    }

    public void awaitJoined() {
        try {
            joined.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns true if we are the owner of the specified key.
     */
    public synchronized boolean isKeyMine(int key) {
        if (predecessorInfo == null) {
            return true;
        }
        int predecessorChordId = predecessorInfo.getChordId();
        int myChordId = AppConfig.myServentInfo.getChordId();
        if (predecessorChordId < myChordId) { //no overflow
            return key <= myChordId && key > predecessorChordId;
        }
        //overflow
        return key <= myChordId || key > predecessorChordId;
    }

    /**
     * Main chord operation - find the nearest node to hop to to find a specific key.
     */
    public synchronized ServentInfo getNextNodeForKey(int key) {
        if (isKeyMine(key)) {
            return AppConfig.myServentInfo;
        }

        int myId = AppConfig.myServentInfo.getChordId();
        int keyDistance = distance(myId, key);

        ServentInfo nextNode = successorTable[0];
        int nextNodeDistance = -1;
        for (ServentInfo successor : successorTable) {
            if (successor == null) {
                continue;
            }
            int successorDistance = distance(myId, successor.getChordId());
            if (successorDistance <= keyDistance && successorDistance > nextNodeDistance) {
                nextNode = successor;
                nextNodeDistance = successorDistance;
            }
        }
        return nextNode;
    }

    private void updateSuccessorTable() {
        Arrays.fill(successorTable, null);
        if (allNodeInfo.isEmpty()) {
            return;
        }

        //first node after me has to be successorTable[0]

        int currentNodeIndex = 0;
        ServentInfo currentNode = allNodeInfo.get(currentNodeIndex);
        successorTable[0] = currentNode;

        int currentIncrement = 2;

        ServentInfo previousNode = AppConfig.myServentInfo;

        //i is successorTable index
        for (int i = 1; i < chordLevel; i++, currentIncrement *= 2) {
            //we are looking for the node that has larger chordId than this
            int currentValue = (AppConfig.myServentInfo.getChordId() + currentIncrement) % CHORD_SIZE;

            int currentId = currentNode.getChordId();
            int previousId = previousNode.getChordId();

            //this loop needs to skip all nodes that have smaller chordId than currentValue
            while (shouldSkipNode(currentValue, currentNodeIndex, previousId)) {
                //try same value with the next node
                previousId = currentId;
                currentNodeIndex = (currentNodeIndex + 1) % allNodeInfo.size();
                currentNode = allNodeInfo.get(currentNodeIndex);
                currentId = currentNode.getChordId();
            }
            successorTable[i] = currentNode;
        }
    }

    private boolean shouldSkipNode(int value, int nodeIndex, int previousId) {
        int nodeId = allNodeInfo.get(nodeIndex).getChordId();
        if (value > nodeId) {
            //before skipping, check for overflow
            return nodeId > previousId || value < previousId;
        }

        int nextNodeId = allNodeInfo.get((nodeIndex + 1) % allNodeInfo.size()).getChordId();
        return nextNodeId < nodeId && value <= nextNodeId;
    }

    private void restructure() {
        allNodeInfo.sort(Comparator.comparingInt(ServentInfo::getChordId));

        List<ServentInfo> newList = new ArrayList<>();
        List<ServentInfo> newList2 = new ArrayList<>();

        int myId = AppConfig.myServentInfo.getChordId();
        for (ServentInfo serventInfo : allNodeInfo) {
            if (serventInfo.getChordId() < myId) {
                newList2.add(serventInfo);
            } else {
                newList.add(serventInfo);
            }
        }

        allNodeInfo.clear();
        allNodeInfo.addAll(newList);
        allNodeInfo.addAll(newList2);

        predecessorInfo = allNodeInfo.isEmpty() ? null : allNodeInfo.getLast();

        updateSuccessorTable();
    }

    public synchronized void addNodes(List<ServentInfo> newNodes) {
        for (ServentInfo newNode : newNodes) {
            if (newNode.getChordId() != AppConfig.myServentInfo.getChordId() && !isMember(newNode.getChordId())) {
                allNodeInfo.add(newNode);
            }
        }
        restructure();
    }

    public synchronized boolean removeNode(int chordId) {
        boolean removed = allNodeInfo.removeIf(node -> node.getChordId() == chordId);
        if (removed) {
            restructure();
        }
        return removed;
    }

    public synchronized boolean isMember(int chordId) {
        return allNodeInfo.stream().anyMatch(node -> node.getChordId() == chordId);
    }

    public synchronized List<ServentInfo> getAllNodes() {
        return new ArrayList<>(allNodeInfo);
    }

    public synchronized ServentInfo getSuccessor() {
        return successorTable[0];
    }

    public synchronized ServentInfo getPredecessor() {
        return predecessorInfo;
    }

    public synchronized void setPredecessor(ServentInfo newNodeInfo) {
        this.predecessorInfo = newNodeInfo;
    }

    public synchronized int getNextNodePort() {
        return successorTable[0].getListenerPort();
    }

    public synchronized String getNextNodeIp() {
        return successorTable[0].getIpAddress();
    }

    public synchronized boolean isCollision(int chordId) {
        return chordId == AppConfig.myServentInfo.getChordId() || isMember(chordId);
    }

}
