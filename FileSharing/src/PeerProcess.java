import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
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
	private boolean[] isUnchocked;
	private int optimesticUnchokedPeerIndex;
	private Timer optiUnchockTimer;
	private Timer unchockTimer;
	private String folder_name;
	private Logger logger;
	private static String peer_Id="";
	private int numberPieceHave;

	
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
		logger = Logger.getInstance();
		neighborPeers = new Vector<PeerInfo>(5, 2);
		// read the configuration files, initialize variables
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
					peer_Id=peerId;

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
		isInterested=new boolean[numberOfPeers];
		peersConnections = new Connection[numberOfPeers];
		neighborsBitFields = new byte[numberOfPeers][numberOfPieces];
		isRequested=new boolean[numberOfPieces];
		this.optimesticUnchokedPeerIndex=-1;
		this.isUnchocked=new boolean[numberOfPeers];
		currentBitField=new byte[(int)Math.ceil(numberOfPieces/8.0)];
		
		this.optiUnchockTimer=new Timer();		
		this.unchockTimer=new Timer();

		/*check if we need to create folder peer_peerId*/
		String currPath=System.getProperty("user.dir");
		folder_name=currPath+"/peer_"+myself.getPeerId();
		File file=new File(folder_name);
		if(!file.exists()&&!file.isDirectory())
			file.mkdir();

		/*If I am complete, split file into pieces */
		if(myself.isCompleteFile()){
			this.numberPieceHave=this.numberOfPieces;
			String file_absoluteName=folder_name+"/"+fileName;
			File file1=new File(file_absoluteName);
			if(file1.exists()){
				//split it into pieces and set currentBitField to be 1
				try {
					this.splitFile(file1, folder_name);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				for(int i=0;i<this.currentBitField.length;i++){
					if(i!=currentBitField.length-1)
						this.currentBitField[i]=-1;
					else {
						String ss="";
						int nn=numberOfPieces%8;
						for(int j=0;j<8;j++){
							if(j<=nn)
								ss+="1";
							else ss+="0";
						}
						this.currentBitField[i]=decodeBinaryString(ss);
					}
				}
					
			}
		}

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

	//I connect others actively
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
					tempPreferred[randomSet.get(randIndex)]=true;

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
							temp.add(randomSet.get(j));
						}
						else if(high==downloadRate[randomSet.get(j)]){
							temp.add(randomSet.get(j));
						}
					}

					if(temp.size()<m){
						//all these set to be preferred
						for(int j=0;j<temp.size();j++){
							tempPreferred[temp.get(j)]=true;
							//remove these value from randomSet
							randomSet.remove(temp.get(j));
						}			    		
						m=m-temp.size();
					}
					else{
						//we just need to randomly select m numbers
						for(int j=m;j>0;j--){
							int randIndex=r.nextInt(temp.size());
							//set the corresponding peer to be preferred
							//if it has been unchocked, no need to send unchocked message							
							tempPreferred[temp.get(randIndex)]=true;
							temp.remove(randIndex);							
						}
						m=0;
					}
				}
			}

			//if it has been unchocked, no need to send unchocked message
			for(int i=0;i<isUnchocked.length;i++){
				if(isUnchocked[i]!=tempPreferred[i]){
					if(isUnchocked[i]){
						if(i!=optimesticUnchokedPeerIndex){
							isUnchocked[i]=false;
							//unchocked becomes chock
							peersConnections[i].sendChockMessage();;
						}
					}
					else {
						isUnchocked[i]=true;
						peersConnections[i].sendUnChockMessage();;
					}
				}
			}
			int k=0;
			String pre_list="";
			boolean isChanged=false;
			while(k<tempPreferred.length){
				if(tempPreferred[k]!=isPreferred[k])
					isChanged=true;
				if(tempPreferred[k])
					pre_list+=neighborPeers.get(k).getPeerId()+" ";
			}
			if(isChanged){
				String s;
				if(pre_list.length()==0){
					s="Peer "+peer_Id+" has no preferred neighbors";
				}
				else{
					s="Peer "+peer_Id+" has the preferred neighbors "+pre_list.trim();
				}
				logger.writeLog(getTime(), s);
					
			}
			isPreferred=tempPreferred;
			//reset the download rate
			for(int i=0;i<downloadRate.length;i++)
				downloadRate[i]=0;
		}
	}

	class OptiUnchockTask extends TimerTask{
		public void run(){
			Vector<Integer> randomSet=new Vector<Integer>();
			for(int i=0;i<numberOfPeers;i++){
				//choose interested but chocked
				if(isInterested[i]&&(!isUnchocked[i])){
					randomSet.add(i);
				}
			}
			int temp;
			if(randomSet.size()==0)
				temp=-1;
			else if(randomSet.size()==1)
				temp=randomSet.elementAt(0);
			else{
				int size=randomSet.size();
				Random r=new Random();
				int s=r.nextInt(size);
				temp=randomSet.elementAt(s);
			}
			
			if(optimesticUnchokedPeerIndex!=temp){
				String s;
				if(temp==-1)
					s="Peer "+peer_Id+" has no optimistically unchocked neighbor";
				else s="Peer "+peer_Id+" has the optimistically unchocked neighbor "+neighborPeers.get(temp).getPeerId();
				logger.writeLog(getTime(), s);
			}
			if(temp==-1){
				if(optimesticUnchokedPeerIndex!=-1 && !isPreferred[optimesticUnchokedPeerIndex]){
					peersConnections[optimesticUnchokedPeerIndex].sendChockMessage();;
					isUnchocked[optimesticUnchokedPeerIndex]=false;
					optimesticUnchokedPeerIndex=temp;
				}

			}
			else{
				if(optimesticUnchokedPeerIndex==-1){
					peersConnections[temp].sendUnChockMessage();; 
					optimesticUnchokedPeerIndex=temp;
					isUnchocked[temp]=true;
				}
				else if(optimesticUnchokedPeerIndex!=temp){
					if(!isPreferred[optimesticUnchokedPeerIndex]){
						isUnchocked[optimesticUnchokedPeerIndex]=false;
						peersConnections[optimesticUnchokedPeerIndex].sendChockMessage();;						
					}
					isUnchocked[temp]=true;
					peersConnections[temp].sendUnChockMessage();; 
					optimesticUnchokedPeerIndex=temp;
				}
			}

		}
	}
	public void increaseDownloadRate(int peerIndex) {
		downloadRate[peerIndex]++;
	}

	public int getNumberOfPeers() {
		return numberOfPeers;
	}

	public int getNumberOfPieces() {
		return numberOfPieces;
	}

	public int getNumberOfPreferredPeers() {
		return NumberOfPreferredPeers;
	}

	public int getUnchokingInterval() {
		return UnchokingInterval;
	}

	public int getOptimisticUnchokingInterval() {
		return OptimisticUnchokingInterval;
	}

	public String getFileName() {
		return fileName;
	}

	public long getFileSize() {
		return fileSize;
	}

	public long getPieceSize() {
		return pieceSize;
	}

	public int[] getDownloadRate() {
		return downloadRate;
	}

	public boolean[] getIsInterested() {
		return isInterested;
	}

	public boolean[] getIsPreferred() {
		return isPreferred;
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

	public Vector<PeerInfo> getNeighborPeers() {
		return neighborPeers;
	}

	public PeerInfo getMyself() {
		return myself;
	}

	public int getIndex() {
		return index;
	}

	public ServerSocket getServer() {
		return server;
	}

	public synchronized boolean havePiece(long pieceNumber) {
		int byteIndex = (int) Math.floor(pieceNumber/8.0);
		return getBitValue(currentBitField[byteIndex],  pieceNumber);
	}

	public synchronized boolean getBitValue(byte b, long pieceNumber) {
		int bitIndex = (int) (pieceNumber % 8);
		return (b & (1 << (7-bitIndex))) != 0;
	}
	public void setPeerBitField(int peerIndex, byte[] bitField){
		neighborsBitFields[peerIndex] = bitField;
	}

	public synchronized void pieceReceived(long pieceIndex) {
		int byteIndex = (int) Math.floor(pieceIndex/8.0);
		int bitIndex = (int) (pieceIndex % 8);
		currentBitField[byteIndex] |= (1 <<(7-bitIndex));
		
	}

	public synchronized void updatePeerBitField(int peerIndex, long pieceIndex) {
		int byteIndex = (int) Math.floor(pieceIndex/8.0);
		int bitIndex = (int) (pieceIndex % 8);
		neighborsBitFields[peerIndex][byteIndex] |= (1 << (7-bitIndex));
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
	public boolean getIfHavePieces(){
		boolean re=false;
		for(int i=0;i<this.currentBitField.length;i++){
			if(this.currentBitField[i]!=0){
				re=true;
				break;
			}
		}
		return re;
	}

	public void splitFile(File f, String name) throws IOException {
		int partCounter = 0;
		int sizeOfFiles = (int) pieceSize;
		byte[] buffer = new byte[sizeOfFiles];

		try (FileInputStream bis = new FileInputStream(f)) {//try-with-resources to ensure closing stream
			String pieceName=name+"/"+String.valueOf(partCounter++);

			int tmp = 0;
			while ((tmp = bis.read(buffer)) != -1) {
				//write each chunk of data into separate file with different number in name
				File newFile = new File(pieceName);
				try (FileOutputStream out = new FileOutputStream(newFile)) {
					out.write(buffer, 0, tmp);//tmp is chunk size
					out.close();
				}
				 pieceName=name+"/"+String.valueOf(partCounter++);
			}
			bis.close();
		}
	}
	
	 public static byte decodeBinaryString(String byteStr) {
	    	int re, len;
	    	if (null == byteStr) {
	    		return 0;
	    	}
	    	len = byteStr.length();
	    	if (len != 4 && len != 8) {
	    		return 0;
	    	}
	    	if (len == 8) {// 8 bit
	    		if (byteStr.charAt(0) == '0') {// positive
	    			re = Integer.parseInt(byteStr, 2);
	    		} else {// negitive
	    			re = Integer.parseInt(byteStr, 2) - 256;
	    		}
	    	} else {// 4 bit
	    		re = Integer.parseInt(byteStr, 2);
	    	}
	    	return (byte) re;
	    }
	    
	    public static String byteToBit(byte b) {  
	        return ""  
	                + (byte) ((b >> 7) & 0x1) + (byte) ((b >> 6) & 0x1)  
	                + (byte) ((b >> 5) & 0x1) + (byte) ((b >> 4) & 0x1)  
	                + (byte) ((b >> 3) & 0x1) + (byte) ((b >> 2) & 0x1)  
	                + (byte) ((b >> 1) & 0x1) + (byte) ((b >> 0) & 0x1);  
	    }  
	    
	    public String getFolderName(){
	    	return this.folder_name;
	    }
	    public static String getPeerId(){
	    	return peer_Id;
	    }
	    public static String getTime(){
	    	SimpleDateFormat   sDateFormat   =   new   SimpleDateFormat("yyyy-MM-dd   hh:mm:ss");     
	    	String   date   =   sDateFormat.format(new   java.util.Date());  
	    	return date;
	    }
	    
	    public int getNumberPieceHave() {
			return numberPieceHave;
		}

		public void incrementNumberPieceHave() {
			this.numberPieceHave++;
			//check if the file is completelt downloaded?? i.e. we have all pieces
			if(numberPieceHave==numberOfPieces)
				myself.setCompleteFile(true);
		}
		public void sendHaveMessage(int connectionIndex, byte[] piece_index) {
			for (int i = 0; i < piece_index.length; i++) {
				if(i != connectionIndex) {
					peersConnections[i].sendHaveMessage(piece_index);
				}
			}
		}
}
