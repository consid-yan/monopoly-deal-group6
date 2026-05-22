package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GameSession {
    private final String sessionId;
    private final String sessionCode;
    private final String hostPlayerId;
    private final int maxPlayers;
    private final Map<String, Player> playersById = new LinkedHashMap<>();
    private final List<String> playerJoinOrder = new ArrayList<>();
    private SessionStatus status = SessionStatus.LOBBY;
    private ActiveGameState activeGame;

    public GameSession(String sessionId, String sessionCode, String hostPlayerId, int maxPlayers) {
        this.sessionId = requireText(sessionId, "sessionId");
        this.sessionCode = requireText(sessionCode, "sessionCode");
        this.hostPlayerId = requireText(hostPlayerId, "hostPlayerId");
        if (maxPlayers < 2 || maxPlayers > 5) {
            throw new IllegalArgumentException("maxPlayers must be between 2 and 5");
        }
        this.maxPlayers = maxPlayers;
    }

    public String sessionId() {
        return sessionId;
    }

    public String sessionCode() {
        return sessionCode;
    }

    public String hostPlayerId() {
        return hostPlayerId;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public SessionStatus status() {
        return status;
    }

    public Optional<ActiveGameState> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public boolean isHost(String playerId) {
        return hostPlayerId.equals(playerId);
    }

    public boolean hasPlayer(String playerId) {
        return playersById.containsKey(playerId);
    }

    public Optional<Player> findPlayer(String playerId) {
        return Optional.ofNullable(playersById.get(playerId));
    }

    public Collection<Player> players() {
        return List.copyOf(playersById.values());
    }

    public List<String> playerJoinOrder() {
        return List.copyOf(playerJoinOrder);
    }

    public int playerCount() {
        return playersById.size();
    }

    public boolean isFull() {
        return playerCount() >= maxPlayers;
    }

    public void addPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        if (playersById.containsKey(player.id())) {
            throw new IllegalArgumentException("duplicate player id: " + player.id());
        }
        if (isFull()) {
            throw new IllegalStateException("session is full");
        }
        playersById.put(player.id(), player);
        playerJoinOrder.add(player.id());
    }

    public void start(ActiveGameState activeGame) {
        this.activeGame = Objects.requireNonNull(activeGame, "activeGame");
        this.status = SessionStatus.ACTIVE;
    }

    public void finish(String winnerPlayerId) {
        if (activeGame == null) {
            throw new IllegalStateException("active game is not initialized");
        }
        activeGame.setWinnerPlayerId(requireText(winnerPlayerId, "winnerPlayerId"));
        this.status = SessionStatus.FINISHED;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

