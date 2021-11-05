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
    public static void main(String argv[]) throws IOException {
        byte[] ack = "ACK".getBytes();
        InetAddress ip_sender = InetAddress.getByName(argv[0]);
        int port_reciever = Integer.parseInt(argv[1]);
        int port_ack = Integer.parseInt(argv[2]);
        String filename = argv[3];
        DatagramSocket rcv_socket = new DatagramSocket(port_reciever);
        byte[] tmp = new byte[1024 * 4]; 
        DatagramPacket rcv_packet = new DatagramPacket(tmp, tmp.length);
        DatagramPacket snd_packet = new DatagramPacket(ack, ack.length,ip_sender,port_ack);
        FileWriter rcv_file = new FileWriter(filename);
        

        while (true){
            try {
                rcv_socket.receive(rcv_packet);
                rcv_socket.send(snd_packet);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
           rcv_file.write(new String(tmp));


        }
    }
}
