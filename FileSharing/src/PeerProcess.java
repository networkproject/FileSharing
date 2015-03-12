import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Vector;

public class PeerProcess {

	// number of peers in the system
	private int numberOfPeers;
	private int numberOfPieces;
	// number of preferred neighbors
	private int NumberOfPreferredPeers;
	private int UnchokingInterval;
	private int OptimisticUnchokingInterval;

	private String fileName;
	private long fileSize;
	private long pieceSize;

	// have the download rate per each peer
	private int[] downloadRate;
	private boolean[] isInterested;
	// each entry is true if the peer is preferable, false otherwise
	private boolean[] isPreferred;
	// each entry correspond to a peer connection(contain socket, id ,
	// index,....)
	private Connection[] peersConnections;
	//maintain bit field for each peer
	private byte[][] neighborsBitFields;
	
	private byte[] currentBitField;
	private boolean[] isRequested;
	private Vector<PeerInfo> neighborPeer;
	private PeerInfo myself;
	
	private int index; 
	private int numberOfConPeers;
	private ServerSocket server;

	private int optimesticUnchokedPeerIndex;
	
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
		int index=0;
		try {
			BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
			while((st = in.readLine()) != null) {
				
				 String[] tokens = st.split("\\s+");
				 String peerId=tokens[0];
				 String hostname=tokens[1];
				 int portnumber=Integer.parseInt(tokens[2]);
				 boolean isCompleteFile=tokens[3].equals("1")? true : false;
				
				 
				 if(peerId.equals(myPeerId)){
					 this.index=index;
					 this.myself=new PeerInfo(peerId,hostname,portnumber,isCompleteFile);
				 }
				 else  this.neighborPeer.add(new PeerInfo(peerId,hostname,portnumber,isCompleteFile));	 
				 index++;	
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
		int numberOfBytes = numberOfPieces /8;//8 bits per byte and each represent a piece
		this.numberOfPeers=index-1;
		downloadRate = new int[numberOfPeers];
		isPreferred = new boolean[numberOfPeers];
		peersConnections = new Connection[numberOfPeers];
		neighborsBitFields = new byte[numberOfPeers][numberOfPieces];
		currentBitField = new byte[numberOfBytes];
		isRequested = new boolean[numberOfPieces];
		// create the thread that calculate preferable peers(chocks/ unchocks)
		startCalculatingPreferredPeers();
		// create the thread that calculate optimistic peer
		startCalculatingOptimisticPeer();
		//start listen
		startListening();
		// make TCP connection to the peers that already in system
		for (int i = 0; i < this.index; i++) {
			createConnection(i, neighborPeer.get(i));
			this.numberOfConPeers++;
		}		
	}
	
	private void startListening() {		
		//start server sockets
		//listen to peers that comes after and wants to make tcp connection
		try {
			this.server=new ServerSocket(this.myself.getPortnumber());
		} catch (IOException e) {
			e.printStackTrace();
		}						
	}
	private void createConnection(int index, PeerInfo info) {
		Connection connection = new Connection(info, index, this);
		peersConnections[index] = connection;
		connection.connect();
	}

	private void startCalculatingPreferredPeers() {
		// preferred peers will be chosen at random for the first time , then
		// will be chosen based on download rate
	}

	private void startCalculatingOptimisticPeer() {

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

	public Vector<PeerInfo> getNeighborPeer() {
		return neighborPeer;
	}

	public void setNeighborPeer(Vector<PeerInfo> neighborPeer) {
		this.neighborPeer = neighborPeer;
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

	public int getNumberOfConPeers() {
		return numberOfConPeers;
	}

	public void setNumberOfConPeers(int numberOfConPeers) {
		this.numberOfConPeers = numberOfConPeers;
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
