import java.io.IOException;
import java.net.DatagramPacket;

public class Receiver {
    public static void main(String[] args) throws Exception {
        TCPServerSocketImpl tcpServerSocket = new TCPServerSocketImpl(Config.receiverPortNum);
        TCPSocket tcpSocket = tcpServerSocket.accept();

        tcpSocket.receive("../test.png");
        tcpSocket.close();
        System.out.println("DONE");
    }
}
