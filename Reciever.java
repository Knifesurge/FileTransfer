import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
public class Reciever {
    
    private static void send_ack(byte seq_num, InetAddress ip, int port, DatagramSocket rcv_socket) throws IOException{
	byte[] buffer = new byte[] {seq_num};
        DatagramPacket snd_packet = new DatagramPacket(buffer, buffer.length,ip,port);
        rcv_socket.send(snd_packet); 
    }
    
    public static void main(String argv[]) throws IOException {
        int seq_check = 0;
        InetAddress ip_sender = InetAddress.getByName(argv[0]);
        int port_reciever = Integer.parseInt(argv[1]);
        int port_ack = Integer.parseInt(argv[2]);
        String filename = argv[3];
        DatagramSocket rcv_socket = new DatagramSocket(port_reciever);
        byte[] tmp = new byte[1024]; 
        DatagramPacket rcv_packet = new DatagramPacket(tmp, tmp.length);
       
        FileWriter rcv_file = new FileWriter(filename);
        

        while (true){
            try {
                rcv_socket.receive(rcv_packet);
                
                send_ack(tmp[0],ip_sender,port_ack,rcv_socket);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (tmp[0] == -1) break; // remove if there are issues with timeout 
            if (seq_check == tmp[0]){
                seq_check = 1 -tmp[0];
		System.out.println("Tmp: " + new String(tmp));
                rcv_file.write(new String(tmp));
           }
        }
        rcv_file.close();
    }
}
