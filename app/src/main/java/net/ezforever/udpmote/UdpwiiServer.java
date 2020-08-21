package net.ezforever.udpmote;

import java.net.DatagramPacket;

public class UdpwiiServer {
    public final String address;
    public final int id;
    public final int index;
    public final String name;
    public final int port;
    public final long timestamp = System.currentTimeMillis();

    public UdpwiiServer(DatagramPacket packet) {
        byte[] buf = packet.getData();
        int name_len = buf[6] & 255;
        this.id = ((buf[1] & 255) << 8) | (buf[2] & 255);
        this.index = buf[3] + 1;
        this.port = ((buf[4] & 255) << 8) | (buf[5] & 255);
        this.name = new String(buf, 7, name_len);
        this.address = packet.getAddress().getHostAddress();
    }

    public static boolean validatePacket(DatagramPacket packet) {
        byte[] buf = packet.getData();
        if (packet.getLength() < 8) {
            return false;
        }
        if (packet.getLength() != (buf[6] & 255) + 7 || buf[0] != -33) {
            return false;
        }
        if ((buf[4] != 0 || buf[5] != 0) && buf[3] >= 0 && buf[3] <= 3) {
            return true;
        }
        return false;
    }
}
