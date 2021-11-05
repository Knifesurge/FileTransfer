
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Nicolas Mills (mill6100@mylaurier.ca)
 */
public class ReceiverTest {
    
    public static void main(String[] args) throws IOException {
        new ReceiverThread(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3]).start();
    }
    

    public static class ReceiverThread extends Thread {
        
        private DatagramSocket socket;
        private FileOutputStream out;
        private InetAddress senderAddr;
        private int dataPort;
        private int ACKPort;
        private String filename;
        private long tttMillis;
        
        public ReceiverThread(String senderIP, int dataPort, int ACKPort, String filename) throws IOException {
            super("ReceiverThread");
            this.dataPort = dataPort;
            this.ACKPort = ACKPort;
            this.filename = filename;
            socket = new DatagramSocket(dataPort);
            
            try {
                out = new FileOutputStream(filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                out = new FileOutputStream("received.txt");
            }
        }
        
        @Override
        public void run() {
            try {
                byte[] buf = new byte[1];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet); // Wait for first ISALIVE packet
                System.out.println("ISALIVE received from: " + 
                        packet.getAddress().getHostAddress() + 
                        ":" + packet.getPort());
                buf = new byte[] {packet.getData()[0]};
                senderAddr = packet.getAddress();
                packet = new DatagramPacket(buf, buf.length, senderAddr, ACKPort);
                System.out.println("Sending packet to " + senderAddr+":"+ACKPort);
                socket.send(packet);
                // Handshake completed
                // We now receive the file chunks as packets
                // until the packet has a sequence number of -1 (EOT)
                
                // Grab current time for total elapsed time
                long start = System.currentTimeMillis();
                int currOffset = 0;
                int currSequenceNum = 0;
                while (packet.getData()[0] != -1) {
                    // Set buffer to hold 4 KB of data + 1 for seq num
                    buf = new byte[(1024 * 4) + 1];
                    packet = new DatagramPacket(buf, buf.length);
                    // Receive a chunk (or partial chunk)
                    System.out.println("Waiting for next chunk...");
                    socket.receive(packet);
                    System.out.println("Received chunk, writing to file...");
                    // Write to file
                    //out.write(buf, currOffset, buf.length);
                    buf = Arrays.copyOfRange(packet.getData(), 1, packet.getLength());
                    out.write(buf);
                    currOffset += buf.length;
                    // Send ACK to Sender
                    buf = new byte[]{packet.getData()[0]};
                    packet = new DatagramPacket(buf, buf.length, senderAddr, ACKPort);
                    socket.send(packet);
                }
                System.out.println("Received EOT");
                // Received EOT packet, ACK it and print elapsed time
                buf = new byte[] {-1};
                packet = new DatagramPacket(buf, buf.length, senderAddr, ACKPort);
                socket.send(packet);
                System.out.println("ACK EOT");
                // Disconnect
                socket.close();
                System.out.println("Socket now closed");
                long end = System.currentTimeMillis();
                tttMillis = end - start;
                System.out.println("Total Transmission Time (TTT) in ms: " + tttMillis);
            } catch (IOException ex) {
                Logger.getLogger(ReceiverTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}