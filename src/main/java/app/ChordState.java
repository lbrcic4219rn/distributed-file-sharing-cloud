package app;

import lombok.Getter;
import lombok.Setter;

import app.file_util.FileInfo;
import servent.base_message.Message;
import servent.base_message.WelcomeMessage;
import servent.base_message.util.MessageUtil;
import servent.storage_message.AddInformMessage;
import servent.storage_message.AskPullMessage;
import servent.storage_message.RemoveMessage;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public class ChordState {

    public static int CHORD_SIZE;

    public static int chordHash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(value.getBytes());
            BigInteger hash = new BigInteger(messageDigest.digest());
            hash = hash.mod(BigInteger.valueOf(64));
            return hash.intValue();
        } catch (NoSuchAlgorithmException e) {
            AppConfig.timestampedErrorPrint("SHA-256 is not available", e);
            return -1;
        }
    }

    private int chordLevel; //log_2(CHORD_SIZE)
    @Getter
    private final ServentInfo[] successorTable;
    private ServentInfo predecessorInfo;
    private final List<ServentInfo> allNodeInfo;
    @Getter @Setter
    private Map<String, FileInfo> storageMap;
    @Getter @Setter
    public List<FileInfo> pulledFiles;
    public int amountToPull;
    public int amountPulled;

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
        for (int i = 0; i < chordLevel; i++) {
            successorTable[i] = null;
        }
        predecessorInfo = null;
        allNodeInfo = new ArrayList<>();
        storageMap = new ConcurrentHashMap<>();
        pulledFiles = new ArrayList<>();
        amountToPull = 0;
        amountPulled = 0;
    }

    public void addPulledFile(FileInfo fi){
        pulledFiles.add(fi);
        amountPulled++;
        if (amountPulled == amountToPull){
            printPulledFiles();
        }
    }


    public void init(WelcomeMessage welcomeMsg) {
        //set a temporary pointer to next node, for sending of update message
        successorTable[0] = new ServentInfo(welcomeMsg.getSenderIpAddress(), welcomeMsg.getSenderPort());
        this.storageMap = new ConcurrentHashMap<>(welcomeMsg.getStorageMap());

        AppConfig.timestampedStandardPrint("# MY PREDECESSOR IS " + predecessorInfo);
        AppConfig.timestampedStandardPrint("# MY SUCCESSORS ARE " + Arrays.toString(successorTable));

        //tell bootstrap this node is not a collider
        try (Socket bsSocket = new Socket("localhost", AppConfig.BOOTSTRAP_PORT)) {
            PrintWriter bsWriter = new PrintWriter(bsSocket.getOutputStream());
            bsWriter.write("New\n" + AppConfig.myServentInfo.getIpAddress() + ":" + AppConfig.myServentInfo.getListenerPort() + "\n");

            bsWriter.flush();
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't register with the bootstrap server", e);
        }
    }


    /**
     * Returns true if we are the owner of the specified key.
     */
    public boolean isKeyMine(int key) {
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
     * We have to take a value that is smaller than required to make sure we don't overshoot.
     * We can only be certain we have found the required node when it is our first next node.
     */
    public ServentInfo getNextNodeForKey(int key) {
        if (isKeyMine(key)) {
            return AppConfig.myServentInfo;
        }

        //normally we start the search from our first successor
        int startInd = 0;

        //if the key is smaller than us, and we are not the owner,
        //then all nodes up to CHORD_SIZE will never be the owner,
        //so we start the search from the first item in our table after CHORD_SIZE
        //we know that such a node must exist, because otherwise we would own this key
        if (key < AppConfig.myServentInfo.getChordId()) {
            int skip = 1;
            while (successorTable[skip].getChordId() > successorTable[startInd].getChordId()) {
                startInd++;
                skip++;
            }
        }

        int previousId = successorTable[startInd].getChordId();

        for (int i = startInd + 1; i < successorTable.length; i++) {
            if (successorTable[i] == null) {
                AppConfig.timestampedErrorPrint("Couldn't find successor for " + key);
                break;
            }

            int successorId = successorTable[i].getChordId();

            if (successorId >= key) {
                return successorTable[i - 1];
            }
            if (key > previousId && successorId < previousId) { //overflow
                return successorTable[i - 1];
            }
            previousId = successorId;
        }
        //if we have only one node in all slots in the table, we might get here
        //then we can return any item
        return successorTable[0];
    }

    private void updateSuccessorTable() {
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

    /**
     * Returns true if the node at <code>nodeIndex</code> can't be the successor for <code>value</code>,
     * taking overflow around the end of the chord into account.
     */
    private boolean shouldSkipNode(int value, int nodeIndex, int previousId) {
        int nodeId = allNodeInfo.get(nodeIndex).getChordId();
        if (value > nodeId) {
            //before skipping, check for overflow
            return nodeId > previousId || value < previousId;
        }
        //node id is larger, check for overflow
        int nextNodeId = allNodeInfo.get((nodeIndex + 1) % allNodeInfo.size()).getChordId();
        return nextNodeId < nodeId && value <= nextNodeId;
    }

    /**
     * This method constructs an ordered list of all nodes. They are ordered by chordId, starting from this node.
     * Once the list is created, we invoke <code>updateSuccessorTable()</code> to do the rest of the work.
     */
    public void addNodes(List<ServentInfo> newNodes) {
        allNodeInfo.addAll(newNodes);

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

        if (!newList2.isEmpty()) {
            predecessorInfo = newList2.getLast();
        } else {
            predecessorInfo = newList.getLast();
        }

        updateSuccessorTable();
    }

    public void addToStorage(FileInfo fileInfo, String requesterIp, int requesterPort) {
        if (!storageMap.containsKey(fileInfo.getPath())) { //Proverimo da li vec imamo fajl, ako nemamo ododaj podatke
            storageMap.put(fileInfo.getPath(), new FileInfo(fileInfo));
            AppConfig.timestampedStandardPrint("File " + fileInfo.getPath() + " stored successfully.");

            String nextNodeIp = AppConfig.chordState.getNextNodeIp();
            int nextNodePort = AppConfig.chordState.getNextNodePort();

            Message addInfoMsg = new AddInformMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                    nextNodeIp, nextNodePort, requesterIp, requesterPort, fileInfo);
            AppConfig.timestampedStandardPrint("Sending inform message " + addInfoMsg);
            MessageUtil.sendMessage(addInfoMsg);

            AppConfig.timestampedStandardPrint("# Nakon dodavanja u storage");
            for (Map.Entry<String, FileInfo> map: storageMap.entrySet()) {
                AppConfig.timestampedStandardPrint("storage = " + map.getKey() + " -- " + map.getValue() + " -- " + map.getValue().getOgNode());
            }
        }
        else {
            AppConfig.timestampedStandardPrint("We already have " + fileInfo.getPath());
        }
    }

    public void pullFile (String path){
        //1.1 pokupimo iz mape path i id od noda koji ga drzi
        //1.2 posaljemo svima pull msg da bi nasli onoga koji nam treba
        //2 cekamo tell msg od njega
        //3. printamo sta smo dobili nazad


        //1.1
        if (!storageMap.containsKey(path)) {
            AppConfig.timestampedErrorPrint("Bad pull path - " + path);
            return;
        }

        List<FileInfo> filesToPull = pullFromStorage(path);

        if (filesToPull.isEmpty()) {
            AppConfig.timestampedErrorPrint("No files found to pull - " + path);
            return;
        }

        //1.2
        pulledFiles.clear();
        amountPulled = 0;

        for (FileInfo fileToPull : filesToPull) {
            Message askMessage = new AskPullMessage( AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                    getNextNodeIp(), getNextNodePort(), AppConfig.myServentInfo.getChordId(), fileToPull);
            MessageUtil.sendMessage(askMessage);
            AppConfig.timestampedErrorPrint("sent ask msg " + askMessage);
        }
        amountToPull = filesToPull.size();
    }

    public void printPulledFiles(){
        AppConfig.timestampedStandardPrint("Printing pulled files");
        for (FileInfo pulledFile: pulledFiles){
            AppConfig.timestampedStandardPrint("\n-----" + pulledFile.getPath() + "-----\n" + pulledFile.getContent());
        }
    }

    public List<FileInfo> pullFromStorage (String path){

        if (storageMap.containsKey(path)){
            List<FileInfo> filesToReturn = new ArrayList<>();

            FileInfo requestedFileInfo = storageMap.get(path); //mozda je file mozda je dir

            if (!requestedFileInfo.isDirectory()) {//ako je file, nasli smo ga i samo ga vrati
                filesToReturn.add(requestedFileInfo);
                return filesToReturn;
            }

            //ako je dir onda iskopaj sve filove koji su nam unutar dira
            List<String> allDirSubFilePaths = getAllFilesFromDir(requestedFileInfo);

            for (String pathKey: allDirSubFilePaths){
                if (storageMap.containsKey(pathKey))
                    filesToReturn.add(storageMap.get(pathKey));
            }


            return filesToReturn;
        }
        return Collections.emptyList();

    }

    private List<String> getAllFilesFromDir(FileInfo dirInfo){
        List<String> filePaths = new ArrayList<>();
        for (String path: dirInfo.getSubFiles()) {
            if (path.contains(".")) filePaths.add(path);//ako ima tacku znaci da je neki file
            else filePaths.addAll(getAllFilesFromDir(storageMap.get(path))); //ako nema znaci da je dir i daj mi njegove subdirove
        }
        return filePaths;
    }

    public void removeFileFromStorage(String path){
        if (!storageMap.containsKey(path)) AppConfig.timestampedErrorPrint("File already removed - " + path);

        FileInfo fileToRemove = storageMap.get(path);
        if (fileToRemove.isFile()){
            storageMap.remove(path);
            AppConfig.timestampedStandardPrint("Removing file" + path + " from virtual memory");
            Message removeMessage = new RemoveMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                    getNextNodeIp(), getNextNodePort(), path);
            MessageUtil.sendMessage(removeMessage);
        }
        else {
            for (String dirFileToRemove: getAllFilesFromDir(fileToRemove)) {
                storageMap.remove(dirFileToRemove);
                Message removeMessage = new RemoveMessage(AppConfig.myServentInfo.getIpAddress(), AppConfig.myServentInfo.getListenerPort(),
                        getNextNodeIp(), getNextNodePort(), path);
                MessageUtil.sendMessage(removeMessage);
            }
        }
    }



    public int getNextNodePort() {
        return successorTable[0].getListenerPort();
    }

    public String getNextNodeIp() {
        return successorTable[0].getIpAddress();
    }

    public void setPredecessor(ServentInfo newNodeInfo) {
        this.predecessorInfo = newNodeInfo;
    }

    public boolean isCollision(int chordId) {
        if (chordId == AppConfig.myServentInfo.getChordId()) {
            return true;
        }
        for (ServentInfo serventInfo : allNodeInfo) {
            if (serventInfo.getChordId() == chordId) {
                return true;
            }
        }
        return false;
    }

}
