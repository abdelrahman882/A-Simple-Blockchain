package concrete;

import interfaces.*;
import jdk.internal.util.xml.impl.Pair;
import org.bouncycastle.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.net.URL;
import java.security.*;

import java.security.spec.ECGenParameterSpec;
import java.util.*;

public class Node implements INode {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private int difficulty;
    private boolean isInterrupt = false;

    HashMap<Integer, ITransaction> AvOps = new HashMap<>();
    ArrayList<Integer> newAddedTs = new ArrayList<>();
    private INTW network;
    private int nodeType;
    private int maxTransaction;
    private ArrayList<String> peers;
    private ArrayList<ITransaction> transactions;
    private HashMap<Integer, PublicKey> id2keys;
    private String CONFIG_FILE;
    private PublicKey nodePublicKey;
    private PrivateKey nodePrivateKey;
    private PublicKey primaryNodePublicKey; // primary node's public key
    private int seqNum = 0; //the sequence number of the current block the node is working on
    private int viewNum = 0; //round number
    private int newViewNum = 0; // next view we are moving to
    private String state = "";//which phase the node is currently in
    private String prevState = "";//prev state before requesting view change
    private int maxMaliciousNodes = 0; //max f nodes out of 3f+1 node
    private IBlock block; //current block the node is validating
    private IBlock newBlock; //client (validator) block
    // private ArrayList<PublicKey> peers;
    private IMessagePool preparePool = new MessagePool(); //contains prepare messages
    private IMessagePool commitPool = new MessagePool();
    private byte[] nodeSignature;
    private IValidator validator; //validator is a helper node
    private Timer configTimer;
    private Timer idleTimer;
    private Timer viewChangeTimer;
    private ArrayList<ITransaction> ledger;
    private ArrayList<IBlock> chain;
    private int chainIndex = 0;
    private boolean isPrimary;
    private String nodeIp;
    private boolean isPow;
    private HashMap<Integer, ITransaction> issuedTransactions;
    private HashMap<Integer, KeyPair> myKeyPairs;
    private int from = 0, to = 0;
    private ArrayList<IMessage> commitMessages;
    private ArrayList<IMessage> prepareMessages;
    private Queue<Block> queue;

    public ArrayList<PairKeyPK> getPublicKeysIP() {
        return publicKeysIP;
    }

    private ArrayList<PairKeyPK> publicKeysIP;
    private IUtils utils = Utils.getInstance();
    ArrayList<String> ips;
    ArrayList<Integer> nodeTypes;

    public static void main(String[] args) {

        String conf = "https://drive.google.com/uc?export=download&id=1DdXJ1X_qX8gjUMybsnLc3wybgNCZxH6J";
        try {
            INode node = new Node(conf);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Node(String config_file) throws IOException, ClassNotFoundException {
        CONFIG_FILE = config_file;
        peers = new ArrayList<>();
        transactions = new ArrayList<>();
        id2keys = new HashMap<>();
        prepareMessages = new ArrayList<>();
        commitMessages = new ArrayList<>();
        ips = new ArrayList<>();
        nodeTypes = new ArrayList<>();
        chain = new ArrayList<>();
        publicKeysIP = new ArrayList<>();
        queue = new ArrayDeque<>();//TODO 5N NASSER MAY BE USED TO CACHE ALL BLOCKS AND REMOVE FROM WHEN ADD TO CHAIN
        //TODO 6AB NASSER WE MAY NEED ANOTHER 2 QUEUES FOR AVOP AND NEWADDS
        INTW network = new Network();
        setNTW(network);
        readConfiguration();
        network.setNode(this);
        network.sendPeers(ips, nodeTypes);
        setIsPrimary();
        Thread th = new Thread((Runnable) network);
        th.start();
        generateKeyPair();
        prepare2issue(0, 100);
        System.out.println("is primary constructor :" + getIsPrimary());
//        if (getIsPrimary()) {
//            IMessage configMessage = new Message("config", false, nodePublicKey);
//            sendConfigMessageAtFirst(configMessage);
//        }

    }

//    private void sendConfigMessageAtFirst(IMessage configMessage) throws IOException {
//        network.sendConfigMessageAtFirst(configMessage);
//    }

    private void prepare2issue(int lowerB, int upperB) {
        System.out.println("entered issue" + this.nodeType);

        if (this.nodeType == 0) {
            System.out.println("entered issue");
            this.from = lowerB;
            this.to = upperB;
            this.myKeyPairs = new HashMap<>();
            HashMap<Integer, PublicKey> toBroadcast = new HashMap<>();
            this.issuedTransactions = new HashMap<>();
            for (int i = lowerB; i < upperB; i++) {
                try {
                    KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
                    myKeyPairs.put(i, pair);
                    toBroadcast.put(i, pair.getPublic());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
            Utils.getInstance().setID2PK(toBroadcast);
//            try {
//                this.network.broadcastPK(toBroadcast);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            //call issue when all pks are here
            this.issueTransactions();
        }
    }


    public void readConfiguration() throws IOException {
        URL conf = new URL(CONFIG_FILE);
        BufferedReader in = new BufferedReader(new InputStreamReader(conf.openStream()));
        String res = "";
        StringBuilder sb = new StringBuilder();
        while ((res = in.readLine()) != null) {
            sb.append(res);
            sb.append("\n");
        }
        ;
        res = sb.toString();

        String[] data = res.split("\n");
        int maxSize = Integer.parseInt(data[0].split(":")[1]);
        int diff = Integer.parseInt(data[1].split(":")[1]);
        int pow = Integer.parseInt(data[2].split(":")[1]);
        ips = new ArrayList<>();
        nodeTypes = new ArrayList<>();
        for (int i = 3; i < data.length; i++) {
            ips.add(data[i].split(",")[0]);
            nodeTypes.add(Integer.parseInt(data[i].split(",")[1]));
        }
        // System.out.println(network.getExternalIP()+" "+ips.get(0));
        setConfigs(pow == 1, maxSize, ips, nodeTypes.get(ips.indexOf(network.getExternalIP())), diff);
    }

    @Override
    public void setConfigs(boolean isPow, int maxNumTransactions, ArrayList<String> IPsOfOtherPeers, int nodeType, int diff) {
        this.isPow = isPow;
        this.nodeType = nodeType;
        this.maxTransaction = maxNumTransactions;
        this.peers = IPsOfOtherPeers;
        this.difficulty = diff;
    }

    @Override
    public void setNTW(INTW ntw) {
        this.network = ntw;
    }

    @Override
    public int getNodeType() {
        return nodeType;
    }

    @Override
    public void addTransaction(Transaction t) throws IOException, InterruptedException {
        System.out.println(t.getID());
        if (verifyTransaction(t)) {
            newAddedTs.add(t.getID());
            AvOps.put(t.getID(), t);

            transactions.add(t);
            System.out.println("Verified" + " " + transactions.size());
            if (transactions.size() == maxTransaction) {
                createBlock();
                transactions.clear();
                AvOps.clear();

            }
        }
    }

    @Override
    public void createBlock() throws IOException, InterruptedException {
        block = new Block();
        block.setTransactions(transactions);
        block.setPrevBlock(getLastBlock());
        block.getBlockHash();

        if (isPow) {
            pow(block, difficulty);
            System.out.println("Create Block Pow");
        } else {

            System.out.println("PBFT creating block transactions");
            //generateNewBlockMessage(block);
            if (getIsPrimary())
                generateNewBlockMessage(block);
        }
    }

    private boolean verifyTransactionSign(ITransaction t) {
        int signer = t.getIPs().get(0);
        byte[] signature = t.getSignedHash();

        boolean b = false;
        try {
            Signature s = Signature.getInstance("SHA1WithRSA");
            s.initVerify(t.getPayerPK());
            s.update(t.hash().getBytes());
            b = s.verify(t.getSignedHash());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }

    private boolean verifyTransactionVal(ITransaction t) {
        int prevID = t.getPrevID();
        if (prevID != -1 && t.getIPs().get(0) == 0) {
            ITransaction prev = getUnspentTransactionByID(prevID);
            if (prev == null) {
                return false;
            }
            int out = t.getOutIndex();
            float totalPayed = 0;
            for (ITransaction.OutputPair p : t.getOPs()) {
                totalPayed += p.value;
            }
            ArrayList<ITransaction.OutputPair> ops = prev.getOPs();
            boolean av = prev.getOPs().get(out).available >= totalPayed;
            if (!av) {
                return false;
            }
            prev.getOPs().get(out).available -= totalPayed;

            return true;
        } else {
            return true; // no value check if there is no prev and input is 0
        }
    }

    @Override
    public boolean verifyBlockTransactions(ArrayList<ITransaction> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            if (verifyTransaction(transactions.get(i))) {
                resetUnspent();
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean verifyTransactionsSignature(ArrayList<ITransaction> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            if (!verifyTransactionSign(transactions.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public boolean verifyTransaction(ITransaction t) {
        return verifyTransactionSign(t) && verifyTransactionVal(t);
    }

    @Override
    public void resetUnspent() {
        for (ITransaction t : AvOps.values()) {
            for (ITransaction.OutputPair o : t.getOPs()) {
                o.available = o.committedVal;
            }
        }
        for (int i : newAddedTs) {
            AvOps.remove(i);
        }
        //TODO remove all the transactions added to the hashmap in the last round
    }

    @Override
    public void commitUnspent() {
        for (ITransaction t : AvOps.values()) {
            float totAv = 0;
            for (ITransaction.OutputPair p : t.getOPs()) {
                totAv += p.available;
                p.committedVal = p.available;
            }
            if (totAv <= 0) {
                AvOps.remove(t.getID());
            }
        }
    }

    @Override
    public void issueTransactions() { // in terms of transactions' id
        try {

            URL url = getClass().getResource(Utils.getInstance().TransactionsDatasetDir());
            File file = new File(url.getPath());
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                ITransaction t = ITransaction.parseTransaction(line);
                if (t == null) {
                    System.out.println("t null");
                    continue;
                }
                t.setPrevTransaction(issuedTransactions.get(t.getPrevID()));
                t.hash();
                this.issuedTransactions.put(t.getID(), t);
                if (t.getIPs().get(0) < to && t.getIPs().get(0) >= from) {
                    t.signTransaction(myKeyPairs.get(t.getIPs().get(0)).getPrivate(), myKeyPairs.get(t.getIPs().get(0)).getPublic());
                    System.out.println("Tr issued .." + t.getID() + "  " + t.getIPs().get(0) + " " + t.getOPs().get(0).id + " " + t.getOPs().get(0).value);

                    this.network.issueTransaction((Transaction) t);
                }
            }
            fr.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ITransaction getUnspentTransactionByID(int id) {
        return AvOps.get(id);
    }

    @Override
    public void shareBlock(IBlock block) throws IOException {
        network.broadcastlock(block);
    }


    @Override
    public void addToChain(IBlock block) {
        chain.add(block);
    }

    @Override
    public void broadCastPublicKeys(HashMap<Integer, PublicKey> keys) throws IOException {
        network.broadcastPK(keys);

    }

    @Override
    public void setPublicKeys(HashMap<Integer, PublicKey> t) {
        this.id2keys = t;
    }


////////////////////////////////////////////////////////////////////////////////////////


    //apply POW (mining) verification by Increasing nonce value until hash target is reached.
    @Override
    public void pow(IBlock block, int difficulty) throws IOException {
        System.out.println("Working in pow");
        int nonce = 0;
        String hash = block.getBlockHash();
        String merkleRoot = Utils.getMerkleRoot(block.getTransactions());
        block.getHeader().setTransactionsHash(merkleRoot);
        String target = Utils.getDificultyString(difficulty); //Create a string with difficulty * "0"
        while (!hash.substring(0, difficulty).equals(target) && !isInterrupt) {
            nonce++;
            block.getHeader().setNonce(nonce);
            hash = block.getHeader().calculateHash();
        }
        block.getHeader().setHash(hash);
        block.getHeader().setNonce(nonce);
        System.out.println("block is mined...");
        System.out.println("block hash is: " + block.getHeader().getHash());
        addToChain(block);
        shareBlock(block);
    }


    /*Generate the public and private key for the node*/
    @Override
    public void generateKeyPair() throws IOException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime192v1");
            // Initialize the key generator and generate a KeyPair
            keyGen.initialize(ecSpec, random);   //256 bytes provides an acceptable security level
            KeyPair keyPair = keyGen.generateKeyPair();
            // Set the public and private keys from the keyPair
            this.nodePrivateKey = keyPair.getPrivate();
            this.nodePublicKey = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.nodeIp = this.network.getIP();
        System.out.println("node ip: " + nodeIp);
        this.network.broadcastPK(new PairKeyPK(this.nodeIp, this.nodePublicKey));

        System.out.println("Node keys are generated");
        System.out.println("Node's public key: " + this.nodePublicKey);
        System.out.println("Node's pirivate key: " + this.nodePrivateKey);
    }

    /*Generate a signature for the node using her private key to sign the message*/
    @Override
    public void generateNodeSignature() {
        String data = Utils.getStringFromKey(this.nodePublicKey) + String.valueOf(this.seqNum) +
                String.valueOf(this.viewNum) + this.newBlock.getHeader().getTransactionsHash();
        this.nodeSignature = Utils.applyECDSASig(this.nodePrivateKey, data);

        System.out.println("node signature is generated.");
        System.out.println("Node's signature: " + this.nodeSignature.toString());

    }

    /*this is the new block that the client broadcasts it to all nodes*/
    @Override
    public IBlock getNewBlock() {
        return this.newBlock;
    }

    @Override
    public void setNewBlock(IBlock newBlock) {
        this.newBlock = newBlock;
    }

    /*the node will save the client block with her*/
    @Override
    public void setNewBlock(IMessage newBlockMessage) throws IOException, InterruptedException {

        System.out.println("New block is received");
        if (newBlockMessage.getMessageType().equals("new block")
                && verifyTransactionsSignature(newBlockMessage.getBlock().getTransactions())) {
            this.primaryNodePublicKey = newBlockMessage.getPrimaryPublicKey();
            System.out.println("primary public key in set new block: " + this.primaryNodePublicKey);
            this.viewNum = newBlockMessage.getViewNum();
            this.maxMaliciousNodes = newBlockMessage.getMaxMaliciousNodes();
            System.out.println("passing set new block validation");
            this.newBlock = newBlockMessage.getBlock();
            System.out.println("block hash in set new block: " + this.newBlock.getBlockHash());
        }
        generateNodeSignature();
// TODO 1S NASSER THE CODE HAS ENDED BASED ON TODO 1B
        if (getIsPrimary()) {
            System.out.println("generate new pre-prepare message as the node is primary");
            generatePreprepareMessage();
        }
    }

    /*this is only for the primary which will let the client to sent the block*/
    @Override
    public void generateNewBlockMessage(IBlock block) throws IOException, InterruptedException {
        /*node public key is the primary public key as the primary who will call this function*/

        System.out.println("new Block hash: " + block.getBlockHash());
        if (network.isPrimary()) this.primaryNodePublicKey = this.nodePublicKey;

        this.viewNum++;
        this.maxMaliciousNodes = (sizeOfNetwork() - 1) / 3;
        this.validator = new Validator(this.nodePublicKey, this.seqNum, this.viewNum, this.maxMaliciousNodes, block);
        this.validator.initiateNewBlockMessage();
        // timer
        IMessage newBlockMessage = validator.finalizeBlock();
        this.newBlock = block;
        System.out.println("new block is created");
        System.out.println("new Block node public key: " + newBlockMessage.getPrimaryPublicKey());
        //TODO 1B NASSER SHOULD CALL GENERATE PRE-PREPARE MESSAGE FIRST ?
        broadcastMessage(newBlockMessage);
        generatePreprepareMessage();
    }

    /*this is for the primary node it's the only one who can generate the pre-prepare message
     * and broadcast it to all nodes*/
    @Override
    public void generatePreprepareMessage() throws IOException, InterruptedException {

        generateNodeSignature();
        this.state = "pre-prepare";
        /*node public key is the primary public key as he is the only one who will call this method*/
        IMessage prePrepareMessage = new Message("pre-prepare", this.nodePublicKey, this.seqNum, this.viewNum, this.nodeSignature, this.nodePublicKey, this.newBlock);
        System.out.println("node signature in generate pre-prepare:" + this.nodeSignature);
        System.out.println("pre-prepare message is created");
        // System.out.println("primary node public key: " + p);
        System.out.println("primary node public key: " + prePrepareMessage.getPrimaryPublicKey());
        System.out.println("primary node public key: " + prePrepareMessage.getNodePublicKey());
        System.out.println("node signature in generate pre-prepare: " + prePrepareMessage.getNodeSignature().toString());

        Thread.sleep(10000);
        broadcastMessage(prePrepareMessage);
    }

    /*All nodes except the primary will validate the pre-prepare message and if valid
     * they will take the block inside it to continue the next phases*/
    @Override
    public void insertPreprepareMessage(IMessage preprepareMessage) throws IOException {
        System.out.println("preprepare message max malicious nodes: " + preprepareMessage.getMaxMaliciousNodes());
        System.out.println("preprepare message block hash: " + preprepareMessage.getBlock().getBlockHash());
        System.out.println("preprepare message type: " + preprepareMessage.getMessageType());
        System.out.println("preprepare message primary public key the same the below: " + preprepareMessage.getPrimaryPublicKey());
        System.out.println("preprepare message seq num: " + preprepareMessage.getSeqNum());
        System.out.println("preprepare message view num: " + preprepareMessage.getViewNum());

        preprepareMessage.setNodePublicKey(preprepareMessage.getPrimaryPublicKey());
        System.out.println("preprepare message sending node public key: " + preprepareMessage.getNodePublicKey());


        System.out.println("peer signature in message: " + preprepareMessage.getNodeSignature().toString());
        System.out.println("verify peer signature : " + preprepareMessage.verifyPeerSignature());
        System.out.println("primary ley for the current node: " + this.primaryNodePublicKey);
        System.out.println("node view num: " + this.viewNum);
        System.out.println("new block in pre-prepare " + this.newBlock);

        System.out.println("Node new block hash: " + this.newBlock.getBlockHash());


        System.out.println("check1: " + preprepareMessage.getMessageType().equals("pre-prepare"));
        System.out.println("check2: " + preprepareMessage.verifyPeerSignature());
        System.out.println("check3.1: " + preprepareMessage.getPrimaryPublicKey());
        System.out.println("check3.2: " + this.primaryNodePublicKey);
        System.out.println("check4.1: " + preprepareMessage.getViewNum());
        System.out.println("check4.2: " + this.viewNum);
        System.out.println("check5: " + preprepareMessage.getBlock().getBlockHash().equals(this.newBlock.getBlockHash()));

        if (preprepareMessage.getMessageType().equals("pre-prepare") && preprepareMessage.verifyPeerSignature() &&
                preprepareMessage.getPrimaryPublicKey().equals(this.primaryNodePublicKey) &&
                preprepareMessage.getViewNum() == this.viewNum &&
                preprepareMessage.getBlock().getBlockHash().equals(this.newBlock.getBlockHash())) {

            System.out.println("pre-prepare validation is passed");
            this.state = "pre-prepare";
            this.seqNum = preprepareMessage.getSeqNum();
            this.block = preprepareMessage.getBlock();
        } else {
            System.out.println("Error with secondary Node in preprepare phase verification so the node will ignore this message");
        }

//        if (preprepareMessage.getMessageType().equals("pre-prepare")){
//            System.out.println("check 1 passed");
//            if(preprepareMessage.verifyPeerSignature()){
//                System.out.println("check 2 passed");
//                if(preprepareMessage.getPrimaryPublicKey().equals(this.primaryNodePublicKey) ){
//                    System.out.println("check 3 passed");
//                  if(preprepareMessage.getViewNum() == this.viewNum ){
//                      System.out.println("check 4 passed");
//                      if(preprepareMessage.getBlock().getBlockHash().equals(this.newBlock.getBlockHash())){
//                          System.out.println("check 5 passed");
//                      }else{ System.out.println("check 5 out"); }
//                  }else{ System.out.println("check 4 out"); }
//                }else{ System.out.println("check 3 out"); }
//            }else{ System.out.println("check 2 out"); }
//        }else{ System.out.println("check 1 out"); }
//


        if (!getIsPrimary()) {
            System.out.println("node is not primary so it will generate prepare message");
            generatePrepareMessage();
        }
    }


    @Override
    public IMessagePool getPreparePool() {
        return this.preparePool;
    }


    /*All nodes except the primary will generate this message and broadcasts it to all the network
     * the node has to finish the pre-prepare state before entering prepare state*/
    @Override
    public void generatePrepareMessage() throws IOException {
        if (this.state.equals("pre-prepare")) {
            IMessage prepareMessage = new Message("prepare", this.primaryNodePublicKey, this.seqNum, this.viewNum, this.nodeSignature, this.block, this.nodePublicKey);

            this.preparePool.insertMessage(prepareMessage);
            System.out.println("prepare message is created");
            broadcastMessage(prepareMessage);
            if (sizeOfNetwork() <= 2) {
                this.state = "prepare";
                generateCommitMessage();
            }

        } else {
            System.out.println("Node is out it won't generate a prepare message");
        }
    }

    /*the node will validate the prepare messages that are sent to here
     * and insert them in her prepare pool if a message is invalid it will ignore it.
     * the node has to receive min 2*f+1 prepare message to be able to move to the next phase*/
    @Override
    public void insertPrepareMessageInPool(ArrayList<IMessage> prepareMessages) throws IOException {
        IMessage prepareMessage;
        for (int i = 0; i < prepareMessages.size(); i++) {
            prepareMessage = prepareMessages.get(i);

            if (this.state.equals("pre-prepare") && prepareMessage.getMessageType().equals("prepare") &&
                    prepareMessage.getPrimaryPublicKey().equals(this.primaryNodePublicKey) &&
                    prepareMessage.getViewNum() == this.viewNum && prepareMessage.getSeqNum() == this.seqNum &&
                    prepareMessage.verifyPeerSignature() &&
                    prepareMessage.getBlock().getBlockHash().equals(this.block.getBlockHash()) &&
                    !this.preparePool.isMessageExists(prepareMessage)) {

                System.out.println("prepare validation is passed");
                if (prepareMessage.getNodePublicKey().equals(this.primaryNodePublicKey)) {//TODO 3N NASSER MAY BE CHECKED
                    System.out.println("Error in prepare phase the primary sent a message");
                } else {
                    /*not sent by a primary*/
                    if (verifyBlockTransactions(prepareMessage.getBlock().getTransactions())) {
                        this.preparePool.insertMessage(prepareMessage);
                    }
                }
            } else {
                System.out.println("Error with secondary node in prepare phase validation, the node will ignore this message");
            }
        }

        if (this.preparePool.getPoolSize() >= 2 * this.maxMaliciousNodes + 1) {
            this.state = "prepare";
            System.out.println("node passed prepare phase");
            this.preparePool.clean();
        }

        generateCommitMessage();

    }


    @Override
    public IMessagePool getCommitPool() {
        return this.commitPool;
    }

    /*All nodes including primary will broadcast a commit message all other nodes*/
    @Override
    public void generateCommitMessage() throws IOException {
        if (this.state.equals("prepare")) {
            IMessage commitMessage = new Message("commit", this.primaryNodePublicKey, this.seqNum, this.viewNum, this.nodeSignature, this.block, this.nodePublicKey);
            this.commitPool.insertMessage(commitMessage);

            System.out.println("node generated commit message and added it to her commit pool");
            broadcastMessage(commitMessage);
        } else {
            System.out.println("Node is out it won't generate a commit message as it doesn't finish her prepare phase");
        }
    }

    /*the node will validate the commit messages that are sent to here
     * and insert them in her commit pool if a message is invalid it will ignore it.
     * the node has to receive min 2*f+1 commit message to be able to move to the final phase*/
    @Override
    public void insertCommitMessageInPool(ArrayList<IMessage> commitMessages) throws IOException {
        IMessage commitMessage;
        for (int i = 0; i < commitMessages.size(); i++) {
            commitMessage = commitMessages.get(i);

            if (this.state.equals("prepare") && commitMessage.getMessageType().equals("commit") &&
                    commitMessage.getPrimaryPublicKey().equals(this.primaryNodePublicKey) &&
                    commitMessage.getSeqNum() == this.seqNum && commitMessage.getViewNum() == this.viewNum &&
                    commitMessage.verifyPeerSignature() &&
                    commitMessage.getBlock().getBlockHash().equals(this.block.getBlockHash()) &&
                    !commitPool.isMessageExists(commitMessage)) {
                System.out.println("commit validation is passed");
                this.commitPool.insertMessage(commitMessage);
            } else {
                System.out.println("Error with Node while validating this commit message the node will ignore it.");
            }
        }

        if (this.commitPool.getPoolSize() >= 2 * this.maxMaliciousNodes + 1) {
            /*mark transactions as spent*/
            verifyBlockTransactions(this.block.getTransactions());
            commitUnspent();
            this.state = "commit";
            System.out.println("node passed commit phase");
            this.commitPool.clean();
            addToChain(this.block);
            System.out.println("node added the block to chain");
        }

        if (this.network.isPrimary())
            generateConfigMessage(this.primaryNodePublicKey);//TODO 4N NASSER MAY BE CHECKED

    }

    public void generateConfigMessage(PublicKey primaryNodePublicKey) throws IOException {
        this.maxMaliciousNodes = (sizeOfNetwork() - 1) / 3;
        IMessage configMessage = new Message("config", isPrimary, primaryNodePublicKey);
        System.out.println("Generating config message...");
        sendConfigMessage(configMessage);
    }

    public void receiveConfigs(IMessage configMessage) {
        System.out.println("Node received config message");
        System.out.println("max malicious nodes in config message: " + configMessage.getMaxMaliciousNodes());
        System.out.println("primary id in config message: " + configMessage.getPrimaryPublicKey().toString());
        System.out.println("is primary in config message: " + configMessage.isPrimary());
        System.out.println("is primary from network call: " + getIsPrimary());

        this.maxMaliciousNodes = configMessage.getMaxMaliciousNodes();
        this.primaryNodePublicKey = configMessage.getPrimaryPublicKey();
        this.isPrimary = configMessage.isPrimary();
        //TODO 7N NASSER SHOULD POLL FROM QUEUE AND START (INSIDE) CREATE BLOCK

    }


    public synchronized void receiveMessage(IMessage t) throws IOException, InterruptedException {
        String type = t.getMessageType();
        System.out.println("receive message: " + t.getMessageType());
        switch (type) {
            case "config":
                network.setPrimary(t.isPrimary());
                setIsPrimary();
                receiveConfigs(t);
                break;
            case "new block":
                setNewBlock(t);
                break;
            case "pre-prepare":
                insertPreprepareMessage(t);
                break;
            case "commit":
                commitMessages.add(t);
                if (commitMessages.size() == network.getsizeofPeers()) {
                    insertCommitMessageInPool(commitMessages);
                    commitMessages.clear();
                }
                break;
            case "prepare":
                prepareMessages.add(t);
                if (prepareMessages.size() == network.getsizeofPeers()) {//TODO 2S NASSER SHOULD BE SIZE-1 EXCLUDE THE PRIMARY
                    insertPrepareMessageInPool(prepareMessages);
                    prepareMessages.clear();
                }
                break;
            default:
                System.out.println("No Type");
        }
    }

    public void sendConfigMessage(IMessage m) throws IOException {
        network.sendConfigMessage(m);
    }

    @Override
    public int sizeOfNetwork() {
        return network.getsizeofPeers() + 1;
    }

    @Override
    public void broadcastMessage(IMessage message) throws IOException {
        this.network.broadcastMessage(message);
    }


    @Override
    public void addTransaction(ArrayList<ITransaction> ledger) {
        this.ledger = ledger;
    }


    @Override
    public void receiveBlock(IBlock block) {
        this.block = block;
        boolean b = block.verifyBlockHash();
        this.isInterrupt = true;
        addToChain(block);
        System.out.println("isVerified: " + b + " " + chain.size());
    }


    @Override
    public IBlock getLastBlock() {
        if (chain.size() > 0) {
            return chain.get(chain.size() - 1);
        }
        return null;
    }

    @Override
    public PublicKey getPrimaryId() {
        return this.primaryNodePublicKey;
    }

    @Override
    public int getNewViewNum() {
        return newViewNum;
    }

    @Override
    public void setNewViewNum(int newViewNum) {
        this.newViewNum = newViewNum;
    }

    @Override
    public IBlock getBlock() {
        return block;
    }

    @Override
    public void setBlock(IBlock block) {
        this.block = block;
    }

    @Override
    public void setPrimaryId(PublicKey primaryId) {
        this.primaryNodePublicKey = primaryId;
    }

    @Override
    public int getSeqNum() {
        return this.seqNum;
    }

    @Override
    public void setSeqNum() {
        this.seqNum = this.chain.size() + 1;
    }


    @Override
    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    @Override
    public int getViewNum() {
        return this.viewNum;
    }

    @Override
    public void setViewNum() {
        this.viewNum = this.chain.size() + 1;
    }

    @Override
    public void setViewNum(int viewNum) {
        this.viewNum = viewNum;
    }

    @Override
    public void setIsPrimary() {
        this.isPrimary = this.network.isPrimary();
    }

    @Override
    public boolean getIsPrimary() {
        return network.isPrimary();
    }

    @Override
    public void setState(String state) {
        this.state = state;
    }

    @Override
    public byte[] getNodeSignature() {
        return nodeSignature;
    }

    @Override
    public void setNodeSignature(byte[] nodeSignature) {
        this.nodeSignature = nodeSignature;
    }

    @Override
    public IValidator getValidator() {
        return validator;
    }

    @Override
    public void setValidator(IValidator validator) {
        this.validator = validator;
    }

    @Override
    public String getState() {
        return this.state;
    }

    @Override

    public void setMaxMaliciousNodes(int f) {
        this.maxMaliciousNodes = f;
    }

    @Override
    public int getMaxMaliciousNodes() {
        return this.maxMaliciousNodes;
    }

    @Override
    public PublicKey getNodePublicKey() {
        return this.nodePublicKey;
    }

    @Override
    public INTW getNetwork() {
        return network;
    }

    @Override
    public void setNetwork(INTW network) {
        this.network = network;
    }

    @Override
    public IBlock getCurrentBlock() {
        return block;
    }

    @Override
    public void setCurrentBlock(IBlock currentBlock) {
        this.block = currentBlock;
    }

    @Override
    public String getPrevState() {
        return prevState;
    }

    @Override
    public void setPrevState(String prevState) {
        this.prevState = prevState;
    }

    @Override
    public void receivePK(PairKeyPK t) {
        publicKeysIP.add(t);
    }
}