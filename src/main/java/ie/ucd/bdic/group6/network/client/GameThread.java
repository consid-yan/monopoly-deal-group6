package ie.ucd.bdic.group6.network.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.util.Objects;

public class GameThread extends Thread {
    private final ObjectInputStream input;
    private final GameClient client;

    GameThread(ObjectInputStream input, GameClient client) {
        super("MonopolyDealClient-Reader");
        setDaemon(true);
        this.input = Objects.requireNonNull(input, "input");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void run() {
        try {
            while (client.isConnected()) {
                client.handleMessage(input.readObject());
            }
        } catch (EOFException | SocketException ignored) {
            client.handleDisconnect(null);
        } catch (IOException | ClassNotFoundException e) {
            client.handleDisconnect(e);
        }
    }
}
