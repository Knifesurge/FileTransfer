
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
        int read = 0;
        int total = 0;
        boolean success;
        byte[] tmp = new byte[1024 * 4];    // Set up a 4 KB buffer
        byte[] buf;
        try {
            FileInputStream fis = new FileInputStream(userFile);
            while ((read = fis.read(tmp)) > 0) {
                buf = Arrays.copyOf(tmp, read);
                total += read;
                success = sendChunkAndVerifyACK(buf, read, this.sequenceNumber);
               
                // Check we received proper ACK, resend until we do or until we 
                // resend the packet 10 times
                if (success) {
                    numInOrderPackets++;
                } else {
                    numInOrderPackets = 0;
                    while (!success && resendCount < 10) {
                        success = sendChunkAndVerifyACK(buf, read, this.sequenceNumber);
                        resendCount++;
                    }
                }
                // Swap to other sequence number
                this.sequenceNumber = this.sequenceNumber == 0 ? 1 : 0;
            }
            // Send EOT to Receiver
            buf = new byte[] {-1};
            packet = new DatagramPacket(buf, buf.length, receiverAddr, dataPort);
            success = sendChunkAndVerifyACK(buf, buf.length, -1);
            if (success) {
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.valueOf(total);
    }
    
    @Override
    protected void done() {
        try {
            socket.close();
        } catch (Exception ignored) {}
    }
    
    public boolean testAlive() {
        try {
            // Send packet to Receiver, wait for ACK with timeout of 0.5 s (500 ms)
            socket.setSoTimeout(500_000);
            byte[] buf = {0};
            packet = new DatagramPacket(buf, buf.length, receiverAddr, dataPort);
            socket.send(packet);
            // Get an ACK response or timeout
            receive();
            byte[] data = packet.getData();
            if (data[0] == 0) {
                // Reset socket timeout
                socket.setSoTimeout(timeout / 1000);
                return true;
            }
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
        send(buffer, length);
        receive();
        return packet.getData()[0] == expectedSeqNum;
    }
    
    public void send(byte[] buffer, int length) throws IOException {
        // Send the provided bytes via packet
        packet = new DatagramPacket(buffer, length, receiverAddr, dataPort);
        socket.send(packet);
    }
    
    public void receive() throws IOException, SocketTimeoutException {
        byte[] buf = new byte[1024*10];
        packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
    }
    
    public boolean isConnected() { return socket.isConnected(); }
    public void setFile(File userFile) { this.userFile = userFile; }
    
}
