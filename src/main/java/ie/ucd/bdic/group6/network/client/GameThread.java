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
        System.out.println("[Client] Message reader thread started, waiting for server messages...");
        try {
            while (client.isConnected()) {
                Object message = input.readObject();
                System.out.println("[Client] Received message from server: " + message.getClass().getSimpleName());
                client.handleMessage(message);
            }
        } catch (EOFException | SocketException ignored) {
            System.out.println("[Client] Connection closed by server");
            client.handleDisconnect(null);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Client] ❌ Failed to read message from server");
            client.handleDisconnect(e);
        } finally {
            System.out.println("[Client] Message reader thread exited");
        }
    }
}