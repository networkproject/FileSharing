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
	}

	/**
	 * starts the tcp connection to the neighbors with the given peerId start
	 */
	public void connect() {

		try {
			tcpConnection = new Socket(peerInfo.getHostname(),peerInfo.getPortnumber());
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
					.floor(currentNode.getPieceSize() / 8) + 1;
			Message bitFieldMessage = new Message();
			byte[] packet = bitFieldMessage.packMessage(messageLength,
					Message.MessageType.BITFIELD, currentNode.getCurrentBitField());
			outStream.write(packet);
			while (!currentNode.getMyself().isCompleteFile()) {
				// wait for bitfield message from connected peer
				while (!bitFieldReceived)
					;
				// if not interested, then no need to continue
				if (!interested)
					break;
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
		while (true) {
			try {
				inStream.read(data);
				Message message = new Message();
				message.unpack(data);
				switch (message.getMessageType()) {
				case CHOKE:

					break;
				case UNCHOKE:

					break;
				case INTERESTED:

					break;
				case NOT_INTERESTED:

					break;
				case HAVE:

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
					byte[] messageToSend = interestMessage.packMessage(1, type,
							null);
					outStream.write(messageToSend);
					bitFieldReceived = true;
					break;
				case REQUEST:

					break;
				case PIECE:
					// read the first 4-byte piece index
					long pieceIndex = getPieceIndex(message.getMessagePayload());
					currentNode.pieceReceived(pieceIndex);
					// send have message as bitfield changes
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
