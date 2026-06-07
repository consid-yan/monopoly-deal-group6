package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.core.rules.WinRule;
import ie.ucd.bdic.group6.core.snapshot.CardSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingActionSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingTradeSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PropertyRowSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds {@link SessionSnapshot} views from live {@link GameSession} state.
 */
public final class SessionSnapshotBuilder {

    private final WinRule winRule;

    public SessionSnapshotBuilder(WinRule winRule) {
        this.winRule = Objects.requireNonNull(winRule, "winRule");
    }

    public SessionSnapshot build(GameSession session, String requesterPlayerId) {
        // Centralized win check: every command path that produces a snapshot evaluates win conditions.
        session.activeGame().ifPresent(ag -> {
            if (session.status() == SessionStatus.ACTIVE) {
                session.findPlayer(ag.turnManager().activePlayerId()).ifPresent(activePlayer -> {
                    if (winRule.hasWon(activePlayer, ag.turnManager().isActivePlayer(activePlayer.id()))) {
                        session.finish(activePlayer.id());
                    }
                });
            }
        });

        Optional<ActiveGameState> activeGame = session.activeGame();

        List<CardSnapshot> currentPlayerHand = session.findPlayer(blankToEmpty(requesterPlayerId))
                .map(player -> player.handView().stream().map(this::cardSummary).toList())
                .orElse(List.of());

        return new SessionSnapshot(
                session.sessionId(),
                session.roomCode(),
                session.hostPlayerId(),
                session.maxPlayers(),
                session.status().name(),
                activeGame.map(game -> game.turnManager().activePlayerId()).orElse(""),
                activeGame.map(game -> game.turnManager().actionsUsed()).orElse(0),
                activeGame.map(game -> game.turnManager().actionsRemaining()).orElse(0),
                activeGame.flatMap(ActiveGameState::winnerPlayerId).orElse(""),
                activeGame.map(game -> game.drawPile().size()).orElse(0),
                activeGame.map(ActiveGameState::discardPileSize).orElse(0),
                activeGame
                        .map(game -> game.pendingDebtsView().stream().map(this::pendingDebtSummary).toList())
                        .orElse(List.of()),
                activeGame
                        .map(game -> game.pendingTradesView().stream().map(this::pendingTradeSummary).toList())
                        .orElse(List.of()),
                activeGame
                        .map(game -> game.pendingActionsView().stream().map(this::pendingActionSummary).toList())
                        .orElse(List.of()),
                session.forfeitedPlayerIds(),
                session.players().stream().map(player -> playerSummary(session, player)).toList(),
                currentPlayerHand
        );
    }

    private PendingDebtSnapshot pendingDebtSummary(PendingDebt debt) {
        return new PendingDebtSnapshot(
                debt.debtId(),
                debt.creditorPlayerId(),
                debt.debtorPlayerId(),
                debt.amountM(),
                debt.sourceAction() == null ? "" : debt.sourceAction().name(),
                debt.sourceCardId() == null ? "" : debt.sourceCardId()
        );
    }

    private PendingTradeSnapshot pendingTradeSummary(PendingTrade trade) {
        return new PendingTradeSnapshot(
                trade.tradeId(),
                trade.proposerPlayerId(),
                trade.targetPlayerId(),
                trade.offeredCardIds(),
                trade.requestedCardIds(),
                trade.status().name(),
                trade.createdAt().toString(),
                trade.respondedAt() == null ? "" : trade.respondedAt().toString()
        );
    }

    private PendingActionSnapshot pendingActionSummary(PendingAction action) {
        return new PendingActionSnapshot(
                action.actionId(),
                action.actorPlayerId(),
                action.actionType().name(),
                action.actionCardId(),
                action.doubleRentCardId().orElse(""),
                action.propertyColor().map(Enum::name).orElse(""),
                action.amountM(),
                action.targetCardId().orElse(""),
                action.targetPropertyRowId().orElse(""),
                action.offeredCardId().orElse(""),
                action.requestedCardId().orElse(""),
                action.targetPlayerIds(),
                action.responsesView().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().name())),
                action.createdAt().toString()
        );
    }

    private PlayerStateSnapshot playerSummary(GameSession session, Player player) {
        return new PlayerStateSnapshot(
                player.id(),
                player.name(),
                session.playerStatus(player.id()),
                session.isConnected(player.id()),
                session.disconnectedAt(player.id()).map(Instant::toString).orElse(""),
                session.isDefeated(player.id()),
                player.hand().size(),
                player.bankTotalValueM(),
                player.bankView().stream().map(this::cardSummary).toList(),
                player.propertyBoard().rowsView().stream().map(this::propertyRowSummary).toList()
        );
    }

    private PropertyRowSnapshot propertyRowSummary(PropertySet row) {
        return new PropertyRowSnapshot(
                row.rowId(),
                row.anchorColor().name(),
                row.isComplete(),
                row.rentValue(),
                row.hasHouse(),
                row.hasHotel(),
                row.cardsView().stream().map(this::cardSummary).toList()
        );
    }

    private CardSnapshot cardSummary(Card card) {
        int valueM = 0;
        String propertyColor = "";
        int requiredToComplete = 0;
        List<String> allowedColors = List.of();
        String actionType = "";
        List<String> rentColorOptions = List.of();

        if (card instanceof MoneyCard moneyCard) {
            valueM = moneyCard.valueM();
        }
        if (card instanceof PropertyCard propertyCard) {
            propertyColor = propertyCard.color().name();
            requiredToComplete = propertyCard.requiredToComplete();
        }
        if (card instanceof PropertyWildCard wildCard) {
            allowedColors = wildCard.allowedColors().stream().map(Enum::name).toList();
        }
        if (card instanceof ActionCard actionCard) {
            actionType = actionCard.actionType().name();
            rentColorOptions = actionCard.rentColorOptions().stream().map(Enum::name).toList();
        }
        return new CardSnapshot(
                card.id(),
                card.type().name(),
                card.displayName(),
                card.bankValueM().orElse(0),
                valueM,
                propertyColor,
                requiredToComplete,
                allowedColors,
                actionType,
                rentColorOptions
        );
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
