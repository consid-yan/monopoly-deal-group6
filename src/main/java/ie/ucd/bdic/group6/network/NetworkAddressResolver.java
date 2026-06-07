package ie.ucd.bdic.group6.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

public final class NetworkAddressResolver {

    public static String resolveShareableHost() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (!isUsableNetworkInterface(networkInterface)) {
                    continue;
                }

                String address = firstNonLoopbackIpv4Address(networkInterface);

                if (address != null) {
                    return address;
                }
            }
            return "127.0.0.1";
        } catch (SocketException e) {
            return "127.0.0.1";
        }
    }

    private static boolean isUsableNetworkInterface(NetworkInterface networkInterface) throws SocketException {
        if (networkInterface.isLoopback()
                || networkInterface.isVirtual()
                || !networkInterface.isUp()) {
            return false;
        }

        String name = networkInterface.getName().toLowerCase(Locale.ROOT);
        String displayName = networkInterface.getDisplayName() == null
                ? ""
                : networkInterface.getDisplayName().toLowerCase(Locale.ROOT);

        return !isIgnoredInterfaceName(name)
                && !isIgnoredInterfaceName(displayName);
    }

    private static boolean isIgnoredInterfaceName(String interfaceName) {
        return interfaceName.contains("docker")
                || interfaceName.contains("vmnet")
                || interfaceName.contains("vbox")
                || interfaceName.contains("virtualbox")
                || interfaceName.contains("wsl");
    }

    private static String firstNonLoopbackIpv4Address(NetworkInterface networkInterface) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();

            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }
        }

        return null;
    }
}
