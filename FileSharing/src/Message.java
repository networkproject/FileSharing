public class Message {

	private int messageLength;
	private MessageType messageType;
	private byte[] messagePayload;

	public Message() {

	}

	public enum MessageType {
		CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED, HAVE, BITFIELD, REQUEST, PIECE
	}

	/**
	 * this method takes the parameters and put them in a byte stream
	 * 
	 * @param msgLength
	 *            the message length
	 * @param type
	 *            the message type, ex. choke, have,...
	 * @param payload
	 *            the message body
	 * @return byte stream of the message to be send over tcp socket
	 */
	public byte[] packMessage(int msgLength, MessageType type, byte[] payload) {
		messageType = type;
		return null;
	}

	/**
	 * should set the message parameters(length, type, payload) by unpacking the
	 * byte array message stream
	 * 
	 * @param messageStream
	 *            the byte stream that comes from a peer over the socket
	 */
	public void unpack(byte[] messageStream) {

	}

	public int getMessageLength() {
		return messageLength;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public byte[] getMessagePayload() {
		return messagePayload;
	}

}
