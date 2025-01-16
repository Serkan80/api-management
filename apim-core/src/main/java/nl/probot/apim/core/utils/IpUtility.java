package nl.probot.apim.core.utils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpUtility {

    static final String IPV_4_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(/[0-9]{1,2})?$";

    public static boolean isValidIPv4(String ip) {
        return ip.matches(IPV_4_REGEX);
    }

    public static boolean isValidIPv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // Check if CIDR notation is present
        if (ip.contains("/")) {
            String[] parts = ip.split("/");
            if (parts.length != 2) {
                return false;
            }

            String ipv6 = parts[0];
            String prefix = parts[1];

            // Validate prefix length (0-128 for IPv6)
            try {
                int prefixLength = Integer.parseInt(prefix);
                if (prefixLength < 0 || prefixLength > 128) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }

            return checkIPv6(ipv6);
        } else {
            return checkIPv6(ip);
        }
    }

    public static boolean isIPv4InRange(String ipToCheck, String subnet) throws UnknownHostException {
        String[] parts = subnet.split("/");
        String subnetIp = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        byte[] ipToCheckBytes = InetAddress.getByName(ipToCheck).getAddress();
        byte[] subnetIpBytes = InetAddress.getByName(subnetIp).getAddress();

        // Calculate the subnet mask
        byte[] subnetMask = new byte[subnetIpBytes.length];
        for (int i = 0; i < subnetMask.length; i++) {
            subnetMask[i] = (byte) (i < prefixLength / 8 ? 0xFF : 0x00);
            if (i == prefixLength / 8 && (prefixLength % 8) != 0) {
                subnetMask[i] = (byte) (0xFF << (8 - (prefixLength % 8)));
            }
        }

        // Apply the subnet mask to both the IP and the subnet
        byte[] maskedIp = applyMask(ipToCheckBytes, subnetMask);
        byte[] maskedSubnet = applyMask(subnetIpBytes, subnetMask);

        return compareBytes(maskedIp, maskedSubnet) == 0;
    }

    public static boolean isIPv6InRange(String ipv6Address, String subnet) {
        try {
            // Parse the CIDR notation
            String[] parts = subnet.split("/");
            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            // Convert IP addresses to byte arrays
            InetAddress network = InetAddress.getByName(networkAddress);
            InetAddress ip = InetAddress.getByName(ipv6Address);

            byte[] networkBytes = network.getAddress();
            byte[] ipBytes = ip.getAddress();

            // Compare the prefix bits
            int bytePrefix = prefixLength / 8;
            int bitPrefix = prefixLength % 8;

            // Compare full bytes
            for (int i = 0; i < bytePrefix; i++) {
                if (networkBytes[i] != ipBytes[i]) {
                    return false;
                }
            }

            // Compare remaining bits
            if (bitPrefix > 0) {
                int mask = ~((1 << (8 - bitPrefix)) - 1);
                return (networkBytes[bytePrefix] & mask) == (ipBytes[bytePrefix] & mask);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] applyMask(byte[] ip, byte[] mask) {
        byte[] maskedIp = new byte[ip.length];
        for (int i = 0; i < ip.length; i++) {
            maskedIp[i] = (byte) (ip[i] & mask[i]);
        }
        return maskedIp;
    }

    private static int compareBytes(byte[] ip1, byte[] ip2) {
        for (int i = 0; i < Math.min(ip1.length, ip2.length); i++) {
            int byte1 = Byte.toUnsignedInt(ip1[i]);
            int byte2 = Byte.toUnsignedInt(ip2[i]);
            if (byte1 != byte2) {
                return Integer.compare(byte1, byte2);
            }
        }
        return 0;
    }

    private static boolean checkIPv6(String ipv6) {
        try {
            InetAddress addr = InetAddress.getByName(ipv6);
            return addr instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
