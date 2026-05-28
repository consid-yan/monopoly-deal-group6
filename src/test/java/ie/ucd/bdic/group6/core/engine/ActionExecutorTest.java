package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.CreateSessionCommand;
import ie.ucd.bdic.group6.command.DiscardCardsCommand;
import ie.ucd.bdic.group6.command.EndTurnCommand;
import ie.ucd.bdic.group6.command.JoinSessionCommand;
import ie.ucd.bdic.group6.command.PayDebtCommand;
import ie.ucd.bdic.group6.command.PlayCardCommand;
import ie.ucd.bdic.group6.command.PlayerDisconnectedCommand;
import ie.ucd.bdic.group6.command.ProposeTradeCommand;
import ie.ucd.bdic.group6.command.ProcessDisconnectTimeoutsCommand;
import ie.ucd.bdic.group6.command.RejoinSessionCommand;
import ie.ucd.bdic.group6.command.RespondActionCommand;
import ie.ucd.bdic.group6.command.RespondTradeCommand;
import ie.ucd.bdic.group6.command.StartGameCommand;
import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.Deck;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.exception.GameException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        String sessionCode = text(created, "sessionCode");

        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "sessionCode", sessionCode,
                "playerId", "player2"
        )));
        Map<String, Object> started = startGame(engine, sessionId, "host");

        assertEquals("ACTIVE", started.get("status"));
        assertEquals("host", started.get("activePlayerId"));
        assertEquals(3, started.get("actionsRemaining"));
        assertEquals(7, currentPlayerHand(started).size());
        assertEquals(7, playerSummary(started, "host").get("handCount"));
        assertEquals(5, playerSummary(started, "player2").get("handCount"));
    }

    @Test
    void startGameRejectsInvalidLobbyStates() {
        GameEngine tooFewPlayers = new GameEngine();
        String loneSessionId = text(createSession(tooFewPlayers, "host", 5), "sessionId");
        GameException tooFew = assertThrows(GameException.class,
                () -> startGame(tooFewPlayers, loneSessionId, "host"));
        assertEquals("INVALID_PLAYER_COUNT", tooFew.getErrorCode());

        GameEngine nonHostEngine = new GameEngine();
        Map<String, Object> created = createSession(nonHostEngine, "host", 5);
        nonHostEngine.executeJoinSession(new JoinSessionCommand(Map.of(
                "sessionCode", text(created, "sessionCode"),
                "playerId", "player2"
        )));
        GameException nonHost = assertThrows(GameException.class,
                () -> startGame(nonHostEngine, text(created, "sessionId"), "player2"));
        assertEquals("HOST_REQUIRED", nonHost.getErrorCode());
    }

    @Test
    void joinSessionRejectsDuplicateAndFullLobby() {
        GameEngine engine = new GameEngine();
        Map<String, Object> created = createSession(engine, "host", 2);
        String sessionCode = text(created, "sessionCode");

        GameException duplicate = assertThrows(GameException.class,
                () -> engine.executeJoinSession(new JoinSessionCommand(Map.of(
                        "sessionCode", sessionCode,
                        "playerId", "host"
                ))));
        assertEquals("PLAYER_ALREADY_JOINED", duplicate.getErrorCode());

        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "sessionCode", sessionCode,
                "playerId", "player2"
        )));
        GameException full = assertThrows(GameException.class,
                () -> engine.executeJoinSession(new JoinSessionCommand(Map.of(
                        "sessionCode", sessionCode,
                        "playerId", "player3"
                ))));
        assertEquals("SESSION_FULL", full.getErrorCode());
    }

    @Test
    void turnOrderCyclesByListIndex() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40));
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        String sessionCode = text(created, "sessionCode");
        join(engine, sessionCode, "player2");
        join(engine, sessionCode, "player3");
        startGame(engine, sessionId, "host");

        Map<String, Object> afterHost = endTurn(engine, sessionId, "host");
        assertEquals("player2", afterHost.get("activePlayerId"));
        Map<String, Object> afterPlayer2 = endTurn(engine, sessionId, "player2");
        assertEquals("player3", afterPlayer2.get("activePlayerId"));
        Map<String, Object> afterPlayer3 = endTurn(engine, sessionId, "player3");
        assertEquals("host", afterPlayer3.get("activePlayerId"));
    }

    @Test
    void bankingMoneyMovesCardAndConsumesAction() {
        MoneyCard banked = money("bank-me", 5);
        StartedGame game = startedGameWithHostCards(List.of(banked), moneyCards("fill", 20));

        Map<String, Object> afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", banked.id(),
                "playMode", "BANK"
        )));

        Map<String, Object> host = playerSummary(afterPlay, "host");
        assertEquals(1, afterPlay.get("actionsUsed"));
        assertEquals(6, host.get("handCount"));
        assertEquals(5, host.get("bankTotalValueM"));
        assertEquals(banked.id(), cardIds(bankCards(host)).getFirst());
    }

    @Test
    void placingPropertyCreatesRowAndConsumesAction() {
        PropertyCard red = property("red-1", PropertyColor.RED);
        StartedGame game = startedGameWithHostCards(List.of(red), moneyCards("fill", 20));

        Map<String, Object> afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", red.id()
        )));

        Map<String, Object> host = playerSummary(afterPlay, "host");
        Map<String, Object> row = propertyRows(host).getFirst();
        assertEquals(1, afterPlay.get("actionsUsed"));
        assertEquals("RED", row.get("anchorColor"));
        assertEquals(List.of(red.id()), cardIds(rowCards(row)));
    }

    @Test
    void passGoDrawsTwoCardsAndConsumesAction() {
        ActionCard passGo = passGo("pass-go");
        MoneyCard drawnOne = money("draw-1", 1);
        MoneyCard drawnTwo = money("draw-2", 2);
        StartedGame game = startedGameWithHostCards(List.of(passGo), List.of(drawnOne, drawnTwo));

        Map<String, Object> afterPlay = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo.id()
        )));

        assertEquals(1, afterPlay.get("actionsUsed"));
        assertEquals(1, afterPlay.get("discardSize"));
        assertEquals(8, playerSummary(afterPlay, "host").get("handCount"));
        assertFalse(cardIds(currentPlayerHand(afterPlay)).contains(passGo.id()));
        assertTrue(cardIds(currentPlayerHand(afterPlay)).containsAll(List.of(drawnOne.id(), drawnTwo.id())));
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", payment.id());
        endTurn(engine, sessionId, "player2");
        Map<String, Object> afterDebt = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));

        Map<String, Object> pendingAction = pendingActions(afterDebt).getFirst();
        assertEquals("DEBT_COLLECTOR", pendingAction.get("actionType"));
        assertTrue(pendingDebts(afterDebt).isEmpty());

        Map<String, Object> afterResponse = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingAction.get("actionId"),
                "accepted", true
        )));

        Map<String, Object> debt = pendingDebts(afterResponse).getFirst();
        assertEquals("host", debt.get("creditorPlayerId"));
        assertEquals("player2", debt.get("debtorPlayerId"));
        assertEquals(5, debt.get("amountM"));

        Map<String, Object> afterPay = engine.executePayDebt(new PayDebtCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "debtId", debt.get("debtId"),
                "paymentCardIds", List.of(payment.id())
        )));

        assertTrue(pendingDebts(afterPay).isEmpty());
        assertEquals(5, playerSummary(afterPay, "host").get("bankTotalValueM"));
        assertEquals(0, playerSummary(afterPay, "player2").get("bankTotalValueM"));
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        Map<String, Object> pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", debtCollector.id(),
                "targetPlayerId", "player2"
        )));
        String actionId = (String) pendingActions(pending).getFirst().get("actionId");

        Map<String, Object> rejected = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", actionId,
                "accepted", false,
                "justSayNoCardId", justSayNo.id()
        )));

        assertTrue(pendingActions(rejected).isEmpty());
        assertTrue(pendingDebts(rejected).isEmpty());
        assertEquals(2, rejected.get("discardSize"));
        assertFalse(cardIds(currentPlayerHand(rejected)).contains(justSayNo.id()));
    }

    @Test
    void doubleRentCreatesDoubledPendingDebtAfterTargetAccepts() {
        PropertyCard brown = property("brown-rent", PropertyColor.BROWN);
        ActionCard rent = rent("rent-brown-blue", ActionType.RENT_BROWN_OR_LIGHT_BLUE,
                PropertyColor.BROWN, PropertyColor.LIGHT_BLUE);
        ActionCard doubleRent = action("double-rent", ActionType.DOUBLE_THE_RENT);
        StartedGame game = startedGameWithHostCards(List.of(brown, rent, doubleRent), moneyCards("fill", 20));

        play(game.engine(), game.sessionId(), brown.id());
        Map<String, Object> pending = game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", rent.id(),
                "propertyColor", "BROWN",
                "doubleRentCardId", doubleRent.id()
        )));

        assertEquals(3, pending.get("actionsUsed"));
        Map<String, Object> afterAccept = game.engine().executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().get("actionId"),
                "accepted", true
        )));

        assertEquals(2, pendingDebts(afterAccept).getFirst().get("amountM"));
        assertEquals(2, afterAccept.get("discardSize"));
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", red.id());
        endTurn(engine, sessionId, "player2");

        Map<String, Object> pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", slyDeal.id(),
                "targetPlayerId", "player2",
                "targetCardId", red.id()
        )));
        Map<String, Object> accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().get("actionId"),
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");
        play(engine, sessionId, hostBlue.id());
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", targetRed.id());
        endTurn(engine, sessionId, "player2");

        Map<String, Object> pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", forcedDeal.id(),
                "targetPlayerId", "player2",
                "offeredCardId", hostBlue.id(),
                "requestedCardId", targetRed.id()
        )));
        Map<String, Object> accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().get("actionId"),
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");
        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", brown1.id());
        Map<String, Object> player2Board = playAs(engine, sessionId, "player2", brown2.id());
        String rowId = (String) propertyRows(playerSummary(player2Board, "player2")).getFirst().get("rowId");
        endTurn(engine, sessionId, "player2");

        Map<String, Object> pending = engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", dealBreaker.id(),
                "targetPlayerId", "player2",
                "targetPropertyRowId", rowId
        )));
        Map<String, Object> accepted = engine.executeRespondAction(new RespondActionCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "actionId", pendingActions(pending).getFirst().get("actionId"),
                "accepted", true
        )));

        assertTrue((Boolean) propertyRows(playerSummary(accepted, "host")).getFirst().get("complete"));
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
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        endTurn(engine, sessionId, "host");
        playAs(engine, sessionId, "player2", payment.id());
        endTurn(engine, sessionId, "player2");
        playAs(engine, sessionId, "host", red.id());
        playAs(engine, sessionId, "host", "host-1");
        playAs(engine, sessionId, "host", "host-2");
        Map<String, Object> proposed = engine.executeProposeTrade(new ProposeTradeCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "targetPlayerId", "player2",
                "offeredCardIds", List.of(red.id()),
                "requestedCardIds", List.of(payment.id())
        )));
        String tradeId = (String) pendingTrades(proposed).getFirst().get("tradeId");

        endTurn(engine, sessionId, "host");
        Map<String, Object> accepted = engine.executeRespondTrade(new RespondTradeCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2",
                "tradeId", tradeId,
                "accepted", true
        )));

        assertTrue(pendingTrades(accepted).isEmpty());
        assertEquals(5, playerSummary(accepted, "host").get("bankTotalValueM"));
        assertTrue(propertyRows(playerSummary(accepted, "host")).isEmpty());
        assertEquals(List.of(red.id()), cardIds(rowCards(propertyRows(playerSummary(accepted, "player2")).getFirst())));
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
        Map<String, Object> snapshot = game.engine().executeRejoinSession(new ie.ucd.bdic.group6.command.RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertEquals(1, propertyRows(playerSummary(snapshot, "host")).size());
        assertTrue(cardIds(currentPlayerHand(snapshot)).contains(blue.id()));
    }

    @Test
    void fourthPlayInTurnIsRejectedWithoutMutation() {
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

        assertEquals("ACTION_LIMIT_REACHED", error.getErrorCode());
        Map<String, Object> snapshot = game.engine().executeRejoinSession(new ie.ucd.bdic.group6.command.RejoinSessionCommand(Map.of(
                "sessionIdentity", game.sessionId(),
                "playerId", "host"
        )));
        assertTrue(cardIds(currentPlayerHand(snapshot)).contains("m4"));
        assertEquals(3, bankCards(playerSummary(snapshot, "host")).size());
    }

    @Test
    void endTurnRejectsLargeHandThenSucceedsAfterDiscard() {
        ActionCard passGo = passGo("pass-go");
        MoneyCard discard = money("discard-me", 1);
        StartedGame game = startedGameWithHostCards(List.of(passGo, discard), moneyCards("draw", 20));
        game.engine().executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "cardId", passGo.id()
        )));

        GameException handLimit = assertThrows(GameException.class,
                () -> endTurn(game.engine(), game.sessionId(), "host"));
        assertEquals("HAND_LIMIT_EXCEEDED", handLimit.getErrorCode());

        game.engine().executeDiscardCards(new DiscardCardsCommand(Map.of(
                "sessionId", game.sessionId(),
                "playerId", "host",
                "discardedCardIds", List.of(discard.id())
        )));
        Map<String, Object> afterEnd = endTurn(game.engine(), game.sessionId(), "host");
        assertEquals(2, afterEnd.get("discardSize"));
        assertEquals("player2", afterEnd.get("activePlayerId"));
    }

    @Test
    void endTurnAdvancesActivePlayerAndAppliesDrawRule() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 30));
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        Map<String, Object> afterEnd = endTurn(engine, sessionId, "host");

        assertEquals("player2", afterEnd.get("activePlayerId"));
        assertEquals(7, playerSummary(afterEnd, "player2").get("handCount"));
    }

    @Test
    void completingThreePropertySetsFinishesGame() {
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
        endTurn(game.engine(), game.sessionId(), "host");
        endTurn(game.engine(), game.sessionId(), "player2");
        play(game.engine(), game.sessionId(), "brown-2");
        play(game.engine(), game.sessionId(), "dark-blue-2");
        Map<String, Object> afterWin = play(game.engine(), game.sessionId(), "utility-2");

        assertEquals("FINISHED", afterWin.get("status"));
        assertEquals("host", afterWin.get("winnerPlayerId"));
    }

    @Test
    void rejoinRejectsUnknownPlayerId() {
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 20));
        Map<String, Object> created = createSession(engine, "host", 5);
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, text(created, "sessionId"), "host");

        GameException error = assertThrows(GameException.class,
                () -> engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                        "sessionIdentity", text(created, "sessionId"),
                        "playerId", "intruder"
                ))));
        assertEquals("PLAYER_NOT_FOUND", error.getErrorCode());
    }

    @Test
    void disconnectedActivePlayerIsSkippedAfterThirtySeconds() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host"
        )));
        clock.advance(Duration.ofSeconds(31));
        Map<String, Object> processed = engine.executeProcessDisconnectTimeouts(
                new ProcessDisconnectTimeoutsCommand(Map.of()));

        assertEquals(List.of(sessionId), processed.get("sessionIds"));
        Map<String, Object> snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "player2"
        )));
        assertEquals("player2", snapshot.get("activePlayerId"));
        assertEquals("DISCONNECTED", playerSummary(snapshot, "host").get("playerStatus"));
    }

    @Test
    void disconnectedPlayerForfeitsAfterNinetySecondsAndCardsAreDiscarded() {
        MutableClock clock = new MutableClock();
        GameEngine engine = engineWithDrawOrder(moneyCards("c", 40), clock);
        Map<String, Object> created = createSession(engine, "host", 5);
        String sessionId = text(created, "sessionId");
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, sessionId, "host");

        engine.executePlayerDisconnected(new PlayerDisconnectedCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "player2"
        )));
        clock.advance(Duration.ofSeconds(91));
        engine.executeProcessDisconnectTimeouts(new ProcessDisconnectTimeoutsCommand(Map.of()));
        Map<String, Object> snapshot = engine.executeRejoinSession(new RejoinSessionCommand(Map.of(
                "sessionIdentity", sessionId,
                "playerId", "host"
        )));

        assertEquals("FINISHED", snapshot.get("status"));
        assertEquals("host", snapshot.get("winnerPlayerId"));
        assertEquals(List.of("player2"), snapshot.get("forfeitedPlayerIds"));
        assertEquals("DEFEATED", playerSummary(snapshot, "player2").get("playerStatus"));
        assertEquals(5, snapshot.get("discardSize"));
    }

    private StartedGame startedGameWithHostCards(List<Card> hostCards, List<Card> extraTopCards) {
        List<Card> drawOrder = new ArrayList<>();
        List<Card> hostInitialCards = hostCards.subList(0, Math.min(5, hostCards.size()));
        List<Card> hostStartCards = hostCards.subList(Math.min(5, hostCards.size()), hostCards.size());
        drawOrder.addAll(hostInitialCards);
        drawOrder.addAll(moneyCards("host-fill", Math.max(0, 5 - hostInitialCards.size())));
        drawOrder.addAll(moneyCards("player2", 5));
        drawOrder.addAll(hostStartCards);
        drawOrder.addAll(moneyCards("host-start", Math.max(0, 2 - hostStartCards.size())));
        drawOrder.addAll(extraTopCards);
        drawOrder.addAll(moneyCards("extra", 20));
        GameEngine engine = engineWithDrawOrder(drawOrder);
        Map<String, Object> created = createSession(engine, "host", 5);
        join(engine, text(created, "sessionCode"), "player2");
        startGame(engine, text(created, "sessionId"), "host");
        return new StartedGame(engine, text(created, "sessionId"));
    }

    private Map<String, Object> play(GameEngine engine, String sessionId, String cardId) {
        return engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", "host",
                "cardId", cardId
        )));
    }

    private Map<String, Object> playAs(GameEngine engine, String sessionId, String playerId, String cardId) {
        return engine.executePlayCard(new PlayCardCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId,
                "cardId", cardId
        )));
    }

    private Map<String, Object> createSession(GameEngine engine, String hostPlayerId, int maxPlayers) {
        return engine.executeCreateSession(new CreateSessionCommand(Map.of(
                "hostPlayerId", hostPlayerId,
                "playerName", hostPlayerId,
                "maxPlayers", maxPlayers
        )));
    }

    private void join(GameEngine engine, String sessionCode, String playerId) {
        engine.executeJoinSession(new JoinSessionCommand(Map.of(
                "sessionCode", sessionCode,
                "playerId", playerId,
                "playerName", playerId
        )));
    }

    private Map<String, Object> startGame(GameEngine engine, String sessionId, String playerId) {
        return engine.executeStartGame(new StartGameCommand(Map.of(
                "sessionId", sessionId,
                "playerId", playerId
        )));
    }

    private Map<String, Object> endTurn(GameEngine engine, String sessionId, String playerId) {
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

    private String text(Map<String, Object> data, String key) {
        return (String) data.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> currentPlayerHand(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("currentPlayerHand");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> bankCards(Map<String, Object> player) {
        return (List<Map<String, Object>>) player.get("bankCards");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> propertyRows(Map<String, Object> player) {
        return (List<Map<String, Object>>) player.get("propertyRows");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowCards(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("cards");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pendingDebts(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("pendingDebts");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pendingTrades(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("pendingTrades");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pendingActions(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("pendingActions");
    }

    private List<String> cardIds(List<Map<String, Object>> cards) {
        return cards.stream().map(card -> (String) card.get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> playerSummary(Map<String, Object> snapshot, String playerId) {
        return ((List<Map<String, Object>>) snapshot.get("players")).stream()
                .filter(player -> playerId.equals(player.get("playerId")))
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
