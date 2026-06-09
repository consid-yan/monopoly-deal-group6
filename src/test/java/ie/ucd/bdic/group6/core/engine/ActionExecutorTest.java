package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.session.CreateSessionCommand;
import ie.ucd.bdic.group6.command.card.DiscardCardCommand;
import ie.ucd.bdic.group6.command.game.EndTurnCommand;
import ie.ucd.bdic.group6.command.session.JoinSessionCommand;
import ie.ucd.bdic.group6.command.debt.PayDebtCommand;
import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.command.connection.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.trade.ProposeTradeCommand;
import ie.ucd.bdic.group6.command.connection.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.command.session.RejoinSessionCommand;
import ie.ucd.bdic.group6.command.action.RespondActionCommand;
import ie.ucd.bdic.group6.command.trade.RespondTradeCommand;
import ie.ucd.bdic.group6.command.game.StartGameCommand;
import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.deck.Deck;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.snapshot.CardSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingActionSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingTradeSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PropertyRowSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;
import ie.ucd.bdic.group6.exception.GameException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionExecutorTest {

    @Test
    void createJoinStartHappyPath() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 20));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        String roomCode = text(created, "roomCode");

        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "roomCode", roomCode,
                "playerId", "player2"
        )));
        SessionSnapshot started = startGame(engine, sessionId, "host");

        assertEquals("ACTIVE", started.status());
        assertEquals("host", started.activePlayerId());
        assertEquals(3, started.actionsRemaining());
        assertEquals(7, currentPlayerHand(started).size());
        assertEquals(7, playerSummary(started, "host").handCount());
        assertEquals(5, playerSummary(started, "player2").handCount());
    }

    @Test
    void startGameRejectsInvalidLobbyStates() {
        GameEngine tooFewPlayers = new GameEngine();
        String loneSessionId = text(createSession(tooFewPlayers, "host", 5), "sessionId");
        GameException tooFew = assertThrows(GameException.class,
                () -> startGame(tooFewPlayers, loneSessionId, "host"));
        assertEquals("INVALID_PLAYER_COUNT", tooFew.getErrorCode());

        GameEngine nonHostEngine = new GameEngine();
        SessionSnapshot created = createSession(nonHostEngine, "host", 5);
        nonHostEngine.executeJoinSession(new JoinSessionCommand(Map.of(
                "roomCode", text(created, "roomCode"),
                "playerId", "player2"
        )));
        GameException nonHost = assertThrows(GameException.class,
                () -> startGame(nonHostEngine, text(created, "sessionId"), "player2"));
        assertEquals("HOST_REQUIRED", nonHost.getErrorCode());
    }

    @Test
    void joinSessionRejectsDuplicateAndFullLobby() {
        GameEngine engine = new GameEngine();
        SessionSnapshot created = createSession(engine, "host", 2);
        String roomCode = text(created, "roomCode");

        GameException duplicate = assertThrows(GameException.class,
                () -> engine.executeJoinSession(new JoinSessionCommand(Map.of(
                        "roomCode", roomCode,
                        "playerId", "host"
                ))));
        assertEquals("PLAYER_ALREADY_JOINED", duplicate.getErrorCode());

        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "roomCode", roomCode,
                "playerId", "player2"
        )));
        GameException full = assertThrows(GameException.class,
                () -> engine.executeJoinSession(new JoinSessionCommand(Map.of(
                        "roomCode", roomCode,
                        "playerId", "player3"
                ))));
        assertEquals("SESSION_FULL", full.getErrorCode());
    }

    @Test
    void turnOrderCyclesByListIndex() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        String roomCode = text(created, "roomCode");
        join(engine, roomCode, "player2");
        join(engine, roomCode, "player3");
        startGame(engine, sessionId, "host");

        SessionSnapshot afterHost = endTurn(engine, sessionId, "host");
        assertEquals("player2", afterHost.activePlayerId());
        SessionSnapshot afterPlayer2 = endTurn(engine, sessionId, "player2");
        assertEquals("player3", afterPlayer2.activePlayerId());
        SessionSnapshot afterPlayer3 = endTurn(engine, sessionId, "player3");
        assertEquals("host", afterPlayer3.activePlayerId());
    }

    @Test
    void bankingMoneyMovesCardAndConsumesAction() {
        MoneyCard banked = money("bank-me", 5);
        StartedGame game = startedGameWithHostCards(List.of(banked), moneyCards("fill", 20));

        SessionSnapshot afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", banked.id(),
                "playMode", "BANK"
        )));

        PlayerStateSnapshot host = playerSummary(afterPlay, "host");
        assertEquals(1, afterPlay.actionsUsed());
        assertEquals(6, host.handCount());
        assertEquals(5, host.bankTotalValueM());
        assertEquals(banked.id(), cardIds(bankCards(host)).getFirst());
    }

    @Test
    void placingPropertyCreatesRowAndConsumesAction() {
        PropertyCard red = property("red-1", PropertyColor.RED);
        StartedGame game = startedGameWithHostCards(List.of(red), moneyCards("fill", 20));

        SessionSnapshot afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", red.id()
        )));

        PlayerStateSnapshot host = playerSummary(afterPlay, "host");
        PropertyRowSnapshot row = propertyRows(host).getFirst();
        assertEquals(1, afterPlay.actionsUsed());
        assertEquals("RED", row.anchorColor());
        assertEquals(List.of(red.id()), cardIds(rowCards(row)));
    }

    @Test
    void passGoDrawsTwoCardsAndConsumesAction() {
        ActionCard passGo = passGo("pass-go");
        MoneyCard drawnOne = money("draw-1", 1);
        MoneyCard drawnTwo = money("draw-2", 2);
        StartedGame game = startedGameWithHostCards(List.of(passGo), List.of(drawnOne, drawnTwo));

        SessionSnapshot afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo.id()
        )));

        assertEquals(1, afterPlay.actionsUsed());
        assertEquals(1, afterPlay.discardSize());
        assertEquals(8, playerSummary(afterPlay, "host").handCount());
        assertFalse(cardIds(currentPlayerHand(afterPlay)).contains(passGo.id()));
        assertTrue(cardIds(currentPlayerHand(afterPlay)).containsAll(List.of(drawnOne.id(), drawnTwo.id())));
    }

    @Test
    void drawRulesStopWhenDrawAndDiscardPilesAreExhausted() {
        ActionCard passGo = passGo("pass-go");
        GameEngine engine = engineWithDrawOrder(List.of(
                passGo,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("player2-5", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        SessionSnapshot started = startGame(engine, sessionId, "host");

        assertEquals(5, currentPlayerHand(started).size());
        assertEquals(0, started.deckSize());

        SessionSnapshot afterPassGo = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", passGo.id()
        )));

        assertEquals(1, afterPassGo.actionsUsed());
        assertEquals(1, afterPassGo.discardSize());
        assertEquals(4, playerSummary(afterPassGo, "host").handCount());
        assertEquals(0, afterPassGo.deckSize());
    }

    @Test
    void debtCollectorCreatesPendingDebtAndPayDebtMovesBankCards() {
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard payment = money("pay-5", 5);
        GameEngine engine = engineWithDrawOrder(List.of(
                debtCollector,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                payment,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("host-start-1", 1),
                money("host-start-2", 1),
                money("extra-1", 1),
                money("extra-2", 1),
                money("extra-3", 1),
                money("extra-4", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", payment.id());
        endTurn(engine, sessionId, "player2");
        SessionSnapshot afterDebt = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));

        PendingActionSnapshot pendingAction = pendingActions(afterDebt).getFirst();
        assertEquals("DEBT_COLLECTOR", pendingAction.actionType());
        assertTrue(pendingDebts(afterDebt).isEmpty());

        SessionSnapshot afterResponse = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingAction.actionId(),
                "accepted", true
        )));

        PendingDebtSnapshot debt = pendingDebts(afterResponse).getFirst();
        assertEquals("host", debt.creditorPlayerId());
        assertEquals("player2", debt.debtorPlayerId());
        assertEquals(5, debt.amountM());

        SessionSnapshot afterPay = engine.executePayDebt(new PayDebtCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "debtId", debt.debtId(),
                "paymentCardIds", List.of(payment.id())
        )));

        assertTrue(pendingDebts(afterPay).isEmpty());
        assertEquals(5, playerSummary(afterPay, "host").bankTotalValueM());
        assertEquals(0, playerSummary(afterPay, "player2").bankTotalValueM());
    }

    @Test
    void acceptedMoneyActionWithNoPublicAssetsClearsDebtImmediately() {
        ActionCard debtCollector = debtCollector("debt-collector");
        StartedGame game = startedGameForPlayer2Assets(new MutableClock(), debtCollector, List.of());
        SessionSnapshot afterResponse = acceptDebtCollectorAction(game, debtCollector.id());

        assertTrue(pendingDebts(afterResponse).isEmpty());
        assertEquals(0, playerSummary(afterResponse, "host").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(afterResponse, "host")).isEmpty());
        SessionSnapshot hostView = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        String hostMoneyId = currentPlayerHand(hostView).stream()
                .filter(card -> "MONEY".equals(card.type()))
                .findFirst()
                .orElseThrow()
                .id();
        SessionSnapshot afterHostContinues = play(game.engine(), game.sessionId(), hostMoneyId);
        assertTrue(pendingDebts(afterHostContinues).isEmpty());
    }

    @Test
    void payDebtWithAllInsufficientAssetsClearsDebtAndTransfersThem() {
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard bank = money("bank-2", 2);
        PropertyCard brown = property("brown-pay", PropertyColor.BROWN);
        StartedGame game = startedGameForPlayer2Assets(new MutableClock(), debtCollector, List.of(bank, brown));
        PendingDebtSnapshot debt = acceptedDebtCollectorDebt(game, debtCollector.id());

        SessionSnapshot afterPay = game.engine().executePayDebt(new PayDebtCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2",
                "debtId", debt.debtId(),
                "paymentCardIds", List.of(bank.id(), brown.id())
        )));

        assertTrue(pendingDebts(afterPay).isEmpty());
        assertEquals(2, playerSummary(afterPay, "host").bankTotalValueM());
        assertTrue(hostPropertyCardIds(afterPay).contains(brown.id()));
        assertEquals(0, playerSummary(afterPay, "player2").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(afterPay, "player2")).isEmpty());
    }

    @Test
    void payDebtRejectsInsufficientSelectionWhenMoreAssetsCanBePaid() {
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard bank = money("bank-2", 2);
        PropertyCard brown = property("brown-pay", PropertyColor.BROWN);
        StartedGame game = startedGameForPlayer2Assets(new MutableClock(), debtCollector, List.of(bank, brown));
        PendingDebtSnapshot debt = acceptedDebtCollectorDebt(game, debtCollector.id());

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executePayDebt(new PayDebtCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "player2",
                        "debtId", debt.debtId(),
                        "paymentCardIds", List.of(bank.id())
                ))));

        assertEquals("INSUFFICIENT_PAYMENT", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "player2"
        )));
        assertEquals(1, pendingDebts(snapshot).size());
        assertEquals(2, playerSummary(snapshot, "player2").bankTotalValueM());
        assertTrue(playerPropertyCardIds(snapshot, "player2").contains(brown.id()));
    }

    @Test
    void acceptedMoneyActionWithOnlyZeroValuePropertyClearsDebtWithoutMovingIt() {
        ActionCard debtCollector = debtCollector("debt-collector");
        PropertyWildCard rainbow = wild("rainbow", EnumSet.allOf(PropertyColor.class), null);
        StartedGame game = startedGameForPlayer2Assets(new MutableClock(), debtCollector, List.of(rainbow));
        SessionSnapshot afterResponse = acceptDebtCollectorAction(game, debtCollector.id());

        assertTrue(pendingDebts(afterResponse).isEmpty());
        assertTrue(hostPropertyCardIds(afterResponse).isEmpty());
        assertEquals(List.of(rainbow.id()), playerPropertyCardIds(afterResponse, "player2"));
    }

    @Test
    void justSayNoCancelsPendingActionBeforeDebtIsCreated() {
        ActionCard debtCollector = debtCollector("debt-collector");
        ActionCard justSayNo = action("just-say-no", ActionType.JUST_SAY_NO);
        GameEngine engine = engineWithDrawOrder(List.of(
                debtCollector,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                justSayNo,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("host-start-1", 1),
                money("host-start-2", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        SessionSnapshot pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));
        String actionId = pendingActions(pending).getFirst().actionId();

        SessionSnapshot rejected = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", actionId,
                "accepted", false,
                "justSayNoCardId", justSayNo.id()
        )));

        assertTrue(pendingActions(rejected).isEmpty());
        assertTrue(pendingDebts(rejected).isEmpty());
        assertEquals(2, rejected.discardSize());
        assertFalse(cardIds(currentPlayerHand(rejected)).contains(justSayNo.id()));
    }

    @Test
    void doubleRentCreatesDoubledPendingDebtAfterTargetAccepts() {
        PropertyCard brown = property("brown-rent", PropertyColor.BROWN);
        ActionCard rent = rent("rent-brown-blue", ActionType.RENT_BROWN_OR_LIGHT_BLUE,
                PropertyColor.BROWN, PropertyColor.LIGHT_BLUE);
        ActionCard doubleRent = action("double-rent", ActionType.DOUBLE_THE_RENT);
        MoneyCard player2Money = money("player2-money", 1);
        StartedGame game = startedGameWithHands(
                List.of(brown, rent, doubleRent),
                List.of(player2Money),
                moneyCards("fill", 20),
                new MutableClock());

        play(game.engine(), game.sessionId(), brown.id());
        endTurn(game.engine(), game.sessionId(), "host");
        playAs(game.engine(), game.sessionId(), "player2", player2Money.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        SessionSnapshot pending = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", rent.id(),
                "propertyColor", "BROWN",
                "doubleRentCardId", doubleRent.id()
        )));

        assertEquals(2, pending.actionsUsed());
        SessionSnapshot afterAccept = game.engine().executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().actionId(),
                "accepted", true
        )));

        assertEquals(2, pendingDebts(afterAccept).getFirst().amountM());
        assertEquals(2, afterAccept.discardSize());
    }

    @Test
    void slyDealTransfersSinglePropertyAfterAcceptance() {
        ActionCard slyDeal = action("sly", ActionType.SLY_DEAL);
        PropertyCard red = property("target-red", PropertyColor.RED);
        GameEngine engine = engineWithDrawOrder(List.of(
                slyDeal,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                red,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("host-start-1", 1),
                money("host-start-2", 1),
                money("player2-start-1", 1),
                money("player2-start-2", 1),
                money("host-next-1", 1),
                money("host-next-2", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", red.id());
        endTurn(engine, sessionId, "player2");

        SessionSnapshot pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", slyDeal.id(),
                "targetPlayerId", "player2",
                "targetCardId", red.id()
        )));
        SessionSnapshot accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().actionId(),
                "accepted", true
        )));

        assertEquals(List.of(red.id()), cardIds(rowCards(propertyRows(playerSummary(accepted, "host")).getFirst())));
        assertTrue(propertyRows(playerSummary(accepted, "player2")).isEmpty());
    }

    @Test
    void forcedDealSwapsPropertiesAfterAcceptance() {
        ActionCard forcedDeal = action("forced", ActionType.FORCED_DEAL);
        PropertyCard hostBlue = property("host-blue", PropertyColor.LIGHT_BLUE);
        PropertyCard targetRed = property("target-red", PropertyColor.RED);
        GameEngine engine = engineWithDrawOrder(List.of(
                forcedDeal,
                hostBlue,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                targetRed,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("host-start-1", 1),
                money("host-start-2", 1),
                money("player2-start-1", 1),
                money("player2-start-2", 1),
                money("host-next-1", 1),
                money("host-next-2", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");
        play(engine, sessionId, hostBlue.id());
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", targetRed.id());
        endTurn(engine, sessionId, "player2");

        SessionSnapshot pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", forcedDeal.id(),
                "targetPlayerId", "player2",
                "offeredCardId", hostBlue.id(),
                "requestedCardId", targetRed.id()
        )));
        SessionSnapshot accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().actionId(),
                "accepted", true
        )));

        assertEquals(List.of(targetRed.id()), cardIds(rowCards(propertyRows(playerSummary(accepted, "host")).getFirst())));
        assertEquals(List.of(hostBlue.id()), cardIds(rowCards(propertyRows(playerSummary(accepted, "player2")).getFirst())));
    }

    @Test
    void dealBreakerTransfersCompleteSetAfterAcceptance() {
        ActionCard dealBreaker = action("deal-breaker", ActionType.DEAL_BREAKER);
        PropertyCard brown1 = property("brown-1", PropertyColor.BROWN);
        PropertyCard brown2 = property("brown-2", PropertyColor.BROWN);
        GameEngine engine = engineWithDrawOrder(List.of(
                dealBreaker,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                brown1,
                brown2,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("host-start-1", 1),
                money("host-start-2", 1),
                money("player2-start-1", 1),
                money("player2-start-2", 1),
                money("host-next-1", 1),
                money("host-next-2", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", brown1.id());
        SessionSnapshot player2Board = playAs(engine, sessionId, "player2", brown2.id());
        String rowId = propertyRows(playerSummary(player2Board, "player2")).getFirst().rowId();
        endTurn(engine, sessionId, "player2");

        SessionSnapshot pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", dealBreaker.id(),
                "targetPlayerId", "player2",
                "targetPropertyRowId", rowId
        )));
        SessionSnapshot accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().actionId(),
                "accepted", true
        )));

        assertTrue(propertyRows(playerSummary(accepted, "host")).getFirst().complete());
        assertTrue(propertyRows(playerSummary(accepted, "player2")).isEmpty());
    }

    @Test
    void acceptedTradeMovesMoneyAndPropertyAtomically() {
        PropertyCard red = property("red-trade", PropertyColor.RED);
        MoneyCard payment = money("trade-money", 3);
        GameEngine engine = engineWithDrawOrder(List.of(
                red,
                money("host-1", 1),
                money("host-2", 1),
                money("host-3", 1),
                money("host-4", 1),
                payment,
                money("player2-1", 1),
                money("player2-2", 1),
                money("player2-3", 1),
                money("player2-4", 1),
                money("host-start-1", 1),
                money("host-start-2", 1),
                money("host-next-1", 1),
                money("host-next-2", 1),
                money("extra-1", 1),
                money("extra-2", 1),
                money("player2-next-1", 1),
                money("player2-next-2", 1)
        ));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", payment.id());
        endTurn(engine, sessionId, "player2");
        playAs(engine, sessionId, "host", red.id());
        playAs(engine, sessionId, "host", "host-1");
        SessionSnapshot proposed = engine.executeProposeTrade(new ProposeTradeCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "targetPlayerId", "player2",
                "offeredCardIds", List.of(red.id()),
                "requestedCardIds", List.of(payment.id())
        )));
        String tradeId = pendingTrades(proposed).getFirst().tradeId();
        assertEquals(2, proposed.actionsUsed());
        assertEquals("host", proposed.activePlayerId());
        assertEquals("RESPONDING", playerSummary(proposed, "player2").playerPhase());
        assertEquals("ACTING", playerSummary(proposed, "host").playerPhase());

        SessionSnapshot accepted = engine.executeRespondTrade(new RespondTradeCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "tradeId", tradeId,
                "accepted", true
        )));

        assertTrue(pendingTrades(accepted).isEmpty());
        assertEquals("host", accepted.activePlayerId());
        assertEquals(2, accepted.actionsUsed());
        assertEquals(4, playerSummary(accepted, "host").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(accepted, "host")).isEmpty());
        assertEquals(List.of(red.id()), cardIds(rowCards(propertyRows(playerSummary(accepted, "player2")).getFirst())));
    }

    @Test
    void proposeTradeRejectsBankedActionAsSimplifiedMoney() {
        PropertyCard red = property("red-trade", PropertyColor.RED);
        ActionCard bankedAction = action("banked-action", ActionType.PASS_GO);
        StartedGame game = startedGameWithHands(
                List.of(red),
                List.of(bankedAction),
                moneyCards("fill", 30),
                new MutableClock());

        endTurn(game.engine(), game.sessionId(), "host");
        bankAs(game.engine(), game.sessionId(), "player2", bankedAction.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        playAs(game.engine(), game.sessionId(), "host", red.id());

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeProposeTrade(new ProposeTradeCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "targetPlayerId", "player2",
                        "offeredCardIds", List.of(red.id()),
                        "requestedCardIds", List.of(bankedAction.id())
                ))));

        assertEquals("CARD_NOT_TRADEABLE", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertTrue(pendingTrades(snapshot).isEmpty());
        assertTrue(hostPropertyCardIds(snapshot).contains(red.id()));
    }

    @Test
    void proposeTradeRejectsBankedWildAsSimplifiedMoney() {
        PropertyCard red = property("red-trade", PropertyColor.RED);
        PropertyWildCard bankedWild = wild(
                "banked-wild",
                EnumSet.of(PropertyColor.BROWN, PropertyColor.LIGHT_BLUE),
                1);
        StartedGame game = startedGameWithHands(
                List.of(red),
                List.of(bankedWild),
                moneyCards("fill", 30),
                new MutableClock());

        endTurn(game.engine(), game.sessionId(), "host");
        bankAs(game.engine(), game.sessionId(), "player2", bankedWild.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        playAs(game.engine(), game.sessionId(), "host", red.id());

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeProposeTrade(new ProposeTradeCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "targetPlayerId", "player2",
                        "offeredCardIds", List.of(red.id()),
                        "requestedCardIds", List.of(bankedWild.id())
                ))));

        assertEquals("CARD_NOT_TRADEABLE", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertTrue(pendingTrades(snapshot).isEmpty());
        assertTrue(hostPropertyCardIds(snapshot).contains(red.id()));
    }

    @Test
    void proposeTradeRejectsMultipleCardsOnEitherSide() {
        PropertyCard red = property("red-trade", PropertyColor.RED);
        PropertyCard yellow = property("yellow-trade", PropertyColor.YELLOW);
        MoneyCard payment = money("trade-money", 3);
        StartedGame game = startedGameWithHands(
                List.of(red, yellow),
                List.of(payment),
                moneyCards("fill", 30),
                new MutableClock());

        endTurn(game.engine(), game.sessionId(), "host");
        playAs(game.engine(), game.sessionId(), "player2", payment.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        playAs(game.engine(), game.sessionId(), "host", red.id());
        playAs(game.engine(), game.sessionId(), "host", yellow.id());

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeProposeTrade(new ProposeTradeCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "targetPlayerId", "player2",
                        "offeredCardIds", List.of(red.id(), yellow.id()),
                        "requestedCardIds", List.of(payment.id())
                ))));

        assertEquals("UNSUPPORTED_TRADE_CARD_COUNT", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertTrue(pendingTrades(snapshot).isEmpty());
        assertTrue(hostPropertyCardIds(snapshot).containsAll(List.of(red.id(), yellow.id())));
    }

    @Test
    void activeTradeTargetMustRespondBeforeContinuingTurn() {
        PropertyCard offered = property("offered-property", PropertyColor.RED);
        MoneyCard hostDiscard = money("host-discard", 1);
        MoneyCard requested = money("requested-money", 3);
        MoneyCard player2Play = money("player2-play", 1);
        StartedGame game = startedGameWithHands(
                List.of(offered, hostDiscard),
                List.of(requested, player2Play),
                moneyCards("fill", 30),
                new MutableClock());

        endTurn(game.engine(), game.sessionId(), "host");
        bankAs(game.engine(), game.sessionId(), "player2", requested.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        playAs(game.engine(), game.sessionId(), "host", offered.id());
        SessionSnapshot proposed = game.engine().executeProposeTrade(new ProposeTradeCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "targetPlayerId", "player2",
                "offeredCardIds", List.of(offered.id()),
                "requestedCardIds", List.of(requested.id())
        )));
        String tradeId = pendingTrades(proposed).getFirst().tradeId();
        endTurn(game.engine(), game.sessionId(), "host");
        game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(hostDiscard.id())
        )));

        GameException playError = assertThrows(GameException.class,
                () -> playAs(game.engine(), game.sessionId(), "player2", player2Play.id()));
        GameException discardError = assertThrows(GameException.class,
                () -> game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "player2",
                        "discardedCardIds", List.of(player2Play.id())
                ))));
        GameException endError = assertThrows(GameException.class,
                () -> endTurn(game.engine(), game.sessionId(), "player2"));

        assertEquals("TRADE_RESPONSE_REQUIRED", playError.getErrorCode());
        assertEquals("TRADE_RESPONSE_REQUIRED", discardError.getErrorCode());
        assertEquals("TRADE_RESPONSE_REQUIRED", endError.getErrorCode());

        game.engine().executeRespondTrade(new RespondTradeCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2",
                "tradeId", tradeId,
                "accepted", false
        )));
        SessionSnapshot afterPlay = playAs(game.engine(), game.sessionId(), "player2", player2Play.id());
        assertEquals(1, afterPlay.actionsUsed());
    }

    @Test
    void discardRejectsWhileActionResponseIsPending() {
        ActionCard passGo1 = passGo("pass-go-1");
        ActionCard passGo2 = passGo("pass-go-2");
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard discard = money("discard", 1);
        StartedGame game = startedGameWithHostCards(
                List.of(passGo1, passGo2, debtCollector, discard),
                moneyCards("draw", 30));

        play(game.engine(), game.sessionId(), passGo1.id());
        play(game.engine(), game.sessionId(), passGo2.id());
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "discardedCardIds", List.of(discard.id())
                ))));

        assertEquals("ACTION_RESPONSE_REQUIRED", error.getErrorCode());
    }

    @Test
    void discardRejectsWhileDebtPaymentIsPending() {
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard payment = money("payment", 2);
        StartedGame game = startedGameForPlayer2Assets(new MutableClock(), debtCollector, List.of(payment));
        acceptedDebtCollectorDebt(game, debtCollector.id());
        SessionSnapshot hostView = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        String hostCardId = currentPlayerHand(hostView).getFirst().id();

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "discardedCardIds", List.of(hostCardId)
                ))));

        assertEquals("PAYMENT_REQUIRED", error.getErrorCode());
    }

    @Test
    void selectedPropertyRowRejectsIncompatibleCardWithoutAutoCreatingRow() {
        PropertyCard red = property("red", PropertyColor.RED);
        PropertyCard blue = property("blue", PropertyColor.LIGHT_BLUE);
        StartedGame game = startedGameWithHostCards(List.of(red, blue), moneyCards("fill", 20));

        play(game.engine(), game.sessionId(), red.id());
        GameException error = assertThrows(GameException.class,
                () -> game.engine().executePlayCard(new PlayCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "cardId", blue.id(),
                        "propertyRowIndex", 0
                ))));

        assertEquals("RECEIVER_ROW_REJECTS_CARD", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertEquals(1, propertyRows(playerSummary(snapshot, "host")).size());
        assertTrue(cardIds(currentPlayerHand(snapshot)).contains(blue.id()));
    }

    @Test
    void improvementCardsCanTargetStablePropertyRowId() {
        PropertyCard brown1 = property("brown-1", PropertyColor.BROWN);
        PropertyCard brown2 = property("brown-2", PropertyColor.BROWN);
        PropertyCard darkBlue1 = property("dark-blue-1", PropertyColor.DARK_BLUE);
        PropertyCard darkBlue2 = property("dark-blue-2", PropertyColor.DARK_BLUE);
        ActionCard house = action("house", ActionType.HOUSE);
        StartedGame game = startedGameWithHostCards(
                List.of(brown1, brown2, darkBlue1, darkBlue2, house),
                moneyCards("fill", 20));

        play(game.engine(), game.sessionId(), brown1.id());
        SessionSnapshot afterBrown = play(game.engine(), game.sessionId(), brown2.id());
        play(game.engine(), game.sessionId(), darkBlue1.id());
        String brownRowId = propertyRows(playerSummary(afterBrown, "host")).stream()
                .filter(row -> "BROWN".equals(row.anchorColor()))
                .findFirst()
                .orElseThrow()
                .rowId();
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), darkBlue2.id());

        SessionSnapshot afterHouse = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", house.id(),
                "targetPropertyRowId", brownRowId
        )));

        PropertyRowSnapshot brownRow = propertyRows(playerSummary(afterHouse, "host")).stream()
                .filter(row -> brownRowId.equals(row.rowId()))
                .findFirst()
                .orElseThrow();
        PropertyRowSnapshot darkBlueRow = propertyRows(playerSummary(afterHouse, "host")).stream()
                .filter(row -> "DARK_BLUE".equals(row.anchorColor()))
                .findFirst()
                .orElseThrow();
        assertTrue(brownRow.hasHouse());
        assertFalse(darkBlueRow.hasHouse());
    }

    @Test
    void previousPlayerCannotPlayAfterThirdActionAutomaticallyAdvancesTurn() {
        List<Card> hostCards = List.of(
                money("m1", 1),
                money("m2", 1),
                money("m3", 1),
                money("m4", 1)
        );
        StartedGame game = startedGameWithHostCards(hostCards, moneyCards("fill", 20));

        for (int i = 1; i <= 3; i++) {
            game.engine().executePlayCard(new PlayCardCommand(Map.of(
                    "sessionId", game.sessionId(),
                    "playerId", "host",
                    "cardId", "m" + i
            )));
        }
        GameException error = assertThrows(GameException.class,
                () -> game.engine().executePlayCard(new PlayCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "cardId", "m4"
                ))));

        assertEquals("NOT_ACTIVE_PLAYER", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertTrue(cardIds(currentPlayerHand(snapshot)).contains("m4"));
        assertEquals(3, bankCards(playerSummary(snapshot, "host")).size());
    }

    @Test
    void thirdActionAutomaticallyAdvancesTurnInEngine() {
        StartedGame game = startedGameWithHostCards(
                List.of(money("m1", 1), money("m2", 1), money("m3", 1)),
                moneyCards("fill", 20));

        play(game.engine(), game.sessionId(), "m1");
        play(game.engine(), game.sessionId(), "m2");
        SessionSnapshot afterThirdAction = play(game.engine(), game.sessionId(), "m3");

        assertEquals("player2", afterThirdAction.activePlayerId());
        assertEquals("ACTING", playerSummary(afterThirdAction, "player2").playerPhase());
        assertEquals("WAITING", playerSummary(afterThirdAction, "host").playerPhase());
    }

    @Test
    void endTurnWithLargeHandEntersEngineOwnedDiscardPhase() {
        ActionCard passGo = passGo("pass-go");
        StartedGame game = startedGameWithHostCards(List.of(passGo, money("discard", 1)), moneyCards("draw", 20));
        play(game.engine(), game.sessionId(), passGo.id());

        SessionSnapshot discardPhase = endTurn(game.engine(), game.sessionId(), "host");

        assertEquals("host", discardPhase.activePlayerId());
        assertEquals("DISCARDING", playerSummary(discardPhase, "host").playerPhase());
    }

    @Test
    void finalDiscardAutomaticallyAdvancesTurnInEngine() {
        ActionCard passGo = passGo("pass-go");
        MoneyCard discard = money("discard", 1);
        StartedGame game = startedGameWithHostCards(List.of(passGo, discard), moneyCards("draw", 20));
        play(game.engine(), game.sessionId(), passGo.id());
        endTurn(game.engine(), game.sessionId(), "host");

        SessionSnapshot afterDiscard = game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(discard.id())
        )));

        assertEquals("player2", afterDiscard.activePlayerId());
        assertEquals("ACTING", playerSummary(afterDiscard, "player2").playerPhase());
    }

    @Test
    void endTurnWithLargeHandEntersDiscardPhaseAndFinalDiscardAdvances() {
        ActionCard passGo = passGo("pass-go");
        MoneyCard discard = money("discard-me", 1);
        StartedGame game = startedGameWithHostCards(List.of(passGo, discard), moneyCards("draw", 20));
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo.id()
        )));

        SessionSnapshot discardPhase = endTurn(game.engine(), game.sessionId(), "host");
        assertEquals("DISCARDING", playerSummary(discardPhase, "host").playerPhase());

        SessionSnapshot afterDiscard = game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(discard.id())
        )));
        assertEquals(2, afterDiscard.discardSize());
        assertEquals("player2", afterDiscard.activePlayerId());
    }

    @Test
    void discardCommandCanMakePartialProgressTowardSevenCards() {
        ActionCard passGo1 = passGo("pass-go-1");
        ActionCard passGo2 = passGo("pass-go-2");
        MoneyCard discard1 = money("discard-one", 1);
        MoneyCard discard2 = money("discard-two", 1);
        StartedGame game = startedGameWithHostCards(
                List.of(passGo1, passGo2, discard1, discard2, money("keep", 1)),
                moneyCards("draw", 20));

        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo1.id()
        )));
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo2.id()
        )));
        endTurn(game.engine(), game.sessionId(), "host");

        SessionSnapshot afterOneDiscard = game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(discard1.id())
        )));

        assertEquals(8, currentPlayerHand(afterOneDiscard).size());
        assertFalse(cardIds(currentPlayerHand(afterOneDiscard)).contains(discard1.id()));
        assertEquals(3, afterOneDiscard.discardSize());

        SessionSnapshot afterEnd = game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(discard2.id())
        )));
        assertEquals("player2", afterEnd.activePlayerId());
    }

    @Test
    void discardCommandCannotDropHandBelowSevenCards() {
        ActionCard passGo1 = passGo("pass-go-1");
        ActionCard passGo2 = passGo("pass-go-2");
        MoneyCard discard1 = money("discard-one", 1);
        MoneyCard discard2 = money("discard-two", 1);
        MoneyCard keep = money("keep", 1);
        StartedGame game = startedGameWithHostCards(
                List.of(passGo1, passGo2, discard1, discard2, keep),
                moneyCards("draw", 20));

        play(game.engine(), game.sessionId(), passGo1.id());
        play(game.engine(), game.sessionId(), passGo2.id());
        endTurn(game.engine(), game.sessionId(), "host");

        GameException error = assertThrows(GameException.class,
                () -> game.engine().executeDiscardCards(new DiscardCardCommand(Map.of(
                        "sessionId", game.sessionId(),
                        "playerId", "host",
                        "discardedCardIds", List.of(discard1.id(), discard2.id(), keep.id())
                ))));

        assertEquals("TOO_MANY_DISCARDS", error.getErrorCode());
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertEquals(9, currentPlayerHand(snapshot).size());
        assertEquals("DISCARDING", playerSummary(snapshot, "host").playerPhase());
    }

    @Test
    void endTurnAdvancesActivePlayerAndAppliesDrawRule() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 30));
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        SessionSnapshot afterEnd = endTurn(engine, sessionId, "host");

        assertEquals("player2", afterEnd.activePlayerId());
        assertEquals(7, playerSummary(afterEnd, "player2").handCount());
    }

    @Test
    void completingThreePropertySetsOnThirdActionFinishesImmediately() {
        List<Card> hostCards = List.of(
                property("brown-1", PropertyColor.BROWN),
                property("dark-blue-1", PropertyColor.DARK_BLUE),
                property("utility-1", PropertyColor.UTILITY),
                property("brown-2", PropertyColor.BROWN),
                property("dark-blue-2", PropertyColor.DARK_BLUE),
                property("utility-2", PropertyColor.UTILITY)
        );
        StartedGame game = startedGameWithHostCards(hostCards, moneyCards("fill", 30));

        play(game.engine(), game.sessionId(), "brown-1");
        play(game.engine(), game.sessionId(), "dark-blue-1");
        play(game.engine(), game.sessionId(), "utility-1");
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), "brown-2");
        play(game.engine(), game.sessionId(), "dark-blue-2");
        SessionSnapshot afterThirdSet = play(game.engine(), game.sessionId(), "utility-2");

        assertEquals("FINISHED", afterThirdSet.status());
        assertEquals("host", afterThirdSet.winnerPlayerId());
        assertEquals("host", afterThirdSet.activePlayerId());
    }

    @Test
    void repeatedSnapshotRequestsDoNotChangeGameState() {
        List<Card> hostCards = List.of(
                property("brown-1", PropertyColor.BROWN),
                property("dark-blue-1", PropertyColor.DARK_BLUE),
                property("utility-1", PropertyColor.UTILITY),
                property("brown-2", PropertyColor.BROWN),
                property("dark-blue-2", PropertyColor.DARK_BLUE),
                property("utility-2", PropertyColor.UTILITY)
        );
        StartedGame game = startedGameWithHostCards(hostCards, moneyCards("fill", 30));

        play(game.engine(), game.sessionId(), "brown-1");
        play(game.engine(), game.sessionId(), "dark-blue-1");
        play(game.engine(), game.sessionId(), "utility-1");
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), "brown-2");
        play(game.engine(), game.sessionId(), "dark-blue-2");
        SessionSnapshot afterThirdSet = play(game.engine(), game.sessionId(), "utility-2");

        SessionSnapshot firstSnapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        SessionSnapshot secondSnapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));

        assertEquals(firstSnapshot, secondSnapshot);
        assertEquals("FINISHED", secondSnapshot.status());
        assertEquals("host", secondSnapshot.winnerPlayerId());
        assertEquals(afterThirdSet.activePlayerId(), secondSnapshot.activePlayerId());
        assertEquals(afterThirdSet.actionsUsed(), secondSnapshot.actionsUsed());
        assertEquals(afterThirdSet.discardSize(), secondSnapshot.discardSize());
        assertTrue(pendingDebts(secondSnapshot).isEmpty());
        assertTrue(pendingActions(secondSnapshot).isEmpty());
    }

    @Test
    void rejoinRejectsUnknownPlayerId() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 20));
        SessionSnapshot created = createSession(engine, "host", 5);
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, text(created, "sessionId"), "host");

        GameException error = assertThrows(GameException.class,
                () -> engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                        "sessionIdentity", text(created, "sessionId"),
                        "playerId", "intruder"
                ))));
        assertEquals("PLAYER_NOT_FOUND", error.getErrorCode());
    }

    @Test
    void rejoinAfterExpiredActiveWindowForfeitsAndRejectsPlayer() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(91));

        GameException error = assertThrows(GameException.class,
                () -> engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                        "sessionIdentity", sessionId,
                        "playerId", "player2"
                ))));

        assertEquals("PLAYER_FORFEITED", error.getErrorCode());
        SessionSnapshot snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "host"
        )));
        assertEquals(List.of("player2"), snapshot.forfeitedPlayerIds());
        assertEquals("FINISHED", snapshot.status());
    }

    @Test
    void lobbyDisconnectTimeoutDoesNotForfeitPlayer() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 20), clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(91));
        Map<String, Object> processed = engine.executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "host"
        )));

        assertEquals(List.of(), processed.get("sessionIds"));
        assertEquals("LOBBY", snapshot.status());
        assertTrue(snapshot.forfeitedPlayerIds().isEmpty());
        assertEquals("DISCONNECTED", playerSummary(snapshot, "player2").playerStatus());
    }

    @Test
    void disconnectedActivePlayerIsSkippedAfterThirtySeconds() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        )));
        clock.advance(Duration.ofSeconds(31));
        Map<String, Object> processed = engine.executeProcessDisconnectTimeouts(
                new ProcessDisconnectTimeoutsCommand(Map.of()));

        assertEquals(List.of(sessionId), processed.get("sessionIds"));
        SessionSnapshot snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "player2"
        )));
        assertEquals("player2", snapshot.activePlayerId());
        assertEquals("DISCONNECTED", playerSummary(snapshot, "host").playerStatus());
    }

    @Test
    void activeTimeoutSkipAdvancesAndDrawsWhenNoWinnerExists() {
        MutableClock clock = new MutableClock();
        List<Card> hostCards = List.of(
                property("brown-1", PropertyColor.BROWN),
                property("dark-blue-1", PropertyColor.DARK_BLUE),
                property("utility-1", PropertyColor.UTILITY),
                property("brown-2", PropertyColor.BROWN),
                property("dark-blue-2", PropertyColor.DARK_BLUE),
                property("utility-2", PropertyColor.UTILITY)
        );
        StartedGame game = startedGameWithHostCards(hostCards, moneyCards("fill", 30), clock);

        play(game.engine(), game.sessionId(), "brown-1");
        play(game.engine(), game.sessionId(), "dark-blue-1");
        play(game.engine(), game.sessionId(), "utility-1");
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), "brown-2");
        play(game.engine(), game.sessionId(), "dark-blue-2");
        SessionSnapshot beforeSkip = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        int player2HandBeforeSkip = playerSummary(beforeSkip, "player2").handCount();

        game.engine().executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host"
        )));
        clock.advance(Duration.ofSeconds(31));
        game.engine().executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "player2"
        )));

        assertEquals("ACTIVE", snapshot.status());
        assertEquals("", snapshot.winnerPlayerId());
        assertEquals("player2", snapshot.activePlayerId());
        assertEquals(player2HandBeforeSkip + 2, playerSummary(snapshot, "player2").handCount());
    }

    @Test
    void thirtySecondAutoSettleCompletesThirdActionAndTriggersVictoryScan() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        PropertyCard hostBrown1 = property("host-brown-1", PropertyColor.BROWN);
        PropertyCard hostBrown2 = property("host-brown-2", PropertyColor.BROWN);
        PropertyCard hostBlue1 = property("host-blue-1", PropertyColor.DARK_BLUE);
        PropertyCard hostBlue2 = property("host-blue-2", PropertyColor.DARK_BLUE);
        PropertyCard hostUtility1 = property("host-utility-1", PropertyColor.UTILITY);
        PropertyCard targetUtility2 = property("target-utility-2", PropertyColor.UTILITY);
        StartedGame game = startedGameWithHands(
                List.of(hostBrown1, hostBrown2, hostBlue1, hostBlue2, hostUtility1, debtCollector),
                List.of(targetUtility2),
                moneyCards("fill", 30),
                clock);

        play(game.engine(), game.sessionId(), hostBrown1.id());
        play(game.engine(), game.sessionId(), hostBrown2.id());
        play(game.engine(), game.sessionId(), hostBlue1.id());
        playAs(game.engine(), game.sessionId(), "player2", targetUtility2.id());
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), hostBlue2.id());
        play(game.engine(), game.sessionId(), hostUtility1.id());
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));

        SessionSnapshot snapshot = disconnectPlayer2AndProcess(game, clock);

        assertEquals("FINISHED", snapshot.status());
        assertEquals("host", snapshot.winnerPlayerId());
        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(3, propertyRows(playerSummary(snapshot, "host")).stream()
                .filter(PropertyRowSnapshot::complete)
                .count());
    }

    @Test
    void activeTimeoutAutoDiscardsFromBackAndPreservesFirstSevenCards() {
        MutableClock clock = new MutableClock();
        ActionCard passGoOne = passGo("pass-go-1");
        ActionCard passGoTwo = passGo("pass-go-2");
        List<Card> hostCards = List.of(
                passGoOne,
                passGoTwo,
                money("keep-1", 1),
                money("keep-2", 1),
                money("keep-3", 1),
                money("keep-4", 1),
                money("keep-5", 1)
        );
        StartedGame game = startedGameWithHostCards(hostCards, moneyCards("draw", 30), clock);
        play(game.engine(), game.sessionId(), passGoOne.id());
        SessionSnapshot beforeSkip = play(game.engine(), game.sessionId(), passGoTwo.id());
        List<String> beforeIds = cardIds(currentPlayerHand(beforeSkip));

        game.engine().executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host"
        )));
        clock.advance(Duration.ofSeconds(31));
        game.engine().executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));

        assertEquals(9, beforeIds.size());
        assertEquals(beforeIds.subList(0, 7), cardIds(currentPlayerHand(snapshot)));
        assertEquals("player2", snapshot.activePlayerId());
        assertEquals(4, snapshot.discardSize());
    }

    @Test
    void thirtySecondDisconnectDoesNotAutoDiscardNonActivePlayer() throws Exception {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");
        GameSession session = sessionById(engine, sessionId);
        Player player2 = session.findPlayer("player2").orElseThrow();
        player2.addToHand(money("overflow-1", 1));
        player2.addToHand(money("overflow-2", 1));
        player2.addToHand(money("overflow-3", 1));
        List<String> beforeHandIds = player2.handView().stream().map(Card::id).toList();

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(31));
        engine.executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "player2"
        )));

        assertEquals("host", snapshot.activePlayerId());
        assertEquals(beforeHandIds, cardIds(currentPlayerHand(snapshot)));
        assertEquals(8, currentPlayerHand(snapshot).size());
        assertEquals(0, snapshot.discardSize());
    }

    @Test
    void disconnectedTargetAutoRejectsPropertyInterferenceActionWithoutReturningCard() {
        MutableClock clock = new MutableClock();
        ActionCard slyDeal = action("sly", ActionType.SLY_DEAL);
        PropertyCard red = property("target-red", PropertyColor.RED);
        StartedGame game = startedGameWithHands(List.of(slyDeal), List.of(red), moneyCards("fill", 30), clock);
        endTurn(game.engine(), game.sessionId(), "host");
        playAs(game.engine(), game.sessionId(), "player2", red.id());
        endTurn(game.engine(), game.sessionId(), "player2");

        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", slyDeal.id(),
                "targetPlayerId", "player2",
                "targetCardId", red.id()
        )));
        SessionSnapshot snapshot = disconnectPlayer2AndProcess(game, clock);

        assertTrue(pendingActions(snapshot).isEmpty());
        assertEquals(1, snapshot.discardSize());
        assertTrue(cardIds(rowCards(propertyRows(playerSummary(snapshot, "player2")).getFirst())).contains(red.id()));
        assertTrue(propertyRows(playerSummary(snapshot, "host")).isEmpty());
    }

    @Test
    void disconnectedPlayerForfeitsAfterNinetySecondsAndCardsAreDiscarded() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, sessionId, "host");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(91));
        engine.executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "host"
        )));

        assertEquals("FINISHED", snapshot.status());
        assertEquals("host", snapshot.winnerPlayerId());
        assertEquals(List.of("player2"), snapshot.forfeitedPlayerIds());
        assertEquals("DEFEATED", playerSummary(snapshot, "player2").playerStatus());
        assertEquals(5, snapshot.discardSize());
    }

    @Test
    void ninetySecondForfeitDoesNotBypassThirtySecondAutoSettle() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard payment = money("payment-5", 5);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(payment));
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));

        game.engine().executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(91));
        game.engine().executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        SessionSnapshot snapshot = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));

        assertEquals(5, playerSummary(snapshot, "host").bankTotalValueM());
        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(List.of("player2"), snapshot.forfeitedPlayerIds());
    }

    @Test
    void autoPayUsesOnlyMinimalBankOverpayWhenBankCanCover() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard exact = money("bank-5", 5);
        MoneyCard two = money("bank-2", 2);
        MoneyCard four = money("bank-4", 4);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(exact, two, four));

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(5, playerSummary(snapshot, "host").bankTotalValueM());
        assertEquals(6, playerSummary(snapshot, "player2").bankTotalValueM());
        assertEquals(List.of(two.id(), four.id()), cardIds(bankCards(playerSummary(snapshot, "player2"))));
    }

    @Test
    void autoPayUsesAllBankThenIncompleteProperties() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard bank = money("bank-1", 1);
        PropertyCard red = property("red-1", PropertyColor.RED);
        PropertyCard brown = property("brown-1", PropertyColor.BROWN);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(bank, red, brown));

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(1, playerSummary(snapshot, "host").bankTotalValueM());
        assertTrue(hostPropertyCardIds(snapshot).containsAll(List.of(red.id(), brown.id())));
        assertEquals(0, playerSummary(snapshot, "player2").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(snapshot, "player2")).isEmpty());
    }

    @Test
    void autoPayDoesNotTouchCompletePropertiesWhenIncompleteCanCover() {
        MutableClock clock = new MutableClock();
        ActionCard birthday = action("birthday", ActionType.ITS_MY_BIRTHDAY);
        PropertyCard red = property("red-1", PropertyColor.RED);
        PropertyCard brown1 = property("brown-1", PropertyColor.BROWN);
        PropertyCard brown2 = property("brown-2", PropertyColor.BROWN);
        StartedGame game = startedGameForPlayer2Assets(clock, birthday, List.of(red, brown1, brown2));

        SessionSnapshot snapshot = triggerBirthdayAndAutoSettlePlayer2(game, clock, birthday.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertTrue(hostPropertyCardIds(snapshot).contains(red.id()));
        assertFalse(hostPropertyCardIds(snapshot).contains(brown1.id()));
        assertFalse(hostPropertyCardIds(snapshot).contains(brown2.id()));
        assertEquals(List.of(brown1.id(), brown2.id()),
                cardIds(rowCards(propertyRows(playerSummary(snapshot, "player2")).getFirst())));
    }

    @Test
    void autoPayUsesCompletePropertiesOnlyAfterIncompleteCannotCover() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        PropertyCard brown = property("brown-1", PropertyColor.BROWN);
        PropertyCard darkBlue1 = property("dark-blue-1", PropertyColor.DARK_BLUE);
        PropertyCard darkBlue2 = property("dark-blue-2", PropertyColor.DARK_BLUE);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(brown, darkBlue1, darkBlue2));

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertTrue(hostPropertyCardIds(snapshot).containsAll(List.of(brown.id(), darkBlue1.id())));
        assertFalse(hostPropertyCardIds(snapshot).contains(darkBlue2.id()));
        assertTrue(playerPropertyCardIds(snapshot, "player2").contains(darkBlue2.id()));
    }

    @Test
    void autoPayTransfersAllAssetsWhenStillInsufficient() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        MoneyCard bank = money("bank-1", 1);
        PropertyCard brown = property("brown-1", PropertyColor.BROWN);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(bank, brown));

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(1, playerSummary(snapshot, "host").bankTotalValueM());
        assertTrue(hostPropertyCardIds(snapshot).contains(brown.id()));
        assertEquals(0, playerSummary(snapshot, "player2").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(snapshot, "player2")).isEmpty());
    }

    @Test
    void autoPayClearsDebtWhenDebtorHasNoAssets() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of());

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertEquals(0, playerSummary(snapshot, "host").bankTotalValueM());
        assertTrue(propertyRows(playerSummary(snapshot, "host")).isEmpty());
    }

    @Test
    void autoPayIgnoresZeroFaceValueProperty() {
        MutableClock clock = new MutableClock();
        ActionCard debtCollector = debtCollector("debt-collector");
        PropertyWildCard rainbow = wild("rainbow", EnumSet.allOf(PropertyColor.class), null);
        StartedGame game = startedGameForPlayer2Assets(clock, debtCollector, List.of(rainbow));

        SessionSnapshot snapshot = triggerDebtCollectorAndAutoSettlePlayer2(game, clock, debtCollector.id());

        assertTrue(pendingDebts(snapshot).isEmpty());
        assertTrue(propertyRows(playerSummary(snapshot, "host")).isEmpty());
        assertEquals(List.of(rainbow.id()),
                cardIds(rowCards(propertyRows(playerSummary(snapshot, "player2")).getFirst())));
    }

    private StartedGame startedGameWithHostCards(List<Card> hostCards, List<Card> extraTopCards) {
        return startedGameWithHostCards(hostCards, extraTopCards, null);
    }

    private StartedGame startedGameWithHostCards(List<Card> hostCards, List<Card> extraTopCards, Clock clock) {
        return startedGameWithHands(hostCards, List.of(), extraTopCards, clock);
    }

    private StartedGame startedGameWithHands(
            List<Card> hostCards,
            List<Card> player2Cards,
            List<Card> extraTopCards,
            Clock clock) {
        List<Card> drawOrder = new ArrayList<>();
        List<Card> hostInitialCards = hostCards.subList(0, Math.min(5, hostCards.size()));
        List<Card> hostStartCards = hostCards.subList(Math.min(5, hostCards.size()), hostCards.size());
        List<Card> player2InitialCards = player2Cards.subList(0, Math.min(5, player2Cards.size()));
        drawOrder.addAll(hostInitialCards);
        drawOrder.addAll(moneyCards("host-fill", Math.max(0, 5 - hostInitialCards.size())));
        drawOrder.addAll(player2InitialCards);
        drawOrder.addAll(moneyCards("player2", Math.max(0, 5 - player2InitialCards.size())));
        drawOrder.addAll(hostStartCards);
        drawOrder.addAll(moneyCards("host-start", Math.max(0, 2 - hostStartCards.size())));
        drawOrder.addAll(extraTopCards);
        drawOrder.addAll(moneyCards("extra", 20));
        GameEngine engine = clock == null ? engineWithDrawOrder(drawOrder) : engineWithDrawOrder(drawOrder, clock);
        SessionSnapshot created = createSession(engine, "host", 5);
        join(engine, text(created, "roomCode"), "player2");
        startGame(engine, text(created, "sessionId"), "host");
        return new StartedGame(engine, text(created, "sessionId"));
    }

    private StartedGame startedGameForPlayer2Assets(
            MutableClock clock,
            ActionCard hostAction,
            List<Card> player2Assets) {
        StartedGame game = startedGameWithHands(
                List.of(hostAction),
                player2Assets,
                moneyCards("auto-fill", 30),
                clock);
        endTurn(game.engine(), game.sessionId(), "host");
        for (Card asset : player2Assets) {
            playAssetAsPlayer2(game.engine(), game.sessionId(), asset);
        }
        SessionSnapshot afterAssets = game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "player2"
        )));
        if ("player2".equals(afterAssets.activePlayerId())) {
            endTurn(game.engine(), game.sessionId(), "player2");
        }
        return game;
    }

    private void playAssetAsPlayer2(GameEngine engine, String sessionId, Card asset) {
        if (asset instanceof PropertyWildCard wildCard) {
            PropertyColor color = wildCard.allowedColors().iterator().next();
            engine.executePlayCard(new PlayCardCommand(Map.of(
                    "sessionId", sessionId,
                    "playerId", "player2",
                    "cardId", asset.id(),
                    "propertyColor", color.name()
            )));
            return;
        }
        playAs(engine, sessionId, "player2", asset.id());
    }

    private SessionSnapshot triggerDebtCollectorAndAutoSettlePlayer2(
            StartedGame game,
            MutableClock clock,
            String debtCollectorId) {
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", debtCollectorId,
                "targetPlayerId", "player2"
        )));
        return disconnectPlayer2AndProcess(game, clock);
    }

    private PendingDebtSnapshot acceptedDebtCollectorDebt(StartedGame game, String debtCollectorId) {
        return pendingDebts(acceptDebtCollectorAction(game, debtCollectorId)).getFirst();
    }

    private SessionSnapshot acceptDebtCollectorAction(StartedGame game, String debtCollectorId) {
        SessionSnapshot pending = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", debtCollectorId,
                "targetPlayerId", "player2"
        )));
        return game.engine().executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().actionId(),
                "accepted", true
        )));
    }

    private SessionSnapshot triggerBirthdayAndAutoSettlePlayer2(
            StartedGame game,
            MutableClock clock,
            String birthdayCardId) {
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", birthdayCardId
        )));
        return disconnectPlayer2AndProcess(game, clock);
    }

    private SessionSnapshot disconnectPlayer2AndProcess(StartedGame game, MutableClock clock) {
        game.engine().executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(31));
        game.engine().executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        return game.engine().executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
    }

    private SessionSnapshot play(GameEngine engine, String sessionId, String cardId) {
        return engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", cardId
        )));
    }

    private SessionSnapshot playAs(GameEngine engine, String sessionId, String playerId, String cardId) {
        return engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId,
                "cardId", cardId
        )));
    }

    private void bankAs(GameEngine engine, String sessionId, String playerId, String cardId) {
        engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId,
                "cardId", cardId,
                "playMode", "BANK"
        )));
    }

    private SessionSnapshot createSession(GameEngine engine, String hostPlayerId, int maxPlayers) {
        return engine.executeCreateSession(new CreateSessionCommand(Map.of(
                "hostPlayerId", hostPlayerId,
                "playerName", hostPlayerId,
                "maxPlayers", maxPlayers
        )));
    }

    private void join(GameEngine engine, String roomCode, String playerId) {
        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "roomCode", roomCode,
                "playerId", playerId,
                "playerName", playerId
        )));
    }

    private SessionSnapshot startGame(GameEngine engine, String sessionId, String playerId) {
        return engine.executeStartGame(new StartGameCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId
        )));
    }

    private SessionSnapshot endTurn(GameEngine engine, String sessionId, String playerId) {
        return engine.executeEndTurn(new EndTurnCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId
        )));
    }

    private GameEngine engineWithDrawOrder(List<Card> drawOrder) {
        return engineWithDrawOrder(drawOrder.toArray(Card[]::new));
    }

    private GameEngine engineWithDrawOrder(List<Card> drawOrder, Clock clock) {
        return new GameEngine(() -> deckFromDrawOrder(drawOrder.toArray(Card[]::new)), clock);
    }

    private GameEngine engineWithDrawOrder(Card... drawOrder) {
        return new GameEngine(() -> deckFromDrawOrder(drawOrder));
    }

    @SuppressWarnings("unchecked")
    private GameSession sessionById(GameEngine engine, String sessionId) throws Exception {
        java.lang.reflect.Field field = GameEngine.class.getDeclaredField("sessionsById");
        field.setAccessible(true);
        Map<String, GameSession> sessions = (Map<String, GameSession>) field.get(engine);
        return sessions.get(sessionId);
    }

    private Deck deckFromDrawOrder(Card... drawOrder) {
        List<Card> bottomToTop = new ArrayList<>(List.of(drawOrder));
        Collections.reverse(bottomToTop);
        return new Deck(bottomToTop);
    }

    private List<Card> moneyCards(String prefix, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            cards.add(money(prefix + "-" + i, 1));
        }
        return cards;
    }

    private MoneyCard money(String id, int value) {
        return new MoneyCard(id, value + "M", value);
    }

    private PropertyCard property(String id, PropertyColor color) {
        return new PropertyCard(id, color.name(), color, PropertyColor.fullSetSize(color));
    }

    private PropertyWildCard wild(String id, EnumSet<PropertyColor> allowedColors, Integer bankValueM) {
        return new PropertyWildCard(id, id, allowedColors, bankValueM);
    }

    private ActionCard passGo(String id) {
        return new ActionCard(id, "Pass Go", ActionType.PASS_GO, 1, null);
    }

    private ActionCard debtCollector(String id) {
        return new ActionCard(id, "Debt Collector", ActionType.DEBT_COLLECTOR, 5, null);
    }

    private ActionCard action(String id, ActionType actionType) {
        return new ActionCard(id, actionType.name(), actionType, 1, null);
    }

    private ActionCard rent(String id, ActionType actionType, PropertyColor first, PropertyColor second) {
        return new ActionCard(id, "Rent", actionType, 1, java.util.EnumSet.of(first, second));
    }

    private String text(SessionSnapshot snapshot, String key) {
        return switch (key) {
            case "sessionId" -> snapshot.sessionId();
            case "roomCode" -> snapshot.roomCode();
            default -> throw new IllegalArgumentException("Unsupported snapshot text key: " + key);
        };
    }

    private List<CardSnapshot> currentPlayerHand(SessionSnapshot snapshot) {
        return snapshot.currentPlayerHand();
    }

    private List<CardSnapshot> bankCards(PlayerStateSnapshot player) {
        return player.bankCards();
    }

    private List<PropertyRowSnapshot> propertyRows(PlayerStateSnapshot player) {
        return player.propertyRows();
    }

    private List<CardSnapshot> rowCards(PropertyRowSnapshot row) {
        return row.cards();
    }

    private List<PendingDebtSnapshot> pendingDebts(SessionSnapshot snapshot) {
        return snapshot.pendingDebts();
    }

    private List<PendingTradeSnapshot> pendingTrades(SessionSnapshot snapshot) {
        return snapshot.pendingTrades();
    }

    private List<PendingActionSnapshot> pendingActions(SessionSnapshot snapshot) {
        return snapshot.pendingActions();
    }

    private List<String> cardIds(List<CardSnapshot> cards) {
        return cards.stream().map(CardSnapshot::id).toList();
    }

    private List<String> hostPropertyCardIds(SessionSnapshot snapshot) {
        return playerPropertyCardIds(snapshot, "host");
    }

    private List<String> playerPropertyCardIds(SessionSnapshot snapshot, String playerId) {
        return propertyRows(playerSummary(snapshot, playerId)).stream()
                .flatMap(row -> row.cards().stream())
                .map(CardSnapshot::id)
                .toList();
    }

    private PlayerStateSnapshot playerSummary(SessionSnapshot snapshot, String playerId) {
        return snapshot.players().stream()
                .filter(player -> playerId.equals(player.playerId()))
                .findFirst()
                .orElseThrow();
    }

    private record StartedGame(GameEngine engine, String sessionId) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-05-28T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
