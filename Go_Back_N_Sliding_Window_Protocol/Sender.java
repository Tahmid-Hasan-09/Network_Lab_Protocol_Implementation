import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;


public class Sender {

	//initial values from user
	private static int windowSize = 4;
	private static Protocol protocol = Protocol.GBN;
	private static int packetNumber = 20;
	private static int portNumber = 5001;
	private static int delayBetweenPackets = 1500;
	public static int timeout = 3000;
	public static double lostAck = 0.05;

	public static void main(String[] args) {

		System.out.println("Sender Ready");

		// calling send data
		try {
			sendData(packetNumber, protocol, windowSize);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static void sendData(int numPackets, Protocol protocol, int windowSize) throws IOException, ClassNotFoundException, InterruptedException {

		ArrayList<Packet> sent = new ArrayList<>();

		int waitingForAck = 0;
		int lastSent = 0;

		byte[] incomingData = new byte[1024];

		//connecting via socket
		DatagramSocket Socket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName("localhost");

		//using ObjectOutputStream to transfer data
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(outputStream);

		//sending data via DatagramPacket
		//sending an initial message to signal receiver
		DatagramPacket initialPacket = new DatagramPacket(new byte[10], 10, IPAddress, Sender.portNumber);
		Socket.send(initialPacket);
		if (protocol == Protocol.GBN) {
			while (true) {
				while (lastSent - waitingForAck < windowSize && lastSent < numPackets) {
					Packet packet = new Packet();
					packet.setPacketNumber(lastSent);
					if (lastSent == numPackets - 1) {
						packet.setLast(true);
					}
					outputStream = new ByteArrayOutputStream();
					os = new ObjectOutputStream(outputStream);
					os.writeObject(packet);
					byte[] data = outputStream.toByteArray();
					DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, Sender.portNumber);
					System.out.println("Sending Packet " + packet.getPacketNumber());
					sent.add(packet);
					Socket.send(sendPacket);
					lastSent++;
					Thread.sleep(delayBetweenPackets);
				}
				DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
				try {
					//set time out for socket
					Socket.setSoTimeout(timeout);
					//read ack
					Socket.receive(incomingPacket);
					byte[] data = incomingPacket.getData();
					ByteArrayInputStream in = new ByteArrayInputStream(data);
					ObjectInputStream is = new ObjectInputStream(in);
					Ack ack = (Ack) is.readObject();
					if (Math.random() > lostAck) {
						//ack is received
						System.out.println("Received ack " + (ack.getAckNumber() - 1));
						waitingForAck = Math.max(waitingForAck, ack.getAckNumber());
					} else {
						//ack is not received
						System.out.println("Ack " + (ack.getAckNumber() - 1) + " not received");
					}

					if (ack.getAckNumber() == numPackets) {
						//finish sending data
						break;
					}
				} catch (SocketTimeoutException e) {

					//time out exception caused by time out
					//resending packets in the window
					System.out.println("Timeout for ack " + waitingForAck);

					for (int i = waitingForAck; i < lastSent; i++) {

						Packet packet = sent.get(i);
						outputStream = new ByteArrayOutputStream();
						os = new ObjectOutputStream(outputStream);
						os.writeObject(packet);
						byte[] data = outputStream.toByteArray();

						//resending packets in the window
						DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, Sender.portNumber);
						System.out.println("Sending packet " + packet.getPacketNumber() + " again");
						Socket.send(sendPacket);
						Thread.sleep(delayBetweenPackets);
					}
				}
			}
		}

		
	}
}