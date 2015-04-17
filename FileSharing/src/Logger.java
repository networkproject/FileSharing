import java.io.FileWriter;
import java.io.IOException;

public class Logger {

	private static Logger instance = null;

	protected Logger() {
		// Exists only to defeat instantiation.
	}

	public static Logger getInstance() {
		if (instance == null) {
			instance = new Logger();
		}
		return instance;
	}

	public synchronized void writeLog(String time,String info) {
		FileWriter out = null;
		try {
			String currPath=System.getProperty("user.dir");
			out = new FileWriter(currPath + "log_peer_"+PeerProcess.getPeerId()+".log", true);
			out.write(time+": "+info+".");
		} catch (IOException e) {
			System.err.println(e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
