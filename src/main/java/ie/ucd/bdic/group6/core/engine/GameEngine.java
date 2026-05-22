package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;
import ie.ucd.bdic.group6.core.card.*;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.core.rules.ActionRule;
import ie.ucd.bdic.group6.core.rules.WinRule;
import ie.ucd.bdic.group6.exception.GameException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class GameEngine implements ActionExecutor {
    private static final int DEFAULT_MAX_PLAYERS = 5;
    private static final int INITIAL_HAND_SIZE = 5;
    private static final int NORMAL_TURN_DRAW = 2;

    private final Map<String, GameSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, GameSession> sessionsByCode = new ConcurrentHashMap<>();
    private final Supplier<Deck> deckSupplier;
    private final ActionRule actionRule = new ActionRule();
    private final WinRule winRule = new WinRule();

    public GameEngine() {
        this(() -> Deck.shuffled(StandardDeckFactory.createUnshuffledDeck()));
    }

    GameEngine(Supplier<Deck> deckSupplier) {
        this.deckSupplier = Objects.requireNonNull(deckSupplier, "deckSupplier");
    }

    @Override
    public synchronized Map<String, Object> executeCreateSession(CreateSessionCommand command) {
        String hostPlayerId = requireText(command.getHostPlayerId(), "hostPlayerId");
        int maxPlayers = readMaxPlayers(command.payload().get("maxPlayers"));
        String sessionId = UUID.randomUUID().toString();
        String sessionCode = nextSessionCode();

        var session = new GameSession(sessionId, sessionCode, hostPlayerId, maxPlayers);

        session.addPlayer(new Player(hostPlayerId, playerName(command.payload(), hostPlayerId)));
        sessionsById.put(sessionId, session);
        sessionsByCode.put(sessionCode, session);
        return snapshot(session, hostPlayerId);
    }

    @Override
    public synchronized Map<String, Object> executeJoinSession(JoinSessionCommand command) {
        String sessionCode = requireText(command.getSessionCode(), "sessionCode");
        String playerId = requireText(command.getPlayerId(), "playerId");
        GameSession session = requireSessionByCode(sessionCode);
        actionRule.requireLobby(session);
        if (session.hasPlayer(playerId)) {
            throw new GameException("PLAYER_ALREADY_JOINED", "This player has already joined the session");
        }
        if (session.isFull()) {
            throw new GameException("SESSION_FULL", "The session is already full");
        }

        session.addPlayer(new Player(playerId, playerName(command.payload(), playerId)));
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executeStartGame(StartGameCommand command) {
        GameSession session = requireSessionById(requireText(command.getSessionId(), "sessionId"));
        String playerId = requireText(command.getPlayerId(), "playerId");
        actionRule.requireLobby(session);
        if (!session.isHost(playerId)) {
            throw new GameException("HOST_REQUIRED", "Only the host can start the game");
        }
        if (session.playerCount() < 2 || session.playerCount() > 5) {
            throw new GameException("INVALID_PLAYER_COUNT", "A game requires 2 to 5 players");
        }

        Deck deck = deckSupplier.get();
        for (String joinedPlayerId : session.playerJoinOrder()) {
            drawCards(requirePlayer(session, joinedPlayerId), deck, INITIAL_HAND_SIZE);
        }
        ActiveGameState activeGame = new ActiveGameState(deck, new TurnManager(session.playerJoinOrder()));
        session.start(activeGame);
        drawForTurnStart(session, activeGame.turnManager().activePlayerId());
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executePlayCard(PlayCardCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        String cardId = requireText(command.getCardId(), "cardId");
        ActiveGameState activeGame = requireActiveGame(session);
        TurnManager turnManager = activeGame.turnManager();
        actionRule.requireActivePlayer(turnManager, playerId);
        actionRule.requireActionAvailable(turnManager);

        Player player = requirePlayer(session, playerId);
        Card card = requireCardInHand(player, cardId);
        PlayMode playMode = resolvePlayMode(command, card);
        switch (playMode) {
            case BANK -> playBankCard(player, card);
            case PROPERTY -> playPropertyCard(command, player, card);
            case ACTION -> playActionCard(player, card, activeGame);
        }

        turnManager.recordAction();
        checkWin(session, player);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executePayDebt(PayDebtCommand command) {
        // TODO(engine-payment): Add PendingDebt, payment validation, and money/property transfer.
        throw new GameException(
                "UNSUPPORTED_COMMAND_FOR_MVP",
                "Pay debt is not implemented in the Core MVP engine");
    }

    @Override
    public synchronized Map<String, Object> executeProposeTrade(ProposeTradeCommand command) {
        // TODO(engine-trade): Add the simplified trade state machine and validate supported exchange types.
        throw new GameException(
                "UNSUPPORTED_COMMAND_FOR_MVP",
                "Trade proposals are not implemented in the Core MVP engine");
    }

    @Override
    public synchronized Map<String, Object> executeRespondTrade(RespondTradeCommand command) {
        // TODO(engine-trade): Complete accepted trades with atomic two-player card transfers.
        throw new GameException(
                "UNSUPPORTED_COMMAND_FOR_MVP",
                "Trade responses are not implemented in the Core MVP engine");
    }

    @Override
    public synchronized Map<String, Object> executeDiscardCards(DiscardCardsCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        ActiveGameState activeGame = requireActiveGame(session);
        actionRule.requireActivePlayer(activeGame.turnManager(), playerId);

        Player player = requirePlayer(session, playerId);
        discardHandDownToSeven(player, readStringList(command.payload().get("discardedCardIds")), activeGame);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executeEndTurn(EndTurnCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        ActiveGameState activeGame = requireActiveGame(session);
        TurnManager turnManager = activeGame.turnManager();
        actionRule.requireActivePlayer(turnManager, playerId);

        Player player = requirePlayer(session, playerId);
        if (player.hand().size() > 7) {
            throw new GameException("HAND_LIMIT_EXCEEDED", "Discard down to 7 cards before ending the turn");
        }

        String nextPlayerId = turnManager.advanceToNextTurn();
        drawForTurnStart(session, nextPlayerId);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executeRejoinSession(RejoinSessionCommand command) {
        // TODO(engine-rejoin): Replace snapshot lookup with identity validation, timers, skip-turn, and forfeit.
        String sessionIdentity = requireText(command.getSessionIdentity(), "sessionIdentity");
        GameSession session = sessionsById.get(sessionIdentity);
        if (session == null) {
            session = sessionsByCode.get(sessionIdentity);
        }
        if (session == null) {
            throw new GameException("SESSION_NOT_FOUND", "No session exists for the given identity");
        }
        return snapshot(session, command.getPlayerId());
    }

    private GameSession requireActiveSession(String sessionId) {
        GameSession session = requireSessionById(requireText(sessionId, "sessionId"));
        actionRule.requireActive(session);
        return session;
    }

    private ActiveGameState requireActiveGame(GameSession session) {
        return session.activeGame()
                .orElseThrow(() -> new GameException("GAME_NOT_STARTED", "The active game has not been created"));
    }

    private GameSession requireSessionById(String sessionId) {
        GameSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new GameException("SESSION_NOT_FOUND", "No session exists for id " + sessionId);
        }
        return session;
    }

    private GameSession requireSessionByCode(String sessionCode) {
        GameSession session = sessionsByCode.get(sessionCode);
        if (session == null) {
            throw new GameException("SESSION_NOT_FOUND", "No session exists for code " + sessionCode);
        }
        return session;
    }

    private Player requirePlayer(GameSession session, String playerId) {
        return session.findPlayer(playerId)
                .orElseThrow(() -> new GameException("PLAYER_NOT_FOUND", "No player exists for id " + playerId));
    }

    private Card requireCardInHand(Player player, String cardId) {
        return player.handView().stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new GameException("CARD_NOT_IN_HAND", "Card is not in hand: " + cardId));
    }

    private void playBankCard(Player player, Card card) {
        requireSuccess(player.tryBankFromHand(card));
    }

    private void playPropertyCard(PlayCardCommand command, Player player, Card card) {
        if (!(card instanceof PropertyCard) && !(card instanceof PropertyWildCard)) {
            throw new GameException("INVALID_PLAY_MODE", "Only property cards can be played as properties");
        }

        PropertyColor anchorColor = resolvePropertyColor(command, card);
        Optional<PropertyColor> wildChoice = card instanceof PropertyWildCard
                ? Optional.of(anchorColor)
                : Optional.empty();

        Optional<Integer> rowIndex = resolvePropertyRowIndex(command);
        if (rowIndex.isPresent()) {
            List<PropertySet> rows = player.propertyBoard().rowsView();
            int index = rowIndex.get();
            if (index < 0 || index >= rows.size()) {
                throw new GameException("PROPERTY_ROW_NOT_FOUND", "No property row exists at index " + index);
            }
            PropertySet row = rows.get(index);
            if (!row.canAdd(card, wildChoice)) {
                throw new GameException(
                        "RECEIVER_ROW_REJECTS_CARD",
                        "The selected property row rejects this card");
            }
            requireSuccess(player.tryPlayPropertyFromHand(row, card, wildChoice));
            return;
        }

        Optional<PropertySet> existingRow = player.propertyBoard().rowsView().stream()
                .filter(row -> row.anchorColor() == anchorColor && row.canAdd(card, wildChoice))
                .findFirst();

        OperationResult<Void> result = existingRow
                .map(row -> player.tryPlayPropertyFromHand(row, card, wildChoice))
                .orElseGet(() -> player.tryStartNewPropertyRowFromHand(anchorColor, card, wildChoice));
        requireSuccess(result);
    }

    private void playActionCard(Player player, Card card, ActiveGameState activeGame) {
        if (!(card instanceof ActionCard actionCard)) {
            throw new GameException("INVALID_PLAY_MODE", "Only action cards can be played as actions");
        }
        // TODO(engine-action): Move action behavior into effect handlers and implement all non-PASS_GO cards.
        if (actionCard.actionType() != ActionType.PASS_GO) {
            throw new GameException(
                    "UNSUPPORTED_ACTION",
                    actionCard.actionType() + " is not implemented in the Core MVP engine");
        }
        if (!player.hand().remove(actionCard)) {
            throw new GameException("CARD_NOT_IN_HAND", "Action card is not in hand: " + actionCard.id());
        }
        activeGame.discard(actionCard);
        drawCards(player, activeGame.drawPile(), 2);
    }

    private PlayMode resolvePlayMode(PlayCardCommand command, Card card) {
        String rawPlayMode = command.getPlayMode();
        if (rawPlayMode == null || rawPlayMode.isBlank()) {
            return switch (card.type()) {
                case MONEY -> PlayMode.BANK;
                case PROPERTY, PROPERTY_WILD -> PlayMode.PROPERTY;
                case ACTION -> PlayMode.ACTION;
            };
        }
        try {
            return PlayMode.valueOf(rawPlayMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new GameException("INVALID_PLAY_MODE", "Unsupported play mode: " + rawPlayMode, e);
        }
    }

    private PropertyColor resolvePropertyColor(PlayCardCommand command, Card card) {
        String rawColor = command.getPropertyColor();
        if ((rawColor == null || rawColor.isBlank()) && card instanceof PropertyCard propertyCard) {
            return propertyCard.color();
        }
        if (rawColor == null || rawColor.isBlank()) {
            throw new GameException("PROPERTY_COLOR_REQUIRED", "propertyColor is required for wild property cards");
        }
        try {
            PropertyColor color = PropertyColor.valueOf(normalizeEnumToken(rawColor));
            if (card instanceof PropertyCard propertyCard && propertyCard.color() != color) {
                throw new GameException("PROPERTY_COLOR_MISMATCH", "Property card cannot be played to " + color);
            }
            return color;
        } catch (IllegalArgumentException e) {
            throw new GameException("INVALID_PROPERTY_COLOR", "Unsupported property color: " + rawColor, e);
        }
    }

    private Optional<Integer> resolvePropertyRowIndex(PlayCardCommand command) {
        String rawIndex = command.getPropertyRowIndex();
        if (rawIndex == null || rawIndex.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(rawIndex.trim()));
        } catch (NumberFormatException e) {
            throw new GameException("INVALID_PROPERTY_ROW_INDEX", "propertyRowIndex must be a number", e);
        }
    }

    private void discardHandDownToSeven(Player player, List<String> cardIds, ActiveGameState activeGame) {
        int cardsToDiscard = player.hand().size() - 7;
        if (cardsToDiscard <= 0) {
            return;
        }
        List<Card> selected = new ArrayList<>();
        for (String cardId : cardIds) {
            if (selected.size() >= cardsToDiscard) {
                break;
            }
            Card card = requireCardInHand(player, cardId);
            boolean alreadySelected = selected.stream().anyMatch(existing -> existing.id().equals(card.id()));
            if (!alreadySelected) {
                selected.add(card);
            }
        }
        if (selected.size() < cardsToDiscard) {
            throw new GameException(
                    "HAND_LIMIT_EXCEEDED",
                    "Need to discard " + cardsToDiscard + " cards but received " + selected.size());
        }
        for (Card card : selected) {
            if (!player.hand().remove(card)) {
                throw new GameException("CARD_NOT_IN_HAND", "Card is not in hand: " + card.id());
            }
            activeGame.discard(card);
        }
    }

    private void drawForTurnStart(GameSession session, String playerId) {
        ActiveGameState activeGame = requireActiveGame(session);
        Player player = requirePlayer(session, playerId);
        int drawCount = player.hand().size() == 0 ? INITIAL_HAND_SIZE : NORMAL_TURN_DRAW;
        drawCards(player, activeGame.drawPile(), drawCount);
    }

    private void drawCards(Player player, Deck deck, int count) {
        for (int i = 0; i < count; i++) {
            deck.tryDraw().ifPresent(player::addToHand);
        }
    }

    private void checkWin(GameSession session, Player player) {
        if (winRule.hasWon(player, true)) {
            session.finish(player.id());
        }
    }

    private void requireSuccess(OperationResult<?> result) {
        if (!result.isSuccess()) {
            String code = result.errorCode().map(Enum::name).orElse("OPERATION_FAILED");
            String message = result.errorMessage().orElse("Operation failed");
            throw new GameException(code, message);
        }
    }

    private Map<String, Object> snapshot(GameSession session, String requesterPlayerId) {
        // TODO(engine-snapshot): Replace this map with a typed DTO once UI/network contracts stabilize.
        Optional<ActiveGameState> activeGame = session.activeGame();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.sessionId());
        data.put("sessionCode", session.sessionCode());
        data.put("status", session.status().name());
        data.put("activePlayerId", activeGame.map(game -> game.turnManager().activePlayerId()).orElse(""));
        data.put("actionsUsed", activeGame.map(game -> game.turnManager().actionsUsed()).orElse(0));
        data.put("actionsRemaining", activeGame.map(game -> game.turnManager().actionsRemaining()).orElse(0));
        data.put("winnerPlayerId", activeGame.flatMap(ActiveGameState::winnerPlayerId).orElse(""));
        data.put("deckSize", activeGame.map(game -> game.drawPile().size()).orElse(0));
        data.put("discardSize", activeGame.map(ActiveGameState::discardPileSize).orElse(0));
        data.put("players", session.players().stream().map(this::playerSummary).toList());

        List<Map<String, Object>> currentPlayerHand = session.findPlayer(blankToEmpty(requesterPlayerId))
                .map(player -> player.handView().stream().map(this::cardSummary).toList())
                .orElse(List.of());
        data.put("currentPlayerHand", currentPlayerHand);
        return Map.copyOf(data);
    }

    private Map<String, Object> playerSummary(Player player) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("playerId", player.id());
        data.put("name", player.name());
        data.put("handCount", player.hand().size());
        data.put("bankTotalValueM", player.bankTotalValueM());
        data.put("bankCards", player.bankView().stream().map(this::cardSummary).toList());
        data.put("propertyRows", player.propertyBoard().rowsView().stream().map(this::propertyRowSummary).toList());
        return Map.copyOf(data);
    }

    private Map<String, Object> propertyRowSummary(PropertySet row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorColor", row.anchorColor().name());
        data.put("complete", row.isComplete());
        data.put("rentValue", row.rentValue());
        data.put("hasHouse", row.hasHouse());
        data.put("hasHotel", row.hasHotel());
        data.put("cards", row.cardsView().stream().map(this::cardSummary).toList());
        return Map.copyOf(data);
    }

    private Map<String, Object> cardSummary(Card card) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", card.id());
        data.put("type", card.type().name());
        data.put("displayName", card.displayName());
        data.put("bankValueM", card.bankValueM().orElse(0));
        if (card instanceof MoneyCard moneyCard) {
            data.put("valueM", moneyCard.valueM());
        }
        if (card instanceof PropertyCard propertyCard) {
            data.put("propertyColor", propertyCard.color().name());
            data.put("requiredToComplete", propertyCard.requiredToComplete());
        }
        if (card instanceof PropertyWildCard wildCard) {
            data.put("allowedColors", wildCard.allowedColors().stream().map(Enum::name).toList());
        }
        if (card instanceof ActionCard actionCard) {
            data.put("actionType", actionCard.actionType().name());
            data.put("rentColorOptions", actionCard.rentColorOptions().stream().map(Enum::name).toList());
        }
        return Map.copyOf(data);
    }

    private int readMaxPlayers(Object raw) {
        if (raw == null) {
            return DEFAULT_MAX_PLAYERS;
        }
        int maxPlayers;
        if (raw instanceof Number number) {
            maxPlayers = number.intValue();
        } else {
            try {
                maxPlayers = Integer.parseInt(raw.toString());
            } catch (NumberFormatException e) {
                throw new GameException("INVALID_MAX_PLAYERS", "maxPlayers must be a number", e);
            }
        }
        if (maxPlayers < 2 || maxPlayers > 5) {
            throw new GameException("INVALID_MAX_PLAYERS", "maxPlayers must be between 2 and 5");
        }
        return maxPlayers;
    }

    private List<String> readStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new GameException("INVALID_CARD_SELECTION", "Card selection must be a list of ids");
        }
        List<String> ids = new ArrayList<>();
        for (Object value : rawList) {
            ids.add(requireText(value == null ? null : value.toString(), "cardId"));
        }
        return List.copyOf(ids);
    }

    private String nextSessionCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        } while (sessionsByCode.containsKey(code));
        return code;
    }

    private String playerName(Map<String, Object> payload, String playerId) {
        Object rawName = payload.get("playerName");
        String name = rawName == null ? "" : rawName.toString().trim();
        return name.isEmpty() ? playerId : name;
    }

    private String normalizeEnumToken(String value) {
        return value.trim().replace(' ', '_').replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new GameException("MISSING_REQUIRED_FIELD", name + " is required");
        }
        return value;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum PlayMode {
        BANK,
        PROPERTY,
        ACTION
    }
}
