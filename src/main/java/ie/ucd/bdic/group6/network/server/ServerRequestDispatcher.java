package ie.ucd.bdic.group6.network.server;

import ie.ucd.bdic.group6.command.CommandType;
import ie.ucd.bdic.group6.controller.GameController;
import ie.ucd.bdic.group6.controller.dto.ControllerRequest;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.facade.model.CommandResult;
import ie.ucd.bdic.group6.network.message.ClientRequest;
import ie.ucd.bdic.group6.network.message.ServerResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ServerRequestDispatcher {
    private static final Logger LOGGER = Logger.getLogger(ServerRequestDispatcher.class.getName());

    private final GameController gameController;
    private final Map<String, Map<String, String>> playerTokensBySessionId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByRoomCode = new ConcurrentHashMap<>();

    ServerRequestDispatcher(GameController gameController) {
        this.gameController = Objects.requireNonNull(gameController, "gameController");
    }

    DispatchResult dispatch(ClientRequest request) {
        ServerResponse authFailure = authenticate(request);

        if (authFailure != null) {
            return DispatchResult.responseOnly(authFailure);
        }

        CommandResult result = execute(request);

        SessionSnapshot snapshot = result.getSnapshot().orElse(null);
        String authenticatedSessionId = authenticatedSessionId(request);
        String sessionId = snapshot == null
                ? firstNonBlank(text(result.getData().get("sessionId")), authenticatedSessionId)
                : text(snapshot.sessionId());
        String playerId = resolvePlayerId(request);
        Map<String, Object> resultData = result.getData();

        if (result.isSuccess()) {
            if (snapshot != null) {
                rememberRoomCode(snapshot);
                resultData = responsePayload(snapshot);
            }
            resultData = withIssuedPlayerToken(request, sessionId, playerId, resultData);
        }

        ServerResponse response = new ServerResponse(
                request.requestId(),
                result.isSuccess(),
                result.getCode(),
                result.getMessage(),
                resultData,
                snapshot);

        if (!result.isSuccess()) {
            return new DispatchResult(
                    response,
                    sessionId,
                    null,
                    false,
                    request.commandType() == CommandType.REJOIN_SESSION && sessionId != null
            );
        }

        boolean bindConnection = sessionId != null && playerId != null;
        return new DispatchResult(response, sessionId, playerId, bindConnection, bindConnection);
    }

    CommandResult rejoinSession(String sessionId, String playerId) {
        return gameController.rejoinSession(new ControllerRequest(Map.of(
                "sessionIdentity", sessionId,
                "playerId", playerId
        )));
    }

    CommandResult playerDisconnected(String sessionId, String playerId) {
        return gameController.playerDisconnected(new ControllerRequest(Map.of(
                "sessionId", sessionId,
                "playerId", playerId
        )));
    }

    List<String> processDisconnectTimeouts() {
        CommandResult result = gameController.processDisconnectTimeouts(new ControllerRequest(Map.of()));

        if (!result.isSuccess()) {
            return List.of();
        }

        Object rawSessionIds = result.getData().get("sessionIds");

        if (!(rawSessionIds instanceof List<?> sessionIds)) {
            return List.of();
        }

        return sessionIds.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    /**
     * [Deep Dive: Player Authentication — How does the server know who you are?]
     * On CREATE_SESSION or JOIN_SESSION: no auth needed (first contact).
     * On all other commands: the client must send a playerToken in the payload.
     * The token was issued by the server during create/join (a random UUID per session+player).
     * It is stored in a ConcurrentHashMap keyed by (sessionId, playerId).
     * If the token doesn't match → UNAUTHORIZED_PLAYER response is returned and the command is rejected.
     * This prevents spoofing: you can't pretend to be another player without knowing their token.
     */
    private ServerResponse authenticate(ClientRequest request) {
        CommandType commandType = request.commandType();

        if (commandType == CommandType.CREATE_SESSION || commandType == CommandType.JOIN_SESSION) {
            return null;
        }

        String sessionId = authenticatedSessionId(request);
        String playerId = text(request.payload().get("playerId"));
        String playerToken = text(request.payload().get("playerToken"));

        if (sessionId == null || playerId == null || playerToken == null) {
            return new ServerResponse(
                    request.requestId(),
                    false,
                    "AUTH_REQUIRED",
                    "A valid player token is required for " + commandType,
                    Map.of(), null
            );
        }

        String expectedToken = playerTokensBySessionId
                .getOrDefault(sessionId, Map.of())
                .get(playerId);

        if (!playerToken.equals(expectedToken)) {
            return new ServerResponse(
                    request.requestId(),
                    false,
                    "UNAUTHORIZED_PLAYER",
                    "Player identity could not be verified",
                    Map.of(), null
            );
        }

        return null;
    }

    private String authenticatedSessionId(ClientRequest request) {
        if (request.commandType() == CommandType.REJOIN_SESSION) {
            String sessionIdentity = text(request.payload().get("sessionIdentity"));

            if (sessionIdentity == null) {
                return null;
            }

            return sessionIdByRoomCode.getOrDefault(sessionIdentity, sessionIdentity);
        }

        return text(request.payload().get("sessionId"));
    }

    private CommandResult execute(ClientRequest request) {
        try {
            ControllerRequest controllerRequest = new ControllerRequest(request.payload());

            return switch (request.commandType()) {
                case CREATE_SESSION -> gameController.createSession(controllerRequest);
                case JOIN_SESSION -> gameController.joinSession(controllerRequest);
                case START_GAME -> gameController.startGame(controllerRequest);
                case PLAY_CARD -> gameController.playCard(controllerRequest);
                case PAY_DEBT -> gameController.payDebt(controllerRequest);
                case PROPOSE_TRADE -> gameController.proposeTrade(controllerRequest);
                case RESPOND_TRADE -> gameController.respondTrade(controllerRequest);
                case RESPOND_ACTION -> gameController.respondAction(controllerRequest);
                case DISCARD_CARDS -> gameController.discardCards(controllerRequest);
                case END_TURN -> gameController.endTurn(controllerRequest);
                case REJOIN_SESSION -> gameController.rejoinSession(controllerRequest);
                case PLAYER_DISCONNECTED -> gameController.playerDisconnected(controllerRequest);
                case PROCESS_DISCONNECT_TIMEOUTS -> gameController.processDisconnectTimeouts(controllerRequest);
            };
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to execute command " + request.commandType(), e);

            return CommandResult.failure(
                    "NETWORK_COMMAND_FAILED",
                    "Failed to execute " + request.commandType() + ": " + e.getMessage()
            );
        }
    }

    private void rememberRoomCode(SessionSnapshot snapshot) {
        String sessionId = text(snapshot.sessionId());
        String roomCode = text(snapshot.roomCode());

        if (sessionId != null && roomCode != null) {
            sessionIdByRoomCode.put(roomCode, sessionId);
        }
    }

    private Map<String, Object> responsePayload(SessionSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", snapshot.sessionId());
        payload.put("roomCode", snapshot.roomCode());
        return payload;
    }

    private Map<String, Object> withIssuedPlayerToken(
            ClientRequest request,
            String sessionId,
            String playerId,
            Map<String, Object> data
    ) {
        if (sessionId == null || playerId == null) {
            return data;
        }

        String playerToken = switch (request.commandType()) {
            case CREATE_SESSION, JOIN_SESSION -> issuePlayerToken(sessionId, playerId);
            case REJOIN_SESSION -> playerTokensBySessionId
                    .getOrDefault(sessionId, Map.of())
                    .get(playerId);
            default -> null;
        };

        if (playerToken == null) {
            return data;
        }

        Map<String, Object> enriched = new LinkedHashMap<>(data);
        enriched.put("playerToken", playerToken);
        return enriched;
    }

    private String issuePlayerToken(String sessionId, String playerId) {
        Map<String, String> tokens = playerTokensBySessionId.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentHashMap<>()
        );

        return tokens.computeIfAbsent(playerId, ignored -> UUID.randomUUID().toString());
    }

    private String resolvePlayerId(ClientRequest request) {
        String playerId = text(request.payload().get("playerId"));

        if (playerId != null) {
            return playerId;
        }

        if (request.commandType() == CommandType.CREATE_SESSION) {
            return text(request.payload().get("hostPlayerId"));
        }

        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    record DispatchResult(ServerResponse response, String sessionId, String playerId, boolean bindConnection,
            boolean broadcastSessionState) {

        private static DispatchResult responseOnly(ServerResponse response) {
            return new DispatchResult(response, null, null, false, false);
        }
    }
}
