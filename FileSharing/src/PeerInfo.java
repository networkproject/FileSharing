
public class PeerInfo {
    private String peerId;
    private String hostname;
    private String portnumber;
    private boolean isCompleteFile;
    
	public PeerInfo(String peerId, String hostname, String portnumber,
			boolean isCompleteFile) {
		this.peerId = peerId;
		this.hostname = hostname;
		this.portnumber = portnumber;
		this.isCompleteFile = isCompleteFile;
	}

	public PeerInfo(String peerId) {
		this.peerId = peerId;
	}
	public String getPeerId() {
		return peerId;
	}

	public void setPeerId(String peerId) {
		this.peerId = peerId;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getPortnumber() {
		return portnumber;
	}

	public void setPortnumber(String portnumber) {
		this.portnumber = portnumber;
	}

	public boolean isCompleteFile() {
		return isCompleteFile;
	}

	public void setCompleteFile(boolean isCompleteFile) {
		this.isCompleteFile = isCompleteFile;
	}
    
    
}
