package ploud.rentor.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class InetAddressTest {
    private static InetAddress getFirstNonLoopbackAddress() {
        try {
            Enumeration networkInterfacesEn = NetworkInterface.getNetworkInterfaces();
            while (networkInterfacesEn.hasMoreElements()) {
                NetworkInterface networkInterface = (NetworkInterface) networkInterfacesEn.nextElement();
                for (Enumeration inetAddressesEn = networkInterface.getInetAddresses(); inetAddressesEn.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) inetAddressesEn.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        if (inetAddress instanceof Inet4Address) {
                            return inetAddress;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            String ipAddress = getFirstNonLoopbackAddress().getLocalHost().getHostAddress();
            System.out.println(ipAddress);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
