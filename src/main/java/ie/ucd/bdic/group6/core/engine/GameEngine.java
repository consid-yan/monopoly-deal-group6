package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.*;
import ie.ucd.bdic.group6.core.card.*;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.player.PlayerPropertyTransfers;
import ie.ucd.bdic.group6.core.player.PlayerSnapshot;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertyPaymentSupport;
import ie.ucd.bdic.group6.core.property.PropertyRentSupport;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.core.rules.ActionRule;
import ie.ucd.bdic.group6.core.rules.PaymentRule;
import ie.ucd.bdic.group6.core.rules.WinRule;
import ie.ucd.bdic.group6.exception.GameException;

import java.util.*;
import java.util.function.Supplier;

public class GameEngine implements ActionExecutor {
    private static final int DEFAULT_MAX_PLAYERS = 5;
    private static final int INITIAL_HAND_SIZE = 5;
    private static final int NORMAL_TURN_DRAW = 2;

    private final Map<String, GameSession> sessionsById = new HashMap<>();
    private final Map<String, GameSession> sessionsByCode = new HashMap<>();
    private final Supplier<Deck> deckSupplier;
    private final ActionRule actionRule = new ActionRule();
    private final WinRule winRule = new WinRule();
    private final PaymentRule paymentRule = new PaymentRule();

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
            drawCardsFromDeck(requirePlayer(session, joinedPlayerId), deck, INITIAL_HAND_SIZE);
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
            case ACTION -> playActionCard(command, session, player, card, activeGame);
        }

        turnManager.recordAction();
        checkWin(session, player, activeGame.turnManager());
        return snapshot(session, playerId);
    }

    @Override
    public synchronized Map<String, Object> executePayDebt(PayDebtCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        PendingDebt debt = resolvePendingDebt(activeGame, command);
        Player debtor = requirePlayer(session, debt.debtorPlayerId());
        Player creditor = requirePlayer(session, debt.creditorPlayerId());
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
    public synchronized Map<String, Object> executeProposeTrade(ProposeTradeCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        String proposerId = requireText(command.getPlayerId(), "playerId");
        String targetId = requireText(command.getTargetPlayerId(), "targetPlayerId");
        actionRule.requireActivePlayer(activeGame.turnManager(), proposerId);
        if (proposerId.equals(targetId)) {
            throw new GameException("TRADE_WITH_SELF", "Cannot propose a trade with yourself");
        }

        Player proposer = requirePlayer(session, proposerId);
        Player target = requirePlayer(session, targetId);
        List<String> offeredCardIds = readStringList(command.getOfferedCardIds());
        List<String> requestedCardIds = readStringList(command.getRequestedCardIds());
        if (offeredCardIds.isEmpty() || requestedCardIds.isEmpty()) {
            throw new GameException("EMPTY_TRADE", "Trade must include offered and requested cards");
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
    public synchronized Map<String, Object> executeRespondTrade(RespondTradeCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        ActiveGameState activeGame = requireActiveGame(session);
        String responderId = requireText(command.getPlayerId(), "playerId");
        actionRule.requireActivePlayer(activeGame.turnManager(), responderId);

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
    public synchronized Map<String, Object> executeDiscardCards(DiscardCardsCommand command) {
        GameSession session = requireActiveSession(command.getSessionId());
        String playerId = requireText(command.getPlayerId(), "playerId");
        ActiveGameState activeGame = requireActiveGame(session);
        actionRule.requireActivePlayer(activeGame.turnManager(), playerId);

        Player player = requirePlayer(session, playerId);
        List<String> cardIds = readStringList(command.payload().get("discardedCardIds"));
        discardHandDownToSeven(player, cardIds, activeGame);
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
        String playerId = requireText(command.getPlayerId(), "playerId");
        GameSession session = sessionsById.get(sessionIdentity);
        if (session == null) {
            session = sessionsByCode.get(sessionIdentity);
        }
        if (session == null) {
            throw new GameException("SESSION_NOT_FOUND", "No session exists for the given identity");
        }
        requirePlayer(session, playerId);
        return snapshot(session, playerId);
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
            requireSuccess(movePropertyCardToBestRow(
                    debtor,
                    creditor,
                    propertyCard.card(),
                    propertyCard.sourceRow()));
        }
    }

    private DebtPaymentSelection resolveDebtPayment(Player debtor, List<String> paymentCardIds) {
        if (paymentCardIds.isEmpty()) {
            throw new GameException("PAYMENT_REQUIRED", "At least one payment card is required");
        }
        Set<String> seen = new HashSet<>();
        List<Card> bankCards = new ArrayList<>();
        List<PropertyPaymentCard> propertyCards = new ArrayList<>();
        int totalValueM = 0;

        for (String cardId : paymentCardIds) {
            if (!seen.add(cardId)) {
                throw new GameException("DUPLICATE_CARD_SELECTION", "Card selected more than once: " + cardId);
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

            PropertyCardLocation propertyCard = findPropertyCardLocation(debtor, cardId)
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
                requireSuccess(movePropertyCardToBestRow(sender, receiver, selection.card(), selection.sourceRow()));
            }
        }
    }

    private List<TradeCardSelection> resolveTradeCards(Player player, List<String> cardIds) {
        if (cardIds.isEmpty()) {
            throw new GameException("EMPTY_TRADE_SIDE", "Each side of a trade must contain at least one card");
        }
        Set<String> seen = new HashSet<>();
        List<TradeCardSelection> selections = new ArrayList<>();
        for (String cardId : cardIds) {
            if (!seen.add(cardId)) {
                throw new GameException("DUPLICATE_CARD_SELECTION", "Card selected more than once: " + cardId);
            }
            Optional<Card> bankCard = findBankCard(player, cardId);
            if (bankCard.isPresent()) {
                selections.add(new TradeCardSelection(bankCard.get(), TradeCardKind.MONEY, null));
                continue;
            }
            PropertyCardLocation propertyCard = findPropertyCardLocation(player, cardId)
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

    private OperationResult<Void> movePropertyCardToBestRow(
            Player sender,
            Player receiver,
            Card card,
            PropertySet sourceRow) {
        PropertyPlacement placement = placementForTransferredProperty(card, sourceRow);
        Optional<PropertySet> receiverRow = receiver.propertyBoard().rowsView().stream()
                .filter(row -> row.anchorColor() == placement.anchorColor())
                .filter(row -> row.canAdd(card, placement.wildChoice()))
                .findFirst();
        if (receiverRow.isPresent()) {
            return PlayerPropertyTransfers.transferPropertyCardToRow(
                    sender,
                    receiver,
                    card,
                    receiverRow.get(),
                    placement.wildChoice());
        }
        OperationResult<PropertySet> moved = PlayerPropertyTransfers.transferPropertyCardToNewRow(
                sender,
                receiver,
                card,
                placement.anchorColor(),
                placement.wildChoice());
        if (!moved.isSuccess()) {
            return OperationResult.fail(
                    moved.errorCode().orElse(OperationError.GENERIC),
                    moved.errorMessage().orElse(""));
        }
        return OperationResult.ok();
    }

    private PropertyPlacement placementForTransferredProperty(Card card, PropertySet sourceRow) {
        if (card instanceof PropertyCard propertyCard) {
            return new PropertyPlacement(propertyCard.color(), Optional.empty());
        }
        if (card instanceof PropertyWildCard wildCard) {
            PropertyColor choice = sourceRow.wildAssignmentFor(wildCard.id())
                    .orElse(sourceRow.anchorColor());
            return new PropertyPlacement(choice, Optional.of(choice));
        }
        throw new GameException("PROPERTY_CANNOT_PAY_DEBT", "Only property cards can be transferred as properties");
    }

    private Optional<Card> findBankCard(Player player, String cardId) {
        return player.bankView().stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst();
    }

    private Optional<PropertyCardLocation> findPropertyCardLocation(Player player, String cardId) {
        for (PropertySet row : player.propertyBoard().rowsView()) {
            Optional<Card> card = row.cardsView().stream()
                    .filter(candidate -> candidate.id().equals(cardId))
                    .findFirst();
            if (card.isPresent()) {
                return Optional.of(new PropertyCardLocation(card.get(), row));
            }
        }
        return Optional.empty();
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

    private void playActionCard(
            PlayCardCommand command,
            GameSession session,
            Player player,
            Card card,
            ActiveGameState activeGame) {
        if (!(card instanceof ActionCard actionCard)) {
            throw new GameException("INVALID_PLAY_MODE", "Only action cards can be played as actions");
        }

        switch (actionCard.actionType()) {
            case PASS_GO -> {
                discardActionFromHand(player, actionCard, activeGame);
                drawCards(player, activeGame, 2);
            }
            case DEBT_COLLECTOR -> executeDebtCollector(command, session, player, actionCard, activeGame);
            case ITS_MY_BIRTHDAY -> executeItsMyBirthday(session, player, actionCard, activeGame);
            case RENT_BROWN_OR_LIGHT_BLUE,
                    RENT_PINK_OR_ORANGE,
                    RENT_RED_OR_YELLOW,
                    RENT_DARK_BLUE_OR_GREEN,
                    RENT_RAILROAD_OR_UTILITY,
                    WILD_RENT -> executeRentCard(command, session, player, actionCard, activeGame);
            case HOUSE, HOTEL -> executeImprovementCard(command, player, actionCard, activeGame);
            case DOUBLE_THE_RENT -> throw new GameException(
                    "UNSUPPORTED_ACTION",
                    "Double The Rent must be paired with a rent card and is not supported yet");
            default -> throw new GameException(
                    "UNSUPPORTED_ACTION",
                    actionCard.actionType() + " is not implemented in the Core MVP engine");
        }
    }

    private void executeDebtCollector(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = requireText(command.getTargetPlayerId(), "targetPlayerId");
        if (player.id().equals(targetPlayerId)) {
            throw new GameException("INVALID_TARGET_PLAYER", "Debt Collector cannot target yourself");
        }
        requirePlayer(session, targetPlayerId);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingDebt(PendingDebt.builder()
                .creditorPlayerId(player.id())
                .debtorPlayerId(targetPlayerId)
                .amountM(5)
                .sourceAction(actionCard.actionType())
                .sourceCardId(actionCard.id())
                .build());
    }

    private void executeItsMyBirthday(
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        List<String> debtorIds = activeGame.turnManager().turnOrder().stream()
                .filter(playerId -> !playerId.equals(player.id()))
                .toList();
        debtorIds.forEach(playerId -> requirePlayer(session, playerId));
        discardActionFromHand(player, actionCard, activeGame);
        for (String debtorId : debtorIds) {
            activeGame.addPendingDebt(PendingDebt.builder()
                    .creditorPlayerId(player.id())
                    .debtorPlayerId(debtorId)
                    .amountM(2)
                    .sourceAction(actionCard.actionType())
                    .sourceCardId(actionCard.id())
                    .build());
        }
    }

    private void executeRentCard(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        PropertyColor rentColor = resolveRentColor(command, player, actionCard);
        int rentM = PropertyRentSupport.maxCompleteRowRentForAnchor(player.propertyBoard(), rentColor);
        if (rentM <= 0) {
            throw new GameException("NO_RENTABLE_PROPERTY", "No complete rentable set for " + rentColor);
        }
        List<Player> targets = resolveRentTargets(command, session, player, activeGame);
        discardActionFromHand(player, actionCard, activeGame);
        for (Player target : targets) {
            activeGame.addPendingDebt(PendingDebt.builder()
                    .creditorPlayerId(player.id())
                    .debtorPlayerId(target.id())
                    .amountM(rentM)
                    .sourceAction(actionCard.actionType())
                    .sourceCardId(actionCard.id())
                    .build());
        }
    }

    private void executeImprovementCard(
            PlayCardCommand command,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        PropertySet targetRow = resolveImprovementRow(command, player);
        OperationResult<ActionCard> spent = actionCard.actionType() == ActionType.HOUSE
                ? player.trySpendHouseCardOnRow(actionCard, targetRow)
                : player.trySpendHotelCardOnRow(actionCard, targetRow);
        requireSuccess(spent);
        activeGame.discard(spent.value().orElse(actionCard));
    }

    private PropertyColor resolveRentColor(PlayCardCommand command, Player player, ActionCard actionCard) {
        String rawColor = command.getPropertyColor();
        if (rawColor != null && !rawColor.isBlank()) {
            PropertyColor color = parsePropertyColor(rawColor);
            if (actionCard.actionType() != ActionType.WILD_RENT && !actionCard.rentColorOptions().contains(color)) {
                throw new GameException("INVALID_RENT_COLOR", "Rent card cannot charge " + color);
            }
            return color;
        }

        List<PropertyColor> availableColors = player.propertyBoard().rowsView().stream()
                .filter(PropertySet::isComplete)
                .map(PropertySet::anchorColor)
                .filter(color -> actionCard.actionType() == ActionType.WILD_RENT
                        || actionCard.rentColorOptions().contains(color))
                .distinct()
                .toList();
        if (availableColors.isEmpty()) {
            throw new GameException("NO_RENTABLE_PROPERTY", "No complete set matches this rent card");
        }
        if (availableColors.size() > 1) {
            throw new GameException("PROPERTY_COLOR_REQUIRED", "propertyColor is required when multiple rents apply");
        }
        return availableColors.getFirst();
    }

    private List<Player> resolveRentTargets(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActiveGameState activeGame) {
        String targetPlayerId = command.getTargetPlayerId();
        if (targetPlayerId != null && !targetPlayerId.isBlank()) {
            if (targetPlayerId.equals(player.id())) {
                throw new GameException("INVALID_TARGET_PLAYER", "Rent cannot target yourself");
            }
            return List.of(requirePlayer(session, targetPlayerId));
        }
        return activeGame.turnManager().turnOrder().stream()
                .filter(playerId -> !playerId.equals(player.id()))
                .map(playerId -> requirePlayer(session, playerId))
                .toList();
    }

    private PropertySet resolveImprovementRow(PlayCardCommand command, Player player) {
        Optional<Integer> rowIndex = resolvePropertyRowIndex(command);
        if (rowIndex.isPresent()) {
            List<PropertySet> rows = player.propertyBoard().rowsView();
            int index = rowIndex.get();
            if (index < 0 || index >= rows.size()) {
                throw new GameException("PROPERTY_ROW_NOT_FOUND", "No property row exists at index " + index);
            }
            return rows.get(index);
        }

        String rawColor = command.getPropertyColor();
        if (rawColor == null || rawColor.isBlank()) {
            throw new GameException("PROPERTY_ROW_REQUIRED", "propertyRowIndex or propertyColor is required");
        }
        PropertyColor color = parsePropertyColor(rawColor);
        return player.propertyBoard().rowsView().stream()
                .filter(row -> row.anchorColor() == color && row.isComplete())
                .findFirst()
                .orElseThrow(() -> new GameException(
                        "NO_COMPLETE_SET",
                        "No complete property set exists for " + color));
    }

    private void discardActionFromHand(Player player, ActionCard actionCard, ActiveGameState activeGame) {
        if (!player.hand().remove(actionCard)) {
            throw new GameException("CARD_NOT_IN_HAND", "Action card is not in hand: " + actionCard.id());
        }
        activeGame.discard(actionCard);
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
        PropertyColor color = parsePropertyColor(rawColor);
        if (card instanceof PropertyCard propertyCard && propertyCard.color() != color) {
            throw new GameException("PROPERTY_COLOR_MISMATCH", "Property card cannot be played to " + color);
        }
        if (card instanceof PropertyWildCard wildCard && !wildCard.allowedColors().contains(color)) {
            throw new GameException("PROPERTY_COLOR_MISMATCH", "Wild card does not support color " + color);
        }
        return color;
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
        drawCards(player, activeGame, drawCount);
    }

    private void drawCardsFromDeck(Player player, Deck deck, int count) {
        for (int i = 0; i < count; i++) {
            deck.tryDraw().ifPresent(player::addToHand);
        }
    }

    private void drawCards(Player player, ActiveGameState activeGame, int count) {
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

    private void requireSuccess(OperationResult<?> result) {
        if (!result.isSuccess()) {
            String code = result.errorCode().map(Enum::name).orElse("OPERATION_FAILED");
            String message = result.errorMessage().orElse("Operation failed");
            throw new GameException(code, message);
        }
    }

    private Map<String, Object> snapshot(GameSession session, String requesterPlayerId) {
        // Centralized win check: every command path that produces a snapshot evaluates win conditions.
        // This guarantees new action types (Sly Deal, Force Deal, etc.) cannot forget to check for victory.
        session.activeGame().ifPresent(ag -> {
            if (session.status() == SessionStatus.ACTIVE) {
                Player activePlayer = session.findPlayer(ag.turnManager().activePlayerId()).orElse(null);
                if (activePlayer != null) {
                    checkWin(session, activePlayer, ag.turnManager());
                }
            }
        });

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
        data.put("pendingDebts", activeGame
                .map(game -> game.pendingDebtsView().stream().map(this::pendingDebtSummary).toList())
                .orElse(List.of()));
        data.put("pendingTrades", activeGame
                .map(game -> game.pendingTradesView().stream().map(this::pendingTradeSummary).toList())
                .orElse(List.of()));
        data.put("players", session.players().stream().map(this::playerSummary).toList());

        List<Map<String, Object>> currentPlayerHand = session.findPlayer(blankToEmpty(requesterPlayerId))
                .map(player -> player.handView().stream().map(this::cardSummary).toList())
                .orElse(List.of());
        data.put("currentPlayerHand", currentPlayerHand);
        return Map.copyOf(data);
    }

    private Map<String, Object> pendingDebtSummary(PendingDebt debt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("debtId", debt.debtId());
        data.put("creditorPlayerId", debt.creditorPlayerId());
        data.put("debtorPlayerId", debt.debtorPlayerId());
        data.put("amountM", debt.amountM());
        data.put("sourceAction", debt.sourceAction() == null ? "" : debt.sourceAction().name());
        data.put("sourceCardId", debt.sourceCardId() == null ? "" : debt.sourceCardId());
        return Map.copyOf(data);
    }

    private Map<String, Object> pendingTradeSummary(PendingTrade trade) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tradeId", trade.tradeId());
        data.put("proposerPlayerId", trade.proposerPlayerId());
        data.put("targetPlayerId", trade.targetPlayerId());
        data.put("offeredCardIds", trade.offeredCardIds());
        data.put("requestedCardIds", trade.requestedCardIds());
        data.put("status", trade.status().name());
        data.put("createdAt", trade.createdAt().toString());
        data.put("respondedAt", trade.respondedAt() == null ? "" : trade.respondedAt().toString());
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

    private PropertyColor parsePropertyColor(String rawColor) {
        try {
            return PropertyColor.valueOf(normalizeEnumToken(rawColor));
        } catch (IllegalArgumentException e) {
            throw new GameException("INVALID_PROPERTY_COLOR", "Unsupported property color: " + rawColor, e);
        }
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private record PropertyCardLocation(Card card, PropertySet row) {
    }

    private record PropertyPaymentCard(Card card, PropertySet sourceRow) {
    }

    private record DebtPaymentSelection(
            List<Card> bankCards,
            List<PropertyPaymentCard> propertyCards,
            int totalValueM) {
    }

    private record PropertyPlacement(PropertyColor anchorColor, Optional<PropertyColor> wildChoice) {
    }

    private enum TradeCardKind {
        MONEY,
        PROPERTY
    }

    private record TradeCardSelection(Card card, TradeCardKind kind, PropertySet sourceRow) {
    }

    private enum PlayMode {
        BANK,
        PROPERTY,
        ACTION
    }
}
