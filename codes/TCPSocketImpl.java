import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Random;
import java.io.*;

enum State{
    TRANSFER , FIN_WAIT_1, FIN_WAIT_2, CLOSE_WAIT, TIMED_WAIT, CLOSED ;
}

public class TCPSocketImpl extends TCPSocket {
    private int destinationPort;
    private String destinationIP = Config.destinationIP;
    private EnhancedDatagramSocket enSocket;
    private State currState;
    private int sourcePort;
    private int currSeqNum;
    private ArrayList<Packet> buffer = new ArrayList<>();
    private long nextToWriteOnFile = 0;
    private BufferedWriter writer;
    private FileInputStream reader;
    private ArrayList<DatagramPacket> sendData = new ArrayList<>();
    private int cwnd;
    private int ackedSeqNum;
    private int numDupAck;

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        this.sourcePort = port;
        this.enSocket = new EnhancedDatagramSocket(port);
        this.currState = State.TRANSFER;
        this.currSeqNum = 0;
    }

    public void setDestinationPort(int destinationPort){
        this.destinationPort = destinationPort;
    }

    public void retransmitPacket(int retransmitSeqNum){
        
    }

    @Override
    public void send(String pathToFile) throws Exception{
        File file = new File(pathToFile);
        this.reader = new FileInputStream(file);
        byte[] chunk = new byte[Config.chunkSize];
        int chunkLen = 0;
        this.currSeqNum = 0;
        this.ackedSeqNum = 0;
        this.numDupAck = 0;
        while ((chunkLen = reader.read(chunk)) != -1) {
            while(currSeqNum <= this.ackedSeqNum +this.cwnd + this.numDupAck) {
                this.currSeqNum ++;
                Packet sendPacket = new Packet("0", "0", "0", this.sourcePort, this.destinationPort, 0, this.currSeqNum, "", 0);
                DatagramPacket sendDatagramPacket = sendPacket.convertToDatagramPacket(this.destinationPort, this.destinationIP);
                sendData.add(sendDatagramPacket);
                this.enSocket.send(sendDatagramPacket);
            }
            byte[] msg = new byte[Config.maxMsgSize];
            DatagramPacket ackDatagram = new DatagramPacket(msg, msg.length);
            Packet ackPacket = new Packet(new String(msg));
            if(ackPacket.getAckNumber() == (this.ackedSeqNum + 1) ){
                this.numDupAck ++;
                retransmitPacket(this.ackedSeqNum + 1);
            }
            else{
                this.cwnd ++;
            }

        }

        throw new RuntimeException("Not implemented!");
    }

    public int sendSyn(DatagramPacket synDatagramPacket ) throws Exception {
        while(true){
            this.enSocket.send(synDatagramPacket);
            this.enSocket.setSoTimeout(1000);

            byte[] msg = new byte[Config.maxMsgSize];
            DatagramPacket synAckDatagramPacket = new DatagramPacket(msg, msg.length);
            Packet synAckPacket;
            while(true){
                try {
                    this.enSocket.receive(synAckDatagramPacket);
                    synAckPacket = new Packet(new String(msg));
                    if(!synAckPacket.getSynFlag().equals("1") || !synAckPacket.getAckFlag().equals("1"))
                        throw new Exception("This message is not SYN ACK");
                    this.destinationPort = synAckPacket.getSourcePort();
                    int rcvSeqNum = synAckPacket.getSeqNumber();
                    return rcvSeqNum;
                }
                catch (SocketTimeoutException e) {
                    // timeout exception.
                    System.out.println("Timeout reached!!! " + e);
                    break;
                }
            }
        }
    }

    @Override
    public void connect(String serverIP, int serverPort) throws Exception {

        Packet synPacket = new Packet("0", "1", "0", Config.senderPortNum, Config.receiverPortNum, 0, 0, "", 0);
        DatagramPacket synDatagramPacket = synPacket.convertToDatagramPacket(serverPort, serverIP);
        int rcvSeqNum = sendSyn(synDatagramPacket);


        Packet ackPacket = new Packet("1", "0", "0", Config.senderPortNum, Config.receiverPortNum, rcvSeqNum + 1, 0, "", 0);
        DatagramPacket ackDatagramPacket = ackPacket.convertToDatagramPacket(Config.receiverPortNum, serverIP);

        for(int i=0; i<7; i++)
            this.enSocket.send(ackDatagramPacket);

    }

    private boolean checkIfAckOrSyn(Packet receivedPacket) {
        return (receivedPacket.getAckFlag().equals("1") || receivedPacket.getSynFlag().equals("1"));
    }

    private void writeToFile(Packet newPacket) throws Exception{
        String data = newPacket.getData();
        this.writer.write(data);
    }

    private void addAllValidPacketsToFile(String pathToFile) throws Exception{
        while(buffer.get(0).getSeqNumber() == nextToWriteOnFile) {
            writeToFile( buffer.get(0));
            nextToWriteOnFile ++;
            buffer.remove(0);
        }
    }

    private void sendAck(int rcvSeqNum) throws Exception {
        Packet ackPacket = new Packet("1", "0", "0", sourcePort, this.destinationPort, rcvSeqNum + 1, this.currSeqNum, "", 0);
        DatagramPacket ackDatagramPacket = ackPacket.convertToDatagramPacket(destinationPort, destinationIP);
        this.enSocket.send(ackDatagramPacket);
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        FileWriter newFile = new FileWriter(pathToFile);
        newFile.close();
        this.writer = new BufferedWriter(new FileWriter(pathToFile, true));

        while(this.currState != State.CLOSE_WAIT) {
            byte[] msg = new byte[Config.maxMsgSize];
            DatagramPacket receivedDatagram = new DatagramPacket(msg, msg.length);
            Packet receivedPacket = new Packet(new String(msg));
            int rcvSeqNum = receivedPacket.getSeqNumber();
            if(checkIfAckOrSyn(receivedPacket))
                continue;
            if (receivedPacket.getFinFlag().equals("1")) {
                this.currState = State.CLOSE_WAIT;
                this.currSeqNum++;
                Packet finAckPacket = new Packet("1", "0", "0", sourcePort, this.destinationPort, rcvSeqNum + 1, this.currSeqNum, "", 0);
                DatagramPacket finAckDatagramPacket = finAckPacket.convertToDatagramPacket(this.destinationPort, destinationIP);
                this.enSocket.send(finAckDatagramPacket);
                continue;
            }
            sendAck(receivedPacket.getSeqNumber());
            if(receivedPacket.getSeqNumber() == nextToWriteOnFile) {
                writeToFile(receivedPacket);
                nextToWriteOnFile++;
                addAllValidPacketsToFile(pathToFile);
            }
            else if(receivedPacket.getSeqNumber() > buffer.get(buffer.size() - 1).getSeqNumber())
                buffer.add(receivedPacket);
        }
//        byte[] msg = new byte[Config.maxMsgSize];
//        DatagramPacket newDatagramPacket = new DatagramPacket(msg, msg.length);
//        while((this.currState != State.CLOSED) &(this.currState != State.CLOSE_WAIT)) {
//            this.enSocket.receive(newDatagramPacket);
//            Packet newPacket = new Packet(new String(msg));
//            int rcvSeqNum = newPacket.getSeqNumber();
//            if (newPacket.getFinFlag().equals("1")) {
//                if (this.currState == State.FIN_WAIT_2) {
//                    this.currState = State.TIMED_WAIT;
//
//                    this.currSeqNum++;
//                    Packet synPacket = new Packet("1", "0", "0", sourcePort, this.destinationPort, rcvSeqNum + 1, this.currSeqNum, "", 0);
//                    DatagramPacket synDatagramPacket = synPacket.convertToDatagramPacket(destinationPort, destinationIP);
//                    for (int i = 0; i < 7; i++)
//                        this.enSocket.send(synDatagramPacket);
//                    this.currState = State.CLOSED;
//
//                } else {
//                    this.currState = State.CLOSE_WAIT;
//
//                    this.currSeqNum++;
//                    Packet synPacket = new Packet("1", "0", "0", sourcePort, this.destinationPort, rcvSeqNum + 1, this.currSeqNum, "", 0);
//                    DatagramPacket synDatagramPacket = synPacket.convertToDatagramPacket(this.destinationPort, destinationIP);
//                    this.enSocket.send(synDatagramPacket);
//                }
//            }
//        }
        this.writer.close();
    }


    @Override
    public void close() throws Exception {
        byte[] msg = new byte[Config.maxMsgSize];
        this.currSeqNum ++;
        Packet closePacket = new Packet("0", "0", "1", Config.senderPortNum, Config.receiverPortNum, 0, this.currSeqNum, "", 0);
        DatagramPacket closeDatagramPacket = closePacket.convertToDatagramPacket(this.destinationPort, this.destinationIP);
        while(true){
            this.enSocket.send(closeDatagramPacket);
            this.currState = State.FIN_WAIT_1;
            this.enSocket.setSoTimeout(1000);

            while (true){
                try{
                    DatagramPacket ackDatagramPacket = new DatagramPacket(msg, msg.length);
                    this.enSocket.receive(ackDatagramPacket);
                    Packet ackPacket = new Packet(new String(msg));
                    if(!ackPacket.getAckFlag().equals("1"))
                        throw new Exception("This message is not ACK");
                    if(ackPacket.getAckNumber() != (this.currSeqNum + 1) )
                        //TODO AFTER RECEIVE
                        throw new Exception("This message is not my ACK -- WILL CHANGE AFTER IMPLEMENTATION OF RECEIVE");
                    this.currState = State.FIN_WAIT_2;
                    this.receive("");
                }
                catch (SocketTimeoutException e) {
                    // timeout exception.
                    System.out.println("Timeout reached!!! " + e);
                    break;
                }
            }

        }
    }

    @Override
    public long getSSThreshold() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getWindowSize() {
        throw new RuntimeException("Not implemented!");
    }
}
