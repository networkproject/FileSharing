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
		byte[] message=new byte[4+1+payload.length];
		System.arraycopy(this.messageLengthToByte(msgLength), 0, message, 0, 4);
		switch(type) {
		case CHOKE:
			message[4]=0;
			break;
		case UNCHOKE:
			message[4]=1;
			break;
		case INTERESTED:
			message[4]=2;
			break;
		case NOT_INTERESTED:
			message[4]=3;
			break;
		case HAVE:
			message[4]=4;
			break;
		case BITFIELD:
			message[4]=5;
			break;
		case REQUEST:
			message[4]=6;
			break;
		case PIECE:
			message[4]=7;
		}
		System.arraycopy(payload, 0, message, 5, payload.length);
		return message;
	
	}

	/**
	 * should set the message parameters(length, type, payload) by unpacking the
	 * byte array message stream
	 * 
	 * @param messageStream
	 *            the byte stream that comes from a peer over the socket
	 */
	public void unpack(byte[] messageStream) {
         this.messageLength=(int)this.getMessageLength(messageStream);
         int typevalue=messageStream[4];
         switch(typevalue){
         case 0:
        	 this.messageType=MessageType.CHOKE;
        	 break;
         case 1:
        	 this.messageType=MessageType.UNCHOKE;
        	 break;
         case 2:
        	 this.messageType=MessageType.INTERESTED;
        	 break;
         case 3:
        	 this.messageType=MessageType.NOT_INTERESTED;
        	 break;
         case 4:
        	 this.messageType=MessageType.HAVE;
        	 break;
         case 5:
        	 this.messageType=MessageType.BITFIELD;
        	 break;
         case 6:
        	 this.messageType=MessageType.REQUEST;
        	 break;
         case 7:
        	 this.messageType=MessageType.PIECE;
        	 break;
         }
         System.arraycopy(messageStream, 5, this.messagePayload, 0, messageStream.length-5);
	}
	private byte[] messageLengthToByte(long messageLength ) {
		byte[] length = new byte[4];
		for (int i = 0; i < 4; i++) {
			length[3 - i] = (byte) (0xff & (messageLength >> 8 * i));
		}
		return length;
	}
	
	private long getMessageLength(byte[] payload) {
		Long result = 0L;
		for (int i = 0; i < 4; i++) {
			result = (result << 8) + (payload[i] & 0xff);
		}
		return result;
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
