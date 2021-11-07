
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

/**
 *
 * @author Nicolas Mills (mill6100@mylaurier.ca)
 */
public class Sender extends SwingWorker {
    
    // Socket used to send/receive packets
    private DatagramSocket socket;
    // Last packet received from the Receiver
    private DatagramPacket packet = null;
    private InetAddress receiverAddr;
    // Receiver port where data being sent
    private int dataPort;
    // Sender port (this) where receiver sends ACKs
    private int ACKPort;
    // Current sequence number
    private int sequenceNumber;
    // Number of in-order packets sent
    private int numInOrderPackets = 0;
    // The current File we are sending
    private File userFile;
    // Socket timeout in microseconds
    private int timeout;
    // If this instance has been set up
    private boolean isReady = false;
    // How many times a packet will be resent before Receiver considered dead
    private int resendCount = 10;
    // Whether we are in unreliable mode or not (resend every 10th packet)
    private boolean unreliableMode = false;
    // Number of packets sent
    private int numSentPackets = 0;
    // Label on GUI to update num sent in-order packets
    private JLabel numPacketsLabel;
    // Whether or not the Packet being sent is the first packet with data
    private boolean isFirstDataPacket = true;
    // Start time of transmission
    private int startTimeMillis;
    // End time of transmission
    private int endTimeMillis;
    
    public Sender() {
        socket = null;
    }
    
    public boolean isReady() {return isReady;}
    public void nowReady() {this.isReady = true;}
    
    public void setDataPort(int port) { this.dataPort = port; }
    public int getDataPort() { return this.dataPort; }
    public void setACKPort(int port) { this.ACKPort = port; }
    public int getACKPort() { return this.ACKPort; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public int getTimeout() { return this.timeout; } 
    public void setReceiverAddr(InetAddress rAddr) { this.receiverAddr = rAddr; }
    public InetAddress getReceiverAddr() { return this.receiverAddr; }
    public void setSocket() throws SocketException { this.socket = new DatagramSocket(ACKPort); socket.setSoTimeout(timeout/1000); }
    public boolean isUnreliable() { return this.unreliableMode; }
    public void setUnreliable(boolean b) { this.unreliableMode = b; }
    public void setLabel(JLabel label) { this.numPacketsLabel = label; }
    
    public Sender(InetAddress receiverAddr, int dataPort, int ACKPort, int timeout) throws SocketException {
        this.receiverAddr = receiverAddr;
        this.dataPort = dataPort;
        this.ACKPort = ACKPort;
        socket = new DatagramSocket(dataPort);
        socket.setSoTimeout(timeout / 1000);
        
        this.sequenceNumber = 0;
        this.numInOrderPackets = 0;
        this.timeout = timeout;
    }
    
    @Override
    protected String doInBackground() {
        // Num bytes read from file
        int readBytes = 0;
        int total = 0;
        boolean success;
        byte[] readTmp = new byte[1024];    // Set up a 1 KB buffer
        byte[] buf;
        byte[] tmp;
        try {
            FileInputStream fis = new FileInputStream(userFile);
            boolean reading = true;
            while (reading) {
                readBytes = fis.read(readTmp);
                if (readBytes < 0) {
                    reading = false;
                }
                // DEBUG
                System.out.println("Data: " + new String(readTmp));
                
                // Convert seq num to byte-string, then the actual byte value
                tmp = new byte[] {Byte.valueOf(Integer.toBinaryString(this.sequenceNumber))};
                // Create the buf array to hold [seq_num, data_array]
                buf = new byte[tmp.length + readTmp.length];    // tmp.length = 1

                // Copy tmp array into buf array
                System.arraycopy(tmp, 0, buf, 0, tmp.length);
                // Copy array of read bytes into buf at an offset of tmp.length = 1
                System.arraycopy(readTmp, 0, buf, tmp.length, readTmp.length);
                    
                total += readTmp.length;
                
                // Send the packet, grab ACK verification status
                System.out.println("Sending and waiting for ACK...");
                success = sendChunkAndVerifyACK(buf, readTmp.length, this.sequenceNumber);
                System.out.println(success ? "Proper ACK received..." : "Improper ACK, resend");
                
                // Check we received proper ACK, resend until we do or until we 
                // resend the packet 10 times
                if (success) {
                    numInOrderPackets++;
                } else {
                    while (!success && resendCount < 10) {
                        success = sendChunkAndVerifyACK(buf, readTmp.length, this.sequenceNumber);
                        resendCount++;
                    }
                    if (success)
                        numInOrderPackets++;
                }
                // Swap to other sequence number
                this.sequenceNumber = this.sequenceNumber == 0 ? 1 : 0;
            }
            
            // Finished transfer, send EOT to Receiver
            buf = new byte[] {-1};
            System.out.println("Sending EOT...");
            success = sendChunkAndVerifyACK(buf, buf.length, -1);
            if (success) {
                System.out.println("EOT ACKd, sending TTT then closing socket");
                endTimeMillis = (int) System.currentTimeMillis();
                int TTT = (int) (endTimeMillis - startTimeMillis);
                buf = new byte[] {Byte.valueOf(Integer.toBinaryString(TTT), 2)};
                System.out.println("TTT: " + TTT);
                packet = new DatagramPacket(buf, buf.length, receiverAddr, dataPort);
                socket.send(packet);
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.valueOf(total);
    }
    
    @Override
    protected void done() {
        try {
            numPacketsLabel.setText(String.valueOf(numInOrderPackets));
        } catch (Exception ignored) {}
    }
    
    public boolean testAlive() {
        try {
            // Send packet to Receiver, wait for ACK with timeout of 0.5 s (500 ms)
            socket.setSoTimeout(500_000);
            byte[] buf = {2};
            packet = new DatagramPacket(buf, buf.length, receiverAddr, dataPort);
            socket.send(packet);
            // Get an ACK response or timeout
            receive();
            // Response received, don't really need to check the ACK
            // Reset socket timeout
            socket.setSoTimeout(timeout / 1000);
            return true;
        } catch (SocketException ex) {
            Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SocketTimeoutException ex) {
            Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    private boolean sendChunkAndVerifyACK(byte[] buffer, int length, int expectedSeqNum) throws IOException, SocketTimeoutException {
        if (isFirstDataPacket) {
            isFirstDataPacket = false;
            startTimeMillis = (int) System.currentTimeMillis();
        }
        if (!unreliableMode) {
            send(buffer, length);
            receive();
        } else {    // In unreliable mode
            if (numSentPackets % 10 == 9) {     // Sending 10th packet
                try {
                    // Don't send every 10th packet, just timeout and wait for ACK
                    receive();
                } catch (SocketTimeoutException e) {
                    // Socket timed out, send the packet and receive the ACK
                    send(buffer, length);
                    receive();
                }     
            } else {
                send(buffer, length);
                receive();
            }
        }
        return packet.getData()[0] == expectedSeqNum;
    }
    
    public void send(byte[] buffer, int length) throws IOException {
        // Send the provided bytes via packet
        packet = new DatagramPacket(buffer, length, receiverAddr, dataPort);
        socket.send(packet);
    }
    
    public void receive() throws IOException, SocketTimeoutException {
        byte[] buf = new byte[1024];
        packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
    }
    
    public boolean isConnected() { return socket.isConnected(); }
    public void setFile(File userFile) { this.userFile = userFile; }
    
}
