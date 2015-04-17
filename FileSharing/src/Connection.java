import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class Connection {

	// peer id written in configuration file, only used writing log file
	PeerInfo peerInfo;
	// the index of the peer in the Peer Node
	int index;
	// reference to the peernode in order to update the download rate
	PeerProcess currentNode;

	// this will hold tcp interactions
	Socket tcpConnection;

	private OutputStream outStream;
	private InputStream inStream;

	private String handShakeMessage;
	private byte[] data;


	private byte[] peerBitField;

	private boolean interested;
	private int numberofPieces;
	
	//if chocked then false, else it is true
	private boolean sendRequest;
	
	//if send request wait for apiece to be downloaded completely until request another piece
	private boolean waitForPiece;
	
//	private ArrayList<String> mesgBuffer; // a buffer to store the msg than needs to be send

	private Logger logger;
	/**
	 * constructor, inits the parameters
	 * 
	 * @param id
	 * @param indx
	 * @param nodeReference
	 */
	public Connection(PeerInfo info, int indx, PeerProcess nodeReference) {
		this.peerInfo = info;
		this.index = indx;
		this.currentNode = nodeReference;
		handShakeMessage = "P2PFILESHARINGPROJ";
		interested = false;
		numberofPieces = currentNode.getNumberOfPieces();
		sendRequest = false;
		waitForPiece = false;
		logger  = Logger.getInstance();
	}

	/**
	 * starts the tcp connection to the neighbors with the given peerId start
	 */
	public void connect() {

		try {
			tcpConnection = new Socket(peerInfo.getHostname(),Integer.parseInt(peerInfo.getPortnumber()));
			init();
			// send the handshake message
			sendHandShakeMessage();
			boolean connected = waitForHandShakeMessage();
			if (connected) {
				listen();
				start();
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}// machine name, port number

	}

	/**
	 * initialize data, variables listening on this connection
	 */
	public void init() {
		// maximum number of bytes any peer can send is (4-bytes message length
		// + 1-byte message type + piece size
	//	data = new byte[(int) (currentNode.getPieceSize() + 5)];
		try {
			inStream = tcpConnection.getInputStream();
			outStream = tcpConnection.getOutputStream();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * start sending bit field, interested,......
	 */
	public void start() {
		try {
			// send current bit field message
			// message length = number of bytes of bit field byte array + 1 byte
			// for message type
           /*	If I have pieces, I need to send bitfield	message	 by Yuanfang*/
			byte[] packet;
			if(this.currentNode.getIfHavePieces()){
//				 messageLength= (int) Math
//						.ceil(numberofPieces / 8.0) + 1;
				Message bitFieldMessage = new Message();
				packet = bitFieldMessage.packMessage(
						Message.MessageType.BITFIELD, currentNode.getCurrentBitField());
				outStream.write(packet);
			}
			
			while (!currentNode.getMyself().isCompleteFile()) {
				// wait for bitfield message from connected peer
				// if not interested, then no need to continue
				while (!sendRequest || waitForPiece || !interested)
					;
				
				
				// try to get pieces of the file, send requests
				// get a piece that is not requested and do not exist in current
				// node bit field and also exists in the connected peer bit
				// field
				int i;
				boolean found = false;
				for (i = 0; i < numberofPieces; i++) {
					if (currentNode.getBitValue(peerBitField[i / 8], i)
							&& !currentNode.havePiece(i) && !currentNode.getIsRequested(i)) {
						found = true;
						break;
					}
				}
				if(found) {
					//send request message with the piece index
					byte[]pieceIndex;
					pieceIndex=String.format("%4d", i).getBytes();
					//1 type + 4 piece index
					Message request = new Message();
					packet = request.packMessage(Message.MessageType.REQUEST, pieceIndex);
					outStream.write(packet);
					waitForPiece = true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void listen() {
		new Thread(new Runnable() {
			public void run() {
				startListening();
			}
		}).start();
	}

	private void startListening() {
		byte[] piece_index=new byte[4];
		byte[] msg_len=new byte[4];
		int pieceIndex;
		while (true) {
			try {
				this.inStream.read(msg_len);
				int len=Integer.parseInt(new String(msg_len));
				data = new byte[len];
				inStream.read(data);
				Message message = new Message();
				message.unpack(data);
				switch (message.getMessageType()) {
				case CHOKE:
					sendRequest = false;
					logger.writeLog(PeerProcess.getTime() ,  "Peer " + currentNode.getMyself().getPeerId() + " is choked by " + peerInfo.getPeerId());
					
					break;
				case UNCHOKE:
					sendRequest = true;
					logger.writeLog(PeerProcess.getTime() ,  "Peer " + currentNode.getMyself().getPeerId() + " is unchoked by " + peerInfo.getPeerId());
					
					break;
				case INTERESTED:
					//update currentNode
					logger.writeLog(PeerProcess.getTime(),  "Peer " + currentNode.getMyself().getPeerId() + " received the \'interested\' message from " + peerInfo.getPeerId() + " for the piece");
					
					currentNode.setIntereseted(index);
					break;
				case NOT_INTERESTED:
					logger.writeLog(PeerProcess.getTime(), "Peer " + currentNode.getMyself().getPeerId() + " received the \'not interested\' message from " + peerInfo.getPeerId() + " for the piece");
					
					currentNode.unSetIntereseted(index);
					break;
				case HAVE:
					System.arraycopy(data,1 , piece_index, 0, 4);
					pieceIndex = Integer.parseInt(new String(piece_index));
					logger.writeLog(PeerProcess.getTime() , "Peer " + currentNode.getMyself().getPeerId() + " received the \'have\' message from " + peerInfo.getPeerId() + " for the piece" + pieceIndex);
					
					currentNode.updatePeerBitField(index, pieceIndex);
					//send interested or not interested message
					Message interestedMessage = new Message();
					Message.MessageType interestedMessageType = Message.MessageType.NOT_INTERESTED;
					
					if(!currentNode.havePiece(pieceIndex)) 
						interestedMessageType = Message.MessageType.INTERESTED;
					byte[] messageToSend = interestedMessage.packMessage(interestedMessageType,
							null);
					outStream.write(messageToSend);
					interested = true;
					break;
				case BITFIELD:
					// set peer bit field
					peerBitField = message.getMessagePayload();
					currentNode.setPeerBitField(index, peerBitField);
					// check if the current peer has bit fields that not exist
					// current node then send interested message
					
					Message interestMessage = new Message();
					Message.MessageType type = Message.MessageType.NOT_INTERESTED;
					for (int i = 0; i < numberofPieces; i++) {
						if (currentNode.getBitValue(peerBitField[i / 8], i)
								&& !currentNode.havePiece(i)) {//just have piece?
							interested = true;
							type = Message.MessageType.INTERESTED;
							break;
						}
					}
					byte[] packetToSend = interestMessage.packMessage(type,
							null);
					outStream.write(packetToSend);
					break;
				case REQUEST:
					if(currentNode.isPrefered(index)||currentNode.isOptimesticUnchockedNeighbor(index)) {
						//send the piece 
						// read the first 4-byte piece index
						System.arraycopy(data,1 , piece_index, 0, 4);
						pieceIndex = Integer.parseInt(new String(piece_index));
						if(currentNode.havePiece(pieceIndex)) {
							//send the piece, read partial file and send it
							byte[] buffer = new byte[(int) currentNode.getPieceSize()];
							InputStream reader = new FileInputStream(new File(currentNode.getFolderName() + "\\" + pieceIndex));
							reader.read(buffer);
							reader.close();
							byte[] toSend = new byte[buffer.length + 4];
							System.arraycopy(buffer, 0, toSend, 4, buffer.length);
							//add pieceIndex to to send
							System.arraycopy(piece_index, 0, toSend, 0, 4);
							Message pieceMessage = new Message();
							byte[] packet = pieceMessage.packMessage( Message.MessageType.PIECE, toSend);
							outStream.write(packet);
						}
					}
					break;
				case PIECE:
					waitForPiece = false;
					// read the first 4-byte piece index
					System.arraycopy(data,1 , piece_index, 0, 4);
					pieceIndex = Integer.parseInt(new String(piece_index));
					currentNode.pieceReceived(pieceIndex);
					currentNode.increaseDownloadRate(index);
					//download the piece
					OutputStream writer = new FileOutputStream(new File(currentNode.getFolderName() + "\\" + pieceIndex));
					byte[] buffer = new byte[(int) currentNode.getPieceSize()];
					System.arraycopy(message.getMessagePayload(), 4, buffer, 0, buffer.length);
					writer.write(buffer);
					writer.close();
					currentNode.incrementNumberPieceHave();
					logger.writeLog(PeerProcess.getTime() ,  "Peer " + currentNode.getMyself().getPeerId() + " has downloaded the piece "  + pieceIndex + " from " + peerInfo.getPeerId() + ". Now the number of pieces it has is " + currentNode.getNumberPieceHave());
					if(currentNode.getMyself().isCompleteFile())
						logger.writeLog(PeerProcess.getTime() ,  "Peer " + currentNode.getMyself().getPeerId() + " has downloaded the complete file");
					
					// send have message as bit field changes
					//send request message with the piece index
					//1 type + 4 piece index
					//should be send others peer, so many message here
					//also look for neighborbit field decide whether should send "NotInterested"
					currentNode.sendHaveMessage(index, piece_index);
					break;
				default:
					System.out.println("Undefined message received");
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void setTcpConnection(Socket tcpConnection) {
		this.tcpConnection = tcpConnection;
		init();
		// wait for the handShake message
		boolean connected = waitForHandShakeMessage();
		if (connected) {
			sendHandShakeMessage();
			listen();
			start();
		}
	}

	private void sendHandShakeMessage() {

		try {
			String header = handShakeMessage + "0000000000"
					+ currentNode.getMyself().getPeerId();
			outStream.write(header.getBytes());
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean waitForHandShakeMessage() {
		byte[] handshake = new byte[32];
		try {
			inStream.read(handshake);
			String message = new String(handshake);
			if (message.startsWith(handShakeMessage)) {
				String id = message.substring(29);
				/*Here we need to check if the peer id is the expected one by Yuanfang*/
				if(id.equals(this.currentNode.getNeighborPeers().get(index).getPeerId())){
					peerInfo = new PeerInfo(id);
					return true;
				}
				else return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

/*	private long getPieceIndex(byte[] payload) {
		Long result = 0L;
		for (int i = 0; i < 4; i++) {
			result = (result << 8) + (payload[i] & 0xff);
		}
		return (long) (Math.log(result) / Math.log(2));
	}*/

/*	private void pieceIndexToByte(long pieceIndex, byte[] payload) {
		long pieceIndexValue = (long) Math.pow(2, pieceIndex);
		for (int i = 0; i < 4; i++) {
			payload[3 - i] = (byte) (0xff & (pieceIndexValue >> 8 * i));
			;
		}
	}*/
	
	public  void sendChockMessage(){
		Message request = new Message();
		byte[] packet = request.packMessage(Message.MessageType.CHOKE, null);
		try {
			outStream.write(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void sendUnChockMessage(){
		Message request = new Message();
		byte[] packet = request.packMessage(Message.MessageType.UNCHOKE, null);
		try {
			outStream.write(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendHaveMessage(byte[] piece_index) {
		Message request = new Message();
		byte[] packet = request.packMessage( Message.MessageType.HAVE, piece_index);
		try {
			outStream.write(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
/**
	public static void main(String[] args) {
//		byte[] bytes = new byte[] { (byte) 0x2F, (byte) 0x01, (byte) 0x10,
//				(byte) 0x6F };
		byte[] bytes = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00 };
		Connection connection = new Connection(null, 0, null);
		System.out.println(connection.getPieceIndex(bytes));
		long number = connection.getPieceIndex(bytes);
		byte[] bytess = new byte[4];
		connection.pieceIndexToByte(number, bytess);
		for (int i = 0; i < bytess.length; i++) {
			System.out.println(bytess[i]);
		}
	}*/
}
