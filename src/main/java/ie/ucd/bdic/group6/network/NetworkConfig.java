package ie.ucd.bdic.group6.network;

public final class NetworkConfig {
    public static final int DEFAULT_PORT = 1935; // The year Monopoly was released
    public static final int CONNECT_TIMEOUT_MS = 5000;

    public static void requireValidPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, but was: " + port);
        }
    }
}
