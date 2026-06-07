package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;
import ie.ucd.bdic.group6.core.card.*;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.player.PlayerSnapshot;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertyPaymentSupport;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.core.rules.ActionRule;
import ie.ucd.bdic.group6.core.rules.PaymentRule;
import ie.ucd.bdic.group6.core.rules.WinRule;
import ie.ucd.bdic.group6.core.snapshot.*;
import ie.ucd.bdic.group6.exception.GameException;
import ie.ucd.bdic.group6.exception.InvalidCommandException;
import ie.ucd.bdic.group6.exception.InvalidGameStateException;
import ie.ucd.bdic.group6.exception.PlayerNotFoundException;
import ie.ucd.bdic.group6.exception.SessionNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class GameEngine implements ActionExecutor, GameplayHandler.Host {
    private static final int DEFAULT_MAX_PLAYERS = 5;
    private static final int INITIAL_HAND_SIZE = 5;
    private static final int NORMAL_TURN_DRAW = 2;
    private static final Duration DISCONNECTED_TURN_SKIP_AFTER = Duration.ofSeconds(30);
    private static final Duration DISCONNECTED_FORFEIT_AFTER = Duration.ofSeconds(90);

    private final Map<String, GameSession> sessionsById = new HashMap<>();
    private final Map<String, GameSession> sessionsByRoomCode = new HashMap<>();
    private final Supplier<Deck> deckSupplier;
    private final Clock clock;
    private final ActionRule actionRule = new ActionRule();
    private final WinRule winRule = new WinRule();
    private final PaymentRule paymentRule = new PaymentRule();
    private final GameplayHandler gameplayHandler;
    private final SessionSnapshotBuilder snapshotBuilder;

    public GameEngine() {
        this(() -> Deck.shuffled(StandardDeckFactory.createUnshuffledDeck()));
    }

    public GameEngine(Clock clock) {
        this(() -> Deck.shuffled(StandardDeckFactory.createUnshuffledDeck()), clock);
    }

    GameEngine(Supplier<Deck> deckSupplier) {
        this(deckSupplier, Clock.systemUTC());
    }

    GameEngine(Supplier<Deck> deckSupplier, Clock clock) {
        this.deckSupplier = Objects.requireNonNull(deckSupplier, "deckSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gameplayHandler = new GameplayHandler(actionRule, this);
        this.snapshotBuilder = new SessionSnapshotBuilder(winRule);
    }

    @Override
    public synchronized SessionSnapshot executeCreateSession(CreateSessionCommand command) {
        String hostPlayerId = requireText(command.getHostPlayerId(), "hostPlayerId");
        int maxPlayers = readMaxPlayers(command.payload().get("maxPlayers"));
        String sessionId = UUID.randomUUID().toString();
        String roomCode = nextRoomCode();

        var session = new GameSession(sessionId, roomCode, hostPlayerId, maxPlayers);

        session.addPlayer(new Player(hostPlayerId, playerName(command.payload(), hostPlayerId)));
        sessionsById.put(sessionId, session);
        sessionsByRoomCode.put(roomCode, session);
        return snapshot(session, hostPlayerId);
    }

    @Override
    public synchronized SessionSnapshot executeJoinSession(JoinSessionCommand command) {
        String roomCode = requireText(command.roomCode(), "roomCode");
        String playerId = requireText(command.getPlayerId(), "playerId");
        GameSession session = requireRoomCode(roomCode);
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
    public synchronized SessionSnapshot executeStartGame(StartGameCommand command) {
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
            drawCardsFromDeck(requirePlayer(session, joinedPlayerId), deck, INITIAL_HAND_SIZE);
        }
        ActiveGameState activeGame = new ActiveGameState(deck, new TurnManager(session.playerJoinOrder()));
        session.start(activeGame);
        drawForTurnStart(session, activeGame.turnManager().activePlayerId());
        return snapshot(session, playerId);
    }

    @Override
    public synchronized SessionSnapshot executePlayCard(PlayCardCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        String cardId = requireText(command.getCardId(), "cardId");
        ActiveGameState activeGame = requireActiveGame(session);
        TurnManager turnManager = activeGame.turnManager();
        requireNoPendingActionResponses(activeGame);
        requireNoPendingDebts(activeGame);
        actionRule.requireActivePlayer(turnManager, playerId);

        Player player = requirePlayer(session, playerId);
        requireNotDefeated(session, playerId);
        Card card = requireCardInHand(player, cardId);
        int actionCost = actionCost(command, card);
        actionRule.requireActionsAvailable(turnManager, actionCost);
        GameplayHandler.PlayMode playMode = gameplayHandler.resolvePlayMode(command, card);
        switch (playMode) {
            case BANK -> gameplayHandler.playBankCard(player, card);
            case PROPERTY -> gameplayHandler.playPropertyCard(command, player, card);
            case ACTION -> gameplayHandler.playActionCard(command, session, player, card, activeGame);
        }

        for (int i = 0; i < actionCost; i++) {
            turnManager.recordAction();
        }
        checkWin(session, player, activeGame.turnManager());
        return snapshot(session, playerId);
    }

    @Override
    public synchronized SessionSnapshot executePayDebt(PayDebtCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        PendingDebt debt = resolvePendingDebt(activeGame, command);
        Player debtor = requirePlayer(session, debt.debtorPlayerId());
        Player creditor = requirePlayer(session, debt.creditorPlayerId());
        requireNotDefeated(session, debtor.id());
        requireNotDefeated(session, creditor.id());
        List<String> paymentCardIds = readStringList(command.getPaymentCardIds());

        int paymentValueM = paymentValueBeforeTransfer(debtor, paymentCardIds);
        if (paymentValueM < debt.amountM()) {
            throw new GameException(
                    "INSUFFICIENT_PAYMENT",
                    "Selected cards total " + paymentValueM + "M but debt requires " + debt.amountM() + "M");
        }

        PlayerSnapshot debtorSnapshot = debtor.snapshot();
        PlayerSnapshot creditorSnapshot = creditor.snapshot();
        try {
            applyDebtPayment(debtor, creditor, paymentCardIds);
        } catch (RuntimeException e) {
            debtor.restore(debtorSnapshot);
            creditor.restore(creditorSnapshot);
            throw e;
        }

        debt.markAsPaid();
        activeGame.removePendingDebt(debt);
        Player activePlayer = requirePlayer(session, activeGame.turnManager().activePlayerId());
        checkWin(session, activePlayer, activeGame.turnManager());
        return snapshot(session, debtor.id());
    }

    @Override
    public synchronized SessionSnapshot executeProposeTrade(ProposeTradeCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        String proposerId = requireText(command.getPlayerId(), "playerId");
        String targetId = requireText(command.getTargetPlayerId(), "targetPlayerId");
        requireNoPendingActionResponses(activeGame);
        requireNoPendingDebts(activeGame);
        actionRule.requireActivePlayer(activeGame.turnManager(), proposerId);
        requireNotDefeated(session, proposerId);
        if (proposerId.equals(targetId)) {
            throw new GameException("TRADE_WITH_SELF", "Cannot propose a trade with yourself");
        }

        Player proposer = requirePlayer(session, proposerId);
        Player target = requirePlayer(session, targetId);
        requireNotDefeated(session, targetId);
        List<String> offeredCardIds = readStringList(command.getOfferedCardIds());
        List<String> requestedCardIds = readStringList(command.getRequestedCardIds());
        if (offeredCardIds.isEmpty() || requestedCardIds.isEmpty()) {
            throw new InvalidCommandException("EMPTY_TRADE", "Trade must include offered and requested cards");
        }
        if (activeGame.hasPendingTradeInvolving(proposerId) || activeGame.hasPendingTradeInvolving(targetId)) {
            throw new GameException("PENDING_TRADE_EXISTS", "A player in this trade already has a pending trade");
        }

        validateSupportedTrade(resolveTradeCards(proposer, offeredCardIds), resolveTradeCards(target, requestedCardIds));
        PendingTrade trade = PendingTrade.builder()
                .proposerPlayerId(proposerId)
                .targetPlayerId(targetId)
                .offeredCardIds(offeredCardIds)
                .requestedCardIds(requestedCardIds)
                .build();
        activeGame.addPendingTrade(trade);
        return snapshot(session, proposerId);
    }

    @Override
    public synchronized SessionSnapshot executeRespondTrade(RespondTradeCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        String responderId = requireText(command.getPlayerId(), "playerId");
        requireNoPendingActionResponses(activeGame);
        requireNoPendingDebts(activeGame);
        actionRule.requireActivePlayer(activeGame.turnManager(), responderId);
        requireNotDefeated(session, responderId);

        PendingTrade trade = activeGame.findPendingTrade(requireText(command.getTradeId(), "tradeId"))
                .orElseThrow(() -> new GameException("TRADE_NOT_FOUND", "No pending trade exists with that id"));
        if (!trade.targetPlayerId().equals(responderId)) {
            throw new GameException("NOT_TRADE_TARGET", "Only the trade target can respond");
        }
        if (!trade.isPending()) {
            throw new GameException("TRADE_NOT_PENDING", "Trade is no longer pending");
        }

        if (command.isAccepted()) {
            Player proposer = requirePlayer(session, trade.proposerPlayerId());
            Player responder = requirePlayer(session, responderId);
            acceptTrade(trade, proposer, responder);
            checkWin(session, responder, activeGame.turnManager());
        } else {
            trade.reject();
        }
        activeGame.removePendingTrade(trade);
        return snapshot(session, responderId);
    }

    @Override
    public synchronized SessionSnapshot executeRespondAction(RespondActionCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        String responderId = requireText(command.getPlayerId(), "playerId");
        requireNotDefeated(session, responderId);

        PendingAction pendingAction = activeGame.findPendingAction(requireText(command.getActionId(), "actionId"))
                .orElseThrow(() -> new GameException("ACTION_NOT_FOUND", "No pending action exists with that id"));
        if (!pendingAction.awaitsResponseFrom(responderId)) {
            throw new GameException("ACTION_RESPONSE_NOT_ALLOWED", "This player is not awaiting this action response");
        }

        if (command.isAccepted()) {
            pendingAction.accept(responderId);
        } else {
            gameplayHandler.spendJustSayNo(session, activeGame, responderId, command.getJustSayNoCardId());
            pendingAction.reject(responderId);
        }

        if (pendingAction.isComplete()) {
            gameplayHandler.applyResolvedPendingAction(session, activeGame, pendingAction);
            activeGame.removePendingAction(pendingAction);
        }
        Player activePlayer = requirePlayer(session, activeGame.turnManager().activePlayerId());
        checkWin(session, activePlayer, activeGame.turnManager());
        return snapshot(session, responderId);
    }

    @Override
    public synchronized SessionSnapshot executeDiscardCards(DiscardCardsCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        ActiveGameState activeGame = requireActiveGame(session);
        actionRule.requireActivePlayer(activeGame.turnManager(), playerId);
        requireNotDefeated(session, playerId);

        Player player = requirePlayer(session, playerId);
        List<String> cardIds = readStringList(command.getDiscardedCardIds());
        discardHandDownToSeven(player, cardIds, activeGame);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized SessionSnapshot executeEndTurn(EndTurnCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        ActiveGameState activeGame = requireActiveGame(session);
        TurnManager turnManager = activeGame.turnManager();
        requireNoPendingActionResponses(activeGame);
        requireNoPendingDebts(activeGame);
        actionRule.requireActivePlayer(turnManager, playerId);
        requireNotDefeated(session, playerId);

        Player player = requirePlayer(session, playerId);
        if (player.hand().size() > 7) {
            throw new GameException("HAND_LIMIT_EXCEEDED", "Discard down to 7 cards before ending the turn");
        }

        cancelTradesAwaitingTarget(activeGame, playerId);
        String nextPlayerId = advanceToNextAvailableTurn(session);
        drawForTurnStart(session, nextPlayerId);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized SessionSnapshot executeRejoinSession(RejoinSessionCommand command) {
        String sessionIdentity = requireText(command.getSessionIdentity(), "sessionIdentity");
        String playerId = requireText(command.getPlayerId(), "playerId");
        GameSession session = sessionsById.get(sessionIdentity);
        if (session == null) {
            session = sessionsByRoomCode.get(sessionIdentity);
        }
        if (session == null) {
            throw new SessionNotFoundException("No session exists for the given identity");
        }
        requirePlayer(session, playerId);
        if (session.isDefeated(playerId)) {
            throw new InvalidGameStateException("PLAYER_FORFEITED", "This player has already forfeited the session");
        }
        session.markConnected(playerId);
        return snapshot(session, playerId);
    }

    @Override
    public synchronized SessionSnapshot executePlayerDisconnected(PlayerDisconnectedCommand command) {
        GameSession session = requireSessionById(requireText(command.getSessionId(), "sessionId"));
        String playerId = requireText(command.getPlayerId(), "playerId");
        requirePlayer(session, playerId);
        session.markDisconnected(playerId, Instant.now(clock));
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executeProcessDisconnectTimeouts(ProcessDisconnectTimeoutsCommand command) {
        List<String> changedSessionIds = new ArrayList<>();
        for (GameSession session : sessionsById.values()) {
            if (processDisconnectTimeouts(session)) {
                changedSessionIds.add(session.sessionId());
            }
        }
        return Map.of("sessionIds", changedSessionIds);
    }

    private int actionCost(PlayCardCommand command, Card card) {
        GameplayHandler.PlayMode playMode = gameplayHandler.resolvePlayMode(command, card);
        if (playMode == GameplayHandler.PlayMode.ACTION
                && card instanceof ActionCard actionCard
                && GameplayHandler.isRentAction(actionCard.actionType())
                && command.getDoubleRentCardId() != null
                && !command.getDoubleRentCardId().isBlank()) {
            return 2;
        }
        return 1;
    }

    private void requireNoPendingActionResponses(ActiveGameState activeGame) {
        if (activeGame.hasPendingActions()) {
            throw new InvalidGameStateException("ACTION_RESPONSE_REQUIRED", "Resolve pending action responses first");
        }
    }

    private void requireNoPendingDebts(ActiveGameState activeGame) {
        if (!activeGame.pendingDebtsView().isEmpty()) {
            throw new InvalidGameStateException("PAYMENT_REQUIRED", "Resolve pending debts before continuing the turn");
        }
    }

    @Override
    public void requireNotDefeated(GameSession session, String playerId) {
        if (session.isDefeated(playerId)) {
            throw new InvalidGameStateException("PLAYER_DEFEATED", "Defeated players cannot act: " + playerId);
        }
    }

    private void cancelTradesAwaitingTarget(ActiveGameState activeGame, String targetPlayerId) {
        for (PendingTrade trade : activeGame.pendingTradesForTarget(targetPlayerId)) {
            trade.cancel();
            activeGame.removePendingTrade(trade);
        }
    }

    private String advanceToNextAvailableTurn(GameSession session) {
        ActiveGameState activeGame = requireActiveGame(session);
        TurnManager turnManager = activeGame.turnManager();
        int guard = turnManager.turnOrder().size();
        String nextPlayerId;
        do {
            nextPlayerId = turnManager.advanceToNextTurn();
            guard--;
        } while (guard > 0 && session.isDefeated(nextPlayerId));
        if (session.isDefeated(nextPlayerId)) {
            throw new GameException("NO_ACTIVE_PLAYERS", "No active players remain in this session");
        }
        return nextPlayerId;
    }

    private boolean processDisconnectTimeouts(GameSession session) {
        if (session.status() == SessionStatus.FINISHED) {
            return false;
        }
        boolean changed = false;
        Instant now = Instant.now(clock);
        for (String playerId : session.playerJoinOrder()) {
            if (session.isDefeated(playerId)) {
                continue;
            }
            Optional<Instant> disconnectedAt = session.disconnectedAt(playerId);
            if (disconnectedAt.isPresent()
                    && Duration.between(disconnectedAt.get(), now).compareTo(DISCONNECTED_FORFEIT_AFTER) >= 0) {
                forfeitPlayer(session, playerId);
                changed = true;
            }
        }

        if (session.status() == SessionStatus.ACTIVE && session.activeGame().isPresent()) {
            String activePlayerId = session.activeGame().orElseThrow().turnManager().activePlayerId();
            Optional<Instant> disconnectedAt = session.disconnectedAt(activePlayerId);
            if (!session.isDefeated(activePlayerId)
                    && disconnectedAt.isPresent()
                    && Duration.between(disconnectedAt.get(), now).compareTo(DISCONNECTED_TURN_SKIP_AFTER) >= 0) {
                skipDisconnectedActiveTurn(session);
                changed = true;
            }
        }
        return changed;
    }

    private void skipDisconnectedActiveTurn(GameSession session) {
        ActiveGameState activeGame = requireActiveGame(session);
        String skippedPlayerId = activeGame.turnManager().activePlayerId();
        cancelTradesAwaitingTarget(activeGame, skippedPlayerId);
        String nextPlayerId = advanceToNextAvailableTurn(session);
        drawForTurnStart(session, nextPlayerId);
    }

    private void forfeitPlayer(GameSession session, String playerId) {
        if (session.isDefeated(playerId)) {
            return;
        }
        session.markDefeated(playerId);
        session.activeGame().ifPresent(activeGame -> {
            discardAllPlayerCards(requirePlayer(session, playerId), activeGame);
            activeGame.clearAllDebtsForPlayer(playerId);
            activeGame.clearAllTradesForPlayer(playerId);
            activeGame.clearAllActionsForPlayer(playerId);
            if (session.status() == SessionStatus.ACTIVE
                    && activeGame.turnManager().activePlayerId().equals(playerId)
                    && session.activePlayerIds().size() > 1) {
                String nextPlayerId = advanceToNextAvailableTurn(session);
                drawForTurnStart(session, nextPlayerId);
            }
        });
        finishIfOnlyOneActivePlayerRemains(session);
    }

    private void discardAllPlayerCards(Player player, ActiveGameState activeGame) {
        for (Card card : player.hand().copyCards()) {
            activeGame.discard(card);
        }
        for (Card card : player.bank().copyCards()) {
            activeGame.discard(card);
        }
        for (PropertySet row : player.propertyBoard().rowsView()) {
            for (Card card : row.cardsView()) {
                activeGame.discard(card);
            }
        }
        player.hand().clear();
        player.bank().clear();
        player.propertyBoard().clearAllRows();
    }

    private void finishIfOnlyOneActivePlayerRemains(GameSession session) {
        if (session.status() != SessionStatus.ACTIVE) {
            return;
        }
        List<String> activePlayerIds = session.activePlayerIds();
        if (activePlayerIds.size() == 1) {
            session.finish(activePlayerIds.getFirst());
        }
    }

    private PendingDebt resolvePendingDebt(ActiveGameState activeGame, PayDebtCommand command) {
        String debtId = command.getDebtId();
        String debtorId = firstNonBlank(command.getDebtorId(), command.getPlayerId());
        String creditorId = command.getCreditorId();
        if (debtId != null && !debtId.isBlank()) {
            PendingDebt debt = activeGame.findPendingDebt(debtId)
                    .orElseThrow(() -> new GameException("DEBT_NOT_FOUND", "No pending debt exists with that id"));
            if (debtorId != null && !debt.debtorPlayerId().equals(debtorId)) {
                throw new GameException("DEBTOR_MISMATCH", "Debt belongs to a different debtor");
            }
            if (creditorId != null && !creditorId.isBlank() && !debt.creditorPlayerId().equals(creditorId)) {
                throw new GameException("CREDITOR_MISMATCH", "Debt belongs to a different creditor");
            }
            return debt;
        }

        String requiredDebtorId = requireText(debtorId, "debtorId");
        String requiredCreditorId = requireText(creditorId, "creditorId");
        List<PendingDebt> matches = activeGame.pendingDebtsForDebtor(requiredDebtorId).stream()
                .filter(debt -> debt.creditorPlayerId().equals(requiredCreditorId))
                .toList();
        if (matches.isEmpty()) {
            throw new GameException("NO_PENDING_DEBT", "No pending debt matches the requested players");
        }
        if (matches.size() > 1) {
            throw new GameException("MULTIPLE_PENDING_DEBTS", "Specify debtId when multiple debts match");
        }
        return matches.getFirst();
    }

    private int paymentValueBeforeTransfer(Player debtor, List<String> paymentCardIds) {
        return resolveDebtPayment(debtor, paymentCardIds).totalValueM();
    }

    private void applyDebtPayment(Player debtor, Player creditor, List<String> paymentCardIds) {
        DebtPaymentSelection payment = resolveDebtPayment(debtor, paymentCardIds);
        if (!payment.bankCards().isEmpty() && payment.propertyCards().isEmpty()) {
            requireSuccess(paymentRule.validateSelectedBankPayment(debtor.bank(), payment.bankCards(), payment.totalValueM()));
        }
        if (!payment.bankCards().isEmpty()) {
            requireSuccess(debtor.tryPayBankCardsTo(creditor, payment.bankCards()));
        }
        for (PropertyPaymentCard propertyCard : payment.propertyCards()) {
            requireSuccess(gameplayHandler.movePropertyCardToBestRow(
                    debtor,
                    creditor,
                    propertyCard.card(),
                    propertyCard.sourceRow()));
        }
    }

    private DebtPaymentSelection resolveDebtPayment(Player debtor, List<String> paymentCardIds) {
        if (paymentCardIds.isEmpty()) {
            throw new InvalidCommandException("PAYMENT_REQUIRED", "At least one payment card is required");
        }
        Set<String> seen = new HashSet<>();
        List<Card> bankCards = new ArrayList<>();
        List<PropertyPaymentCard> propertyCards = new ArrayList<>();
        int totalValueM = 0;

        for (String cardId : paymentCardIds) {
            if (!seen.add(cardId)) {
                throw new InvalidCommandException("DUPLICATE_CARD_SELECTION", "Card selected more than once: " + cardId);
            }
            Optional<Card> bankCard = findBankCard(debtor, cardId);
            if (bankCard.isPresent()) {
                Card card = bankCard.get();
                if (card.bankValueM().isEmpty()) {
                    throw new GameException("NON_BANKABLE_CARD", "Bank card has no payment value: " + card.id());
                }
                bankCards.add(card);
                totalValueM += CardBankSupport.bankPaymentValueM(card);
                continue;
            }

            GameplayHandler.PropertyCardLocation propertyCard = gameplayHandler.findPropertyCardLocation(debtor, cardId)
                    .orElseThrow(() -> new GameException(
                            "CARD_NOT_AVAILABLE_FOR_PAYMENT",
                            "Card is not in debtor bank or property board: " + cardId));
            OperationResult<Integer> value = PropertyPaymentSupport.paymentValueM(
                    propertyCard.row(),
                    propertyCard.card());
            requireSuccess(value);
            propertyCards.add(new PropertyPaymentCard(propertyCard.card(), propertyCard.row()));
            totalValueM += value.value().orElseThrow();
        }
        return new DebtPaymentSelection(List.copyOf(bankCards), List.copyOf(propertyCards), totalValueM);
    }

    private void acceptTrade(PendingTrade trade, Player proposer, Player responder) {
        List<TradeCardSelection> offeredCards = resolveTradeCards(proposer, trade.offeredCardIds());
        List<TradeCardSelection> requestedCards = resolveTradeCards(responder, trade.requestedCardIds());
        validateSupportedTrade(offeredCards, requestedCards);

        PlayerSnapshot proposerSnapshot = proposer.snapshot();
        PlayerSnapshot responderSnapshot = responder.snapshot();
        try {
            transferTradeCards(proposer, responder, offeredCards);
            transferTradeCards(responder, proposer, requestedCards);
            trade.accept();
        } catch (RuntimeException e) {
            proposer.restore(proposerSnapshot);
            responder.restore(responderSnapshot);
            throw e;
        }
    }

    private void transferTradeCards(Player sender, Player receiver, List<TradeCardSelection> selections) {
        List<Card> bankCards = selections.stream()
                .filter(selection -> selection.kind() == TradeCardKind.MONEY)
                .map(TradeCardSelection::card)
                .toList();
        if (!bankCards.isEmpty()) {
            requireSuccess(sender.tryPayBankCardsTo(receiver, bankCards));
        }
        for (TradeCardSelection selection : selections) {
            if (selection.kind() == TradeCardKind.PROPERTY) {
                requireSuccess(gameplayHandler.movePropertyCardToBestRow(sender, receiver, selection.card(), selection.sourceRow()));
            }
        }
    }

    private List<TradeCardSelection> resolveTradeCards(Player player, List<String> cardIds) {
        if (cardIds.isEmpty()) {
            throw new InvalidCommandException("EMPTY_TRADE_SIDE", "Each side of a trade must contain at least one card");
        }
        Set<String> seen = new HashSet<>();
        List<TradeCardSelection> selections = new ArrayList<>();
        for (String cardId : cardIds) {
            if (!seen.add(cardId)) {
                throw new InvalidCommandException("DUPLICATE_CARD_SELECTION", "Card selected more than once: " + cardId);
            }
            Optional<Card> bankCard = findBankCard(player, cardId);
            if (bankCard.isPresent()) {
                selections.add(new TradeCardSelection(bankCard.get(), TradeCardKind.MONEY, null));
                continue;
            }
            GameplayHandler.PropertyCardLocation propertyCard = gameplayHandler.findPropertyCardLocation(player, cardId)
                    .orElseThrow(() -> new GameException(
                            "CARD_NOT_TRADEABLE",
                            "Trade card must be in the player's bank or property board: " + cardId));
            selections.add(new TradeCardSelection(
                    propertyCard.card(),
                    TradeCardKind.PROPERTY,
                    propertyCard.row()));
        }
        return List.copyOf(selections);
    }

    private void validateSupportedTrade(
            List<TradeCardSelection> offeredCards,
            List<TradeCardSelection> requestedCards) {
        Set<TradeCardKind> offeredKinds = tradeKinds(offeredCards);
        Set<TradeCardKind> requestedKinds = tradeKinds(requestedCards);
        if (offeredKinds.size() != 1 || requestedKinds.size() != 1) {
            throw new GameException("UNSUPPORTED_TRADE_TYPE", "Each trade side must use one card category");
        }
        TradeCardKind offeredKind = offeredKinds.iterator().next();
        TradeCardKind requestedKind = requestedKinds.iterator().next();
        if (offeredKind == TradeCardKind.MONEY && requestedKind == TradeCardKind.MONEY) {
            throw new GameException(
                    "UNSUPPORTED_TRADE_TYPE",
                    "Supported trades are property-money, money-property, or property-property");
        }
    }

    private Set<TradeCardKind> tradeKinds(List<TradeCardSelection> selections) {
        EnumSet<TradeCardKind> kinds = EnumSet.noneOf(TradeCardKind.class);
        for (TradeCardSelection selection : selections) {
            kinds.add(selection.kind());
        }
        return kinds;
    }

    private Optional<Card> findBankCard(Player player, String cardId) {
        return player.bankView().stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst();
    }

    private GameSession requireActiveSession(String sessionId) {
        GameSession session = requireSessionById(requireText(sessionId, "sessionId"));
        actionRule.requireActive(session);
        return session;
    }

    private ActiveGameState requireActiveGame(GameSession session) {
        return session.activeGame()
                .orElseThrow(() -> new InvalidGameStateException(
                        "GAME_NOT_STARTED",
                        "The active game has not been created"));
    }

    private GameSession requireSessionById(String sessionId) {
        GameSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("No session exists for id " + sessionId);
        }
        return session;
    }

    private GameSession requireRoomCode(String roomCode) {
        GameSession session = sessionsByRoomCode.get(roomCode);
        if (session == null) {
            throw new SessionNotFoundException("No session exists for code " + roomCode);
        }
        return session;
    }

    @Override
    public Player requirePlayer(GameSession session, String playerId) {
        return session.findPlayer(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No player exists for id " + playerId));
    }

    @Override
    public Card requireCardInHand(Player player, String cardId) {
        return player.handView().stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new GameException("CARD_NOT_IN_HAND", "Card is not in hand: " + cardId));
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
        drawCards(player, activeGame, drawCount);
    }

    private void drawCardsFromDeck(Player player, Deck deck, int count) {
        for (int i = 0; i < count; i++) {
            deck.tryDraw().ifPresent(player::addToHand);
        }
    }

    @Override
    public void drawCards(Player player, ActiveGameState activeGame, int count) {
        Deck deck = activeGame.drawPile();
        int before = player.hand().size();
        for (int i = 0; i < count; i++) {
            if (deck.isEmpty()) {
                activeGame.reshuffleDiscardIntoDraw();
            }
            deck.tryDraw().ifPresent(player::addToHand);
        }
        int drawn = player.hand().size() - before;
        if (drawn < count) {
            throw new GameException("DRAW_PILE_EXHAUSTED",
                    "Expected " + count + " cards but only drew " + drawn + " — both piles exhausted");
        }
    }

    private void checkWin(GameSession session, Player player, TurnManager turnManager) {
        if (winRule.hasWon(player, turnManager.isActivePlayer(player.id()))) {
            session.finish(player.id());
        }
    }

    @Override
    public void requireSuccess(OperationResult<?> result) {
        if (!result.isSuccess()) {
            String code = result.errorCode().map(Enum::name).orElse("OPERATION_FAILED");
            String message = result.errorMessage().orElse("Operation failed");
            throw new GameException(code, message);
        }
    }

    private SessionSnapshot snapshot(GameSession session, String requesterPlayerId) {
        return snapshotBuilder.build(session, requesterPlayerId);
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
                throw new InvalidCommandException("INVALID_MAX_PLAYERS", "maxPlayers must be a number", e);
            }
        }
        if (maxPlayers < 2 || maxPlayers > 5) {
            throw new InvalidCommandException("INVALID_MAX_PLAYERS", "maxPlayers must be between 2 and 5");
        }
        return maxPlayers;
    }

    private List<String> readStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new InvalidCommandException("INVALID_CARD_SELECTION", "Card selection must be a list of ids");
        }
        List<String> ids = new ArrayList<>();
        for (Object value : rawList) {
            ids.add(requireText(value == null ? null : value.toString(), "cardId"));
        }
        return List.copyOf(ids);
    }

    private String nextRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        } while (sessionsByRoomCode.containsKey(code));
        return code;
    }

    private String playerName(Map<String, Object> payload, String playerId) {
        Object rawName = payload.get("playerName");
        String name = rawName == null ? "" : rawName.toString().trim();
        return name.isEmpty() ? playerId : name;
    }

    @Override
    public PropertyColor parsePropertyColor(String rawColor) {
        try {
            return PropertyColor.valueOf(normalizeEnumToken(rawColor));
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException(
                    "INVALID_PROPERTY_COLOR",
                    "Unsupported property color: " + rawColor,
                    e);
        }
    }

    private String normalizeEnumToken(String value) {
        return value.trim().replace(' ', '_').replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    @Override
    public String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidCommandException("MISSING_REQUIRED_FIELD", name + " is required");
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private record PropertyPaymentCard(Card card, PropertySet sourceRow) {
    }

    private record DebtPaymentSelection(
            List<Card> bankCards,
            List<PropertyPaymentCard> propertyCards,
            int totalValueM) {
    }

    private enum TradeCardKind {
        MONEY,
        PROPERTY
    }

    private record TradeCardSelection(Card card, TradeCardKind kind, PropertySet sourceRow) {
    }
}
