import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class PeerProcess {

	/*common properties used for all peers*/
	private int numberOfPeers;// number of peers in the system
	private int numberOfPieces;	
	private int NumberOfPreferredPeers;// number of preferred neighbors


	private int UnchokingInterval;
	private int OptimisticUnchokingInterval;

	private String fileName;
	private long fileSize;
	private long pieceSize;

   /*properties used for myself*/
	private PeerInfo myself;

	private ServerSocket server;

	private int index; //the index in the Common.cfg	
	private byte[] currentBitField;//my bitfield
	private boolean[] isRequested;//record if each piece has been requested
	
	/*properties used for other peers*/
	private int[] downloadRate;// have the download rate per each peer
	private boolean[] isInterested;//recode if the neighbor peers are interested in my file	
	private boolean[] isPreferred;// each entry is true if the peer is preferable, false otherwise	
	private Connection[] peersConnections;// each entry correspond to a peer connection(socket,id,index,....)	
	private byte[][] neighborsBitFields;//maintain bit field for each peer
	private Vector<PeerInfo> neighborPeers;//the basic information for neighbor peers(id,address,port)
	private int optimesticUnchokedPeerIndex;
    private Timer optiUnchockTimer;
    private Timer unchockTimer;

	/*
	private PeerInfo CurrentPeerInfo;*/

	
	public static void main(String[] args){
		String myPeerId;
		if(args.length==1)
			myPeerId=args[0];
		else if(args.length==0){
			System.out.println("We need one argument for peerId");
			return;
		}
		else{
			System.out.println("Too many arguments");
			return;
		}
		PeerProcess myprocess=new PeerProcess();
		myprocess.init(myPeerId);
	}

		public void init(String myPeerId) {
		// read the configuration files, initialize variables
		//ex:
		String st;
		int connectPeers=0;
		try {
			BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
			while((st = in.readLine()) != null) {
				
				 String[] tokens = st.split("\\s+");
				 String peerId=tokens[0];
				 String hostname=tokens[1];
				 String portnumber=tokens[2];			
                 boolean hasFile=tokens[3].equals("1")? true : false;

				 
				 if(peerId.equals(myPeerId)){
					 this.index=connectPeers;
					 this.myself=new PeerInfo(peerId,hostname,portnumber,hasFile);

				 }
				 else  this.neighborPeers.add(new PeerInfo(peerId,hostname,portnumber,hasFile));	 
				 connectPeers++;	
			}			
			in.close();			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String st1;
		try {
			BufferedReader in = new BufferedReader(new FileReader("Common.cfg"));
			while((st1 = in.readLine()) != null) {
				
				 String[] tokens = st1.split("\\s+");
				 if(tokens[0].equals("NumberOfPreferredNeighbors"))
					 this.NumberOfPreferredPeers=Integer.parseInt(tokens[1]);
				 else if(tokens[0].equals("UnchokingInterval"))
					 this.UnchokingInterval=Integer.parseInt(tokens[1]);
				 else if(tokens[0].equals("OptimisticUnchockingInterval"))
					 this.OptimisticUnchokingInterval=Integer.parseInt(tokens[1]);
				 else if(tokens[0].equals("FileName"))
					 this.fileName=tokens[1];
				 else if(tokens[0].equals("FileSize"))
					 this.fileSize=Long.parseLong(tokens[1]);
				 else if(tokens[0].equals("PieceSize"))
					 this.pieceSize=Long.parseLong(tokens[1]);
			}			
			in.close();			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.numberOfPieces=(int)Math.ceil(this.fileSize/(double)this.pieceSize);

		this.numberOfPeers=connectPeers-1;
		downloadRate = new int[numberOfPeers];
		isPreferred = new boolean[numberOfPeers];
		peersConnections = new Connection[numberOfPeers];
		neighborsBitFields = new byte[numberOfPeers][numberOfPieces];

		this.optiUnchockTimer=new Timer();		
		this.unchockTimer=new Timer();
		
		
		//start listen
		startListening();
		// make TCP connection to the peers that already in system
		for (int i = 0; i < this.index; i++) {
			createConnection(i);

		}	
		// create the thread that calculate preferable peers(chocks/ unchocks)
		startCalculatingPreferredPeers();
		// create the thread that calculate optimistic peer
		startCalculatingOptimisticPeer();		
	}
	
	private void startListening() {		
		//start server sockets
		//listen to peers that comes after and wants to make tcp connection
		try {
			this.server=new ServerSocket(Integer.parseInt(this.myself.getPortnumber()));
			while(true){
				Socket client=server.accept();
				String client_hostname=client.getInetAddress().getHostName();
				int indexPeer=-1;
				for(int i=0;i<this.neighborPeers.size();i++){
					if(client_hostname.equals(neighborPeers.elementAt(i).getHostname())){
						indexPeer=i;
						break;
					}						
				}
				if(indexPeer!=-1)
					this.createConnectionForPeer(indexPeer, client);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}						
	}

    private void createConnection(int index) {

         this.peersConnections[index]=new Connection(neighborPeers.elementAt(index),index,this);   
         this.peersConnections[index].connect();
	}

	private void createConnectionForPeer(int index,Socket socket){
		this.peersConnections[index]=new Connection(neighborPeers.elementAt(index),index,this);

		this.peersConnections[index].setTcpConnection(socket);
	}
	
	private void startCalculatingPreferredPeers() {
		// preferred peers will be chosen at random for the first time , then
		// will be chosen based on download rate
		this.unchockTimer.schedule(new UnchockTask(),0, this.UnchokingInterval*1000);
	}

	private void startCalculatingOptimisticPeer() {
		this.optiUnchockTimer.schedule(new OptiUnchockTask(),0,this.OptimisticUnchokingInterval*1000);
	}

	class UnchockTask extends TimerTask{
		public void run(){
			boolean[] tempPreferred=new boolean[numberOfPeers];
			for(int n=0;n<numberOfPeers;n++)
				tempPreferred[n]=false;
			
			Vector<Integer> randomSet=new Vector<Integer>();
			Random r=new Random();
			for(int i=0;i<numberOfPeers;i++){
				if(isInterested[i])
				  randomSet.add(i);
			}
			//randomly selected if I have file
			if(myself.isCompleteFile()){
				for(int i=NumberOfPreferredPeers;i>0 && randomSet.size()>0;i--){
					int randIndex=r.nextInt(randomSet.size());
					//set the corresponding peer to be preferred
					//if it has been unchocked, no need to send unchocked message
					if(!isPreferred[randomSet.get(randIndex)]){
						tempPreferred[randomSet.get(randIndex)]=true;
						//send unchocked message
					}
					else{
						tempPreferred[randomSet.get(randIndex)]=true;
					}
					randomSet.remove(randIndex);
				}
			}
			//select according to download rate
			else{
			    int m=NumberOfPreferredPeers;			    
			    while(m>0 && randomSet.size()>0){
			    	int high=-1;	
			    	ArrayList<Integer> temp=new ArrayList<Integer>();
			    	for(int j=0;j<randomSet.size();j++){
			    		if(high<downloadRate[randomSet.get(j)]){
			    			high=downloadRate[randomSet.get(j)];
			    			temp.clear();
			    			temp.add(j);
			    		}
			    		else if(high==downloadRate[randomSet.get(j)]){
			    			temp.add(j);
			    		}
			    	}
			    	
			    	if(temp.size()<m){
			    		//all these set to be preferred
			    		for(int j=0;j<temp.size();j++){
			    			if(!isPreferred[randomSet.get(temp.get(j))]){
								tempPreferred[randomSet.get(temp.get(j))]=true;
								//send unchocked message
							}
							else{
								tempPreferred[randomSet.get(temp.get(j))]=true;
							}
			    			//remove these value from randomSet
			    			randomSet.remove(temp.get(j)-j);
			    		}			    		
			    		m=m-temp.size();
			    	}
			    	else{
			    		//we just need to randomly select m numbers
			    		for(int j=m;j>0;j--){
							int randIndex=r.nextInt(temp.size());
							//set the corresponding peer to be preferred
							//if it has been unchocked, no need to send unchocked message
							if(!isPreferred[randomSet.get(temp.get(randIndex))]){
								tempPreferred[randomSet.get(temp.get(randIndex))]=true;
								//send unchocked message
							}
							else{
								tempPreferred[randomSet.get(temp.get(randIndex))]=true;
							}
						}
			    		m=0;
			    	}
			    }
			}
			
			isPreferred=tempPreferred;
		}
	}
	
	class OptiUnchockTask extends TimerTask{
		public void run(){
			Vector<Integer> randomSet=new Vector<Integer>();
			for(int i=0;i<numberOfPeers;i++){
				//choose interested but chocked
				if(isInterested[i]&&(!isPreferred[i])){
					randomSet.add(i);
				}
			}
			if(randomSet.size()==0)
				optimesticUnchokedPeerIndex=-1;
			else if(randomSet.size()==1)
				optimesticUnchokedPeerIndex=randomSet.elementAt(0);
			else{
				int size=randomSet.size();
				Random r=new Random();
				int s=r.nextInt(size);
				optimesticUnchokedPeerIndex=randomSet.elementAt(s);
				//sends unckocked message to this selected peer
			}
		}
	}
	public void increaseDownloadRate(int peerIndex) {
		downloadRate[peerIndex]++;
	}

	public int getNumberOfPeers() {
		return numberOfPeers;
	}

	public void setNumberOfPeers(int numberOfPeers) {
		this.numberOfPeers = numberOfPeers;
	}

	public int getNumberOfPieces() {
		return numberOfPieces;
	}

	public void setNumberOfPieces(int numberOfPieces) {
		this.numberOfPieces = numberOfPieces;
	}

	public int getNumberOfPreferredPeers() {
		return NumberOfPreferredPeers;
	}

	public void setNumberOfPreferredPeers(int numberOfPreferredPeers) {
		NumberOfPreferredPeers = numberOfPreferredPeers;
	}

	public int getUnchokingInterval() {
		return UnchokingInterval;
	}

	public void setUnchokingInterval(int unchokingInterval) {
		UnchokingInterval = unchokingInterval;
	}

	public int getOptimisticUnchokingInterval() {
		return OptimisticUnchokingInterval;
	}

	public void setOptimisticUnchokingInterval(int optimisticUnchokingInterval) {
		OptimisticUnchokingInterval = optimisticUnchokingInterval;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public long getPieceSize() {
		return pieceSize;
	}

	public void setPieceSize(long pieceSize) {
		this.pieceSize = pieceSize;
	}

	public int[] getDownloadRate() {
		return downloadRate;
	}

	public void setDownloadRate(int[] downloadRate) {
		this.downloadRate = downloadRate;
	}

	public boolean[] getIsInterested() {
		return isInterested;
	}

	public void setIsInterested(boolean[] isInterested) {
		this.isInterested = isInterested;
	}

	public boolean[] getIsPreferred() {
		return isPreferred;
	}

	public void setIsPreferred(boolean[] isPreferred) {
		this.isPreferred = isPreferred;
	}

	public Connection[] getPeersConnections() {
		return peersConnections;
	}

	public void setPeersConnections(Connection[] peersConnections) {
		this.peersConnections = peersConnections;
	}

	public byte[][] getNeighborsBitFields() {
		return neighborsBitFields;
	}

	public void setNeighborsBitFields(byte[][] neighborsBitFields) {
		this.neighborsBitFields = neighborsBitFields;
	}

	public byte[] getCurrentBitField() {
		return currentBitField;
	}

	public void setCurrentBitField(byte[] currentBitField) {
		this.currentBitField = currentBitField;
	}

	public boolean[] getIsRequested() {
		return isRequested;
	}

	public void setIsRequested(boolean[] isRequested) {
		this.isRequested = isRequested;
	}

	public Vector<PeerInfo> getNeighborPeers() {
		return neighborPeers;
	}

	public void setNeighborPeer(Vector<PeerInfo> neighborPeer) {
		this.neighborPeers = neighborPeer;
	}

	public PeerInfo getMyself() {
		return myself;
	}

	public void setMyself(PeerInfo myself) {
		this.myself = myself;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public ServerSocket getServer() {
		return server;
	}

	public void setServer(ServerSocket server) {
		this.server = server;
	}
	
	public synchronized boolean havePiece(long pieceNumber) {
		int byteIndex = (int) Math.floor(pieceNumber/8);
	    return getBitValue(currentBitField[byteIndex],  pieceNumber);
	}
	
	public synchronized boolean getBitValue(byte b, long pieceNumber) {
		int bitIndex = (int) (pieceNumber % 8);
	    return (b & (1 << bitIndex)) != 0;
	}
	public void setPeerBitField(int peerIndex, byte[] bitField){
		neighborsBitFields[peerIndex] = bitField;
	}
	
	public synchronized void pieceReceived(long pieceIndex) {
		int byteIndex = (int) Math.floor(pieceIndex/8);
		int bitIndex = (int) (pieceIndex % 8);
		currentBitField[byteIndex] |= (1 << bitIndex);
		//check if the file is completelt downloaded?? i.e. we have all pieces
	}

	public synchronized void updatePeerBitField(int peerIndex, long pieceIndex) {
		int byteIndex = (int) Math.floor(pieceIndex/8);
		int bitIndex = (int) (pieceIndex % 8);
		neighborsBitFields[peerIndex][byteIndex] |= (1 << bitIndex);
	}
	
	public void setIntereseted(int peerIndex) {
		isInterested[peerIndex] = true;
	}
	public void unSetIntereseted(int peerIndex) {
		isInterested[peerIndex] = false;
	}
	
	public synchronized boolean getIsRequested(int pieceIndex) {
		return isRequested[pieceIndex];
	}
	public synchronized void setIsRequested(int pieceIndex, boolean isRequested) {
		this.isRequested[pieceIndex] = isRequested;
	}
	
	public synchronized boolean isPrefered(int peerIndex) {
		return isPreferred[peerIndex];
	}
	public synchronized boolean isOptimesticUnchockedNeighbor(int peerIndex) {
		if(optimesticUnchokedPeerIndex == peerIndex)
			return true;
		else
			return false; 
	}
}
