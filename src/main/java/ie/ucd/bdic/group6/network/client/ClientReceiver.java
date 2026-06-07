package ie.ucd.bdic.group6.network.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.util.Objects;

public final class ClientReceiver implements Runnable{
    private final ObjectInputStream input;
    private final GameClient client;

    public ClientReceiver(ObjectInputStream inputStream, GameClient client) {
        input = Objects.requireNonNull(inputStream, "inputStream");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void run() {
        try {
            while (client.isConnected()) {
                final Object inputObject = input.readObject();
                client.handleMessage(inputObject);
            }
        } catch (EOFException ignored) {
            // Server closed the stream normally.
            client.handleDisconnect(null);
        } catch (SocketException e) {
            // Socket may be closed by client.close(), or reset by network/server.
            client.handleDisconnect(e);
        } catch (IOException | ClassNotFoundException e) {
            // Unexpected read failure or incompatible message class.
            client.handleDisconnect(e);
        }
    }
}
