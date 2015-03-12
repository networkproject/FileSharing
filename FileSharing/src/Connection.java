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

	private boolean bitFieldReceived;

	private byte[] peerBitField;

	private boolean interested;
	private int numberofPieces;
	
	//if chocked then false, else it is true
	private boolean sendRequest;
	
	//if send request wait for apiece to be downloaded completely until request another piece
	private boolean waitForPiece;

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
		bitFieldReceived = false;
		handShakeMessage = "P2PFILESHARINGPROJ";
		interested = false;
		numberofPieces = currentNode.getNumberOfPieces();
		sendRequest = false;
		waitForPiece = false;
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
		data = new byte[(int) (currentNode.getPieceSize() + 5)];
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
			int messageLength = (int) Math
					.floor(numberofPieces / 8) + 1;
			Message bitFieldMessage = new Message();
			byte[] packet = bitFieldMessage.packMessage(messageLength,
					Message.MessageType.BITFIELD, currentNode.getCurrentBitField());
			outStream.write(packet);
			while (!currentNode.getMyself().isCompleteFile()) {
				// wait for bitfield message from connected peer
				// if not interested, then no need to continue
				while (!bitFieldReceived || !sendRequest || waitForPiece || !interested)
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
					byte[]pieceIndex = new byte[4];
					pieceIndexToByte(i, pieceIndex);
					//1 type + 4 piece index
					messageLength = 5;
					Message request = new Message();
					packet = request.packMessage(messageLength, Message.MessageType.REQUEST, pieceIndex);
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
		long pieceIndex;
		while (true) {
			try {
				data = new byte[(int) (currentNode.getPieceSize() + 5)];
				inStream.read(data);
				Message message = new Message();
				message.unpack(data);
				switch (message.getMessageType()) {
				case CHOKE:
					sendRequest = false;
					break;
				case UNCHOKE:
					sendRequest = true;
					break;
				case INTERESTED:
					//update currentNode
					currentNode.setIntereseted(index);
					break;
				case NOT_INTERESTED:
					currentNode.unSetIntereseted(index);
					break;
				case HAVE:
					pieceIndex = getPieceIndex(message.getMessagePayload());
					//update bit field of current connected peer
					currentNode.updatePeerBitField(index, pieceIndex);
					//send interested or not interested message
					Message interestedMessage = new Message();
					Message.MessageType interestedMessageType = Message.MessageType.NOT_INTERESTED;
					
					if(!currentNode.havePiece(pieceIndex)) 
						interestedMessageType = Message.MessageType.INTERESTED;
					byte[] messageToSend = interestedMessage.packMessage(1, interestedMessageType,
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
								&& !currentNode.havePiece(i)) {
							interested = true;
							type = Message.MessageType.INTERESTED;
							break;
						}
					}
					byte[] packetToSend = interestMessage.packMessage(1, type,
							null);
					outStream.write(packetToSend);
					bitFieldReceived = true;
					break;
				case REQUEST:
					if(currentNode.isPrefered(index)||currentNode.isOptimesticUnchockedNeighbor(index)) {
						//send the piece 
						// read the first 4-byte piece index
						pieceIndex = getPieceIndex(message.getMessagePayload());
						if(currentNode.havePiece(pieceIndex)) {
							//send the piece, read partial file and send it
							byte[] buffer = new byte[(int) currentNode.getPieceSize()];
							InputStream reader = new FileInputStream(new File(currentNode.getMyself().getPeerId() + "/" + pieceIndex));
							reader.read(buffer);
							reader.close();
							byte[] toSend = new byte[buffer.length + 4];
							System.arraycopy(buffer, 0, toSend, 4, buffer.length);
							Message pieceMessage = new Message();
							byte[] packet = pieceMessage.packMessage(toSend.length + 1, Message.MessageType.PIECE, toSend);
							outStream.write(packet);
						}
					}
					break;
				case PIECE:
					waitForPiece = false;
					// read the first 4-byte piece index
					pieceIndex = getPieceIndex(message.getMessagePayload());
					currentNode.pieceReceived(pieceIndex);
					currentNode.increaseDownloadRate(index);
					//download the piece
					OutputStream writer = new FileOutputStream(new File(currentNode.getMyself().getPeerId() + "/" + pieceIndex));
					byte[] buffer = new byte[(int) currentNode.getPieceSize()];
					System.arraycopy(message.getMessagePayload(), 4, buffer, 0, buffer.length);
					writer.write(buffer);
					writer.close();
					// send have message as bit field changes
					//send request message with the piece index
					byte[]pieceIndexField = new byte[4];
					pieceIndexToByte(pieceIndex, pieceIndexField);
					//1 type + 4 piece index
					int messageLength = 5;
					Message request = new Message();
					byte[] packet = request.packMessage(messageLength, Message.MessageType.HAVE, pieceIndexField);
					outStream.write(packet);
					
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
				String id = message.substring(message.lastIndexOf('0') + 1);
				peerInfo = new PeerInfo(id);
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private long getPieceIndex(byte[] payload) {
		Long result = 0L;
		for (int i = 0; i < 4; i++) {
			result = (result << 8) + (payload[i] & 0xff);
		}
		return (long) (Math.log(result) / Math.log(2));
	}

	private void pieceIndexToByte(long pieceIndex, byte[] payload) {
		long pieceIndexValue = (long) Math.pow(2, pieceIndex);
		for (int i = 0; i < 4; i++) {
			payload[3 - i] = (byte) (0xff & (pieceIndexValue >> 8 * i));
			;
		}
	}

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
	}
}
