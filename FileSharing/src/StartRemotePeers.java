import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class StartRemotePeers{
	public static void main(String args){
		String path=System.getProperty("user.dir");
		
		//read peer info
		String st;
		try {
			BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
			while((st = in.readLine()) != null) {
				
				 String[] tokens = st.split("\\s+");
				 String peerId=tokens[0];
				 String hostname=tokens[1];
				 
				 System.out.println("Start remote peer " + peerId +  " at " + hostname );
					
					// *********************** IMPORTANT *************************** //
					// If your program is JAVA, use this line.
			     Runtime.getRuntime().exec("ssh " + hostname + " cd " + path + "; java PeerProcess " + peerId);
			
			}
			
			in.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}