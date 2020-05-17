package concrete;

import interfaces.IAnalyser;
import interfaces.INTW;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Analyser implements IAnalyser {
    private static IAnalyser ins = null;
    public static IAnalyser getInstance(){
        if(ins == null){
            ins = new Analyser();
        }
        return ins;
    }
    public Analyser(){
        this.allData=new ArrayList<>();
        this.allData.add(this.myData);
    }
    private int type = 0;
    private int blockSize=100;
    private int difficulty = 3;
    private int numberOfParticipants= 3;
    private ArrayList<Analytics> allData = new ArrayList<>();

    private Analytics myData = new Analytics();
    private String report;

    @Override
    public void setType(int i) {
        this.type = i;
    }

    //Exchanging data between nodes


    @Override
    public void broadcastData(INTW ntw) {
        ntw.broadcastAnalytics(this.myData);
    }

    @Override
    public void receiveData(Analytics data) {
        allData.add(data);
    }

    @Override
    public boolean isDoneExchanging() {
        return this.allData.size() == this.numberOfParticipants ;
    }

    //POW ===============================================================
    @Override
    public void setBlockSize(int size) {
        this.blockSize = size;
    }

    @Override
    public void setDifficulty(int diff) {
        this.difficulty = diff;
    }

    @Override
    public float getAvgMessageComplexity() {
        float numExch =0;
        for(Analytics a : this.allData){
            numExch += a.avgNumOfMessExch4Block;
        }

        return numExch / (float)this.numberOfParticipants;
    }

    @Override
    public float getNumberOfStaleBlocks() {
        int n =0;
        for(Analytics a : this.allData){
            n += a.stales;
        }
        return n;
    }

    @Override
    public float getAvgTimeToMine() {
        float tttm =0;
        int nosm=0;
        for(Analytics a : this.allData){
            tttm +=a.totalTTM;
            nosm+= a.numberOfSuccessfulMines;
        }
        return tttm/(float)nosm;
    }
    //BFT =======================================================================

    @Override
    public void setNumOfParticipants(int num) {
        this.numberOfParticipants = num;
    }

    @Override
    public float getMessageComplexity() {
        float numExch =0;
        for(Analytics a : this.allData){
            numExch += a.avgNumOfMessExch4Block;
        }

        return numExch ;
    }

    @Override
    public float getAvgTimeToAgreeOnBlock() {
        float totT =0;
        int numAg = 0;
        for(Analytics a : this.allData){
            totT += a.totalAgreeOnBlockTime;
            numAg += a.numberOfAgreedOnBlocks;
        }
        return totT / numAg;
    }
    //reporting

    @Override
    public String saveReport() {
        this.report = this.getReport();
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("report_"+(this.type == 0 ? "pow":"bft")+"_"+this.blockSize+"_"+this.difficulty+"_"+this.numberOfParticipants+".txt"));

            writer.write(this.report);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this.report;
    }

    @Override
    public String getReport() {
        StringBuilder sb = new StringBuilder();
        if(this.type==0){//pow
            sb.append("Type:pow\n");
            sb.append("BlockSize:"+this.blockSize+"\n");
            sb.append("Difficulty:"+this.difficulty+"\n");
            sb.append("AMC:"+this.getAvgMessageComplexity()+"\n");
            sb.append("NSB:"+this.getNumberOfStaleBlocks()+"\n");
            sb.append("ATM:"+this.getAvgTimeToMine()+"\n");

        }else{//bft
            sb.append("Type:bft\n");
            sb.append("NumOfNodes:"+this.numberOfParticipants+"\n");
            sb.append("MC:"+this.getMessageComplexity()+"\n");
            sb.append("ATA:"+this.getAvgTimeToAgreeOnBlock()+"\n");

        }

        return sb.toString();
    }
    //reporting ====================================================================
    private int numOfMessages =0;
    private int numOfBlocks =0;
    private double miningStart =0;
    private double bftStart =0;


    @Override
    public void reportMessageSent() {
        this.numOfMessages ++;
    }

    @Override
    public void reportBlockDone() {
        this.myData.avgNumOfMessExch4Block = ((this.myData.avgNumOfMessExch4Block *this.numOfBlocks) +(float) this.numOfMessages) / (this.numOfBlocks+1);
        this.numOfBlocks ++;
        this.numOfMessages=0;

    }

    @Override
    public void reportStartingMining() {
        this.miningStart = System.currentTimeMillis();
    }

    @Override
    public void reportEndingMiningSuccessfully() {
        this.myData.totalTTM += System.currentTimeMillis() - this.miningStart;
        this.myData.numberOfSuccessfulMines ++;
    }

    @Override
    public void reportEndingMiningUnsuccessfully() {
        //nothing here , it's just to make sure you call the above func only on success
    }

    @Override
    public void reportStale() {
        this.myData.stales++;

    }

    @Override
    public void reportStartingBFTVoting() {
        this.bftStart = System.currentTimeMillis();
    }

    @Override
    public void reportEndingBFTVoting() {
        this.myData.totalAgreeOnBlockTime += System.currentTimeMillis() - this.bftStart;
        this.myData.numberOfAgreedOnBlocks++;
    }
}