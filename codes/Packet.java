import java.io.*; 
import java.net.DatagramPacket;
  
class Packet implements java.io.Serializable 
{  
    private String ack_flag;
    private String syn_flag;
    private int source_port;
    private int destination_port; 
    private InetAddress destination_address;
    private String ack_number;
    private String seq_number;
    private String data;
    private byte[] buffer;
    private int offset;
    private int length;

    public Packet(String ack_flag, int syn_flag, String source_port, int destination_port, InetAddress destination_address, String ack_number, String data, byte[] buffer, int offset, int length)
    {        
        this.ack_flag = ack_flag;
        this.syn_flag = syn_flag;
        this.source_port = source_port;
        this.destination_port = destination_port;
        this.ack_number = ack_number;
        this.data = data;
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        this.destination_address = destination_address;
    }

    public String get_ack_flag()
    {
        return ack_flag;
    }

    public String get_syn_flag()
    {
        return syn_flag;
    }

    public String get_ack_number()
    {
        return ack_number;
    }

    public String get_data()
    {
        return data;
    }

    public DatagramPacket convertToDatagramPacket()
    {
        DatagramPacket dPacket = DatagramPacket(buffer, offset, length, destination_address, destination_port)
    }
}