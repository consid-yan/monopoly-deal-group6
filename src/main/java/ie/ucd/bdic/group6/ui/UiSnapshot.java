package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.engine.PlayerPhase;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.snapshot.CardSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingActionSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingDebtSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PendingTradeSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PlayerStateSnapshot;
import ie.ucd.bdic.group6.core.snapshot.PropertyRowSnapshot;
import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UiSnapshot {

    private static final SessionSnapshot EMPTY_SNAPSHOT = new SessionSnapshot(
            "", "", "", 0, "", "", 0,
            0, "", 0, 0,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
    );
    private static final UiSnapshot EMPTY = new UiSnapshot(EMPTY_SNAPSHOT);

    private final SessionSnapshot snapshot;
    private final List<PlayerView> players;
    private final List<CardView> currentPlayerHand;
    private final List<PendingDebtView> pendingDebts;
    private final List<PendingTradeView> pendingTrades;
    private final List<PendingActionView> pendingActions;

    private UiSnapshot(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        this.players = this.snapshot.players().stream().map(PlayerView::from).toList();
        this.currentPlayerHand = this.snapshot.currentPlayerHand().stream().map(CardView::from).toList();
        this.pendingDebts = this.snapshot.pendingDebts().stream().map(PendingDebtView::from).toList();
        this.pendingTrades = this.snapshot.pendingTrades().stream().map(PendingTradeView::from).toList();
        this.pendingActions = this.snapshot.pendingActions().stream().map(PendingActionView::from).toList();
    }

    public static UiSnapshot empty() {
        return EMPTY;
    }

    public static UiSnapshot from(SessionSnapshot snapshot) {
        if (snapshot == null) {
            return empty();
        }
        return new UiSnapshot(snapshot);
    }

    public boolean isEmpty() {
        return snapshot == EMPTY_SNAPSHOT
                || (snapshot.sessionId().isBlank()
                && snapshot.roomCode().isBlank()
                && players.isEmpty());
    }

    public String sessionId() {
        return snapshot.sessionId();
    }

    public String roomCode() {
        return snapshot.roomCode();
    }

    public String status() {
        return snapshot.status();
    }

    public String hostPlayerId() {
        return snapshot.hostPlayerId();
    }

    public int maxPlayers() {
        return snapshot.maxPlayers();
    }

    public String activePlayerId() {
        return snapshot.activePlayerId();
    }

    public int actionsUsed() {
        return snapshot.actionsUsed();
    }

    public int actionsRemaining() {
        return snapshot.actionsRemaining();
    }

    public String winnerPlayerId() {
        return snapshot.winnerPlayerId();
    }

    public int deckSize() {
        return snapshot.deckSize();
    }

    public int discardSize() {
        return snapshot.discardSize();
    }

    public List<PlayerView> players() {
        return players;
    }

    public List<CardView> currentPlayerHand() {
        return currentPlayerHand;
    }

    public List<PendingDebtView> pendingDebts() {
        return pendingDebts;
    }

    public List<PendingTradeView> pendingTrades() {
        return pendingTrades;
    }

    public List<PendingActionView> pendingActions() {
        return pendingActions;
    }

    public List<String> forfeitedPlayerIds() {
        return snapshot.forfeitedPlayerIds();
    }

    public Optional<PlayerView> player(String playerId) {
        return players.stream().filter(player -> player.playerId().equals(playerId)).findFirst();
    }

    public PlayerPhase playerPhase(String playerId) {
        return player(playerId)
                .map(PlayerView::playerPhase)
                .map(UiSnapshot::parsePlayerPhase)
                .orElse(PlayerPhase.WAITING);
    }

    public String playerName(String playerId) {
        return player(playerId)
                .map(PlayerView::displayName)
                .orElse(playerId == null || playerId.isBlank() ? "" : playerId);
    }

    public Optional<CardView> card(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return Optional.empty();
        }
        return currentPlayerHand.stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst()
                .or(() -> players.stream()
                        .flatMap(player -> player.visibleCards().stream())
                        .filter(card -> card.id().equals(cardId))
                        .findFirst());
    }

    public List<PlayerView> opponents(String playerId) {
        return players.stream()
                .filter(player -> !player.playerId().equals(playerId))
                .sorted(Comparator.comparing(PlayerView::displayName))
                .toList();
    }

    public boolean isTurnFor(String playerId) {
        return playerId != null && !playerId.isBlank() && playerId.equals(activePlayerId());
    }

    public int cardsToDiscard() {
        return Math.max(0, currentPlayerHand.size() - 7);
    }

    public List<PendingDebtView> pendingDebtsForDebtor(String playerId) {
        return pendingDebts.stream()
                .filter(debt -> debt.debtorPlayerId().equals(playerId))
                .toList();
    }

    public List<PendingTradeView> pendingTradesForTarget(String playerId) {
        return pendingTrades.stream()
                .filter(trade -> trade.targetPlayerId().equals(playerId))
                .filter(PendingTradeView::pending)
                .toList();
    }

    public boolean canRespondToTrade(String playerId) {
        return !pendingTradesForTarget(playerId).isEmpty();
    }

    public List<PendingActionView> pendingActionsAwaiting(String playerId) {
        return pendingActions.stream()
                .filter(action -> action.awaits(playerId))
                .toList();
    }

    public boolean canProposeTrade(String playerId) {
        return player(playerId)
                .map(player -> opponents(playerId).stream()
                        .filter(opponent -> !opponent.defeated())
                        .anyMatch(player::canTradeWith))
                .orElse(false);
    }

    public record PlayerView(
            String playerId,
            String name,
            String playerStatus,
            String playerPhase,
            boolean connected,
            String disconnectedAt,
            boolean defeated,
            int handCount,
            int bankTotalValueM,
            List<CardView> bankCards,
            List<PropertyRowView> propertyRows) {

        private static PlayerView from(PlayerStateSnapshot snapshot) {
            return new PlayerView(
                    snapshot.playerId(),
                    snapshot.name(),
                    snapshot.playerStatus(),
                    snapshot.playerPhase(),
                    snapshot.connected(),
                    snapshot.disconnectedAt(),
                    snapshot.defeated(),
                    snapshot.handCount(),
                    snapshot.bankTotalValueM(),
                    snapshot.bankCards().stream().map(CardView::from).toList(),
                    snapshot.propertyRows().stream().map(PropertyRowView::from).toList());
        }

        public String displayName() {
            return name == null || name.isBlank() ? playerId : name;
        }

        public int completedSetCount() {
            return (int) propertyRows.stream().filter(PropertyRowView::complete).count();
        }

        public List<CardView> propertyCards() {
            return propertyRows.stream().flatMap(row -> row.cards().stream()).toList();
        }

        public List<CardView> paymentCards() {
            return visibleCards().stream()
                    .filter(CardView::canUseForDebtPayment)
                    .toList();
        }

        public List<CardView> stealablePropertyCards() {
            return propertyRows.stream()
                    .filter(row -> !row.complete())
                    .flatMap(row -> row.cards().stream())
                    .toList();
        }

        public List<PropertyRowView> improvableRows(String actionType) {
            return propertyRows.stream()
                    .filter(row -> row.canAcceptImprovement(actionType))
                    .toList();
        }

        public List<String> rentableColorsFor(CardView rentCard) {
            return propertyRows.stream()
                    .filter(row -> row.rentValue() > 0)
                    .map(PropertyRowView::anchorColor)
                    .distinct()
                    .filter(color -> rentCard.rentColorOptions().isEmpty()
                            || rentCard.rentColorOptions().contains(color))
                    .toList();
        }

        public boolean canTradeWith(PlayerView target) {
            return visibleCards().stream()
                    .anyMatch(offered -> !target.supportedTradeRequestsFor(offered, this).isEmpty());
        }

        public List<CardView> supportedTradeRequestsFor(CardView offered, PlayerView offeredOwner) {
            String offeredKind = offeredOwner.tradeKind(offered).orElse("");
            return visibleCards().stream()
                    .filter(requested -> isSupportedTradeKind(offeredKind, tradeKind(requested).orElse("")))
                    .toList();
        }

        public List<CardView> visibleCards() {
            List<CardView> cards = new ArrayList<>();
            cards.addAll(bankCards);
            cards.addAll(propertyCards());
            return List.copyOf(cards);
        }

        private Optional<String> tradeKind(CardView card) {
            if (card == null) {
                return Optional.empty();
            }
            boolean inBank = bankCards.stream().anyMatch(candidate -> candidate.id().equals(card.id()));
            if (inBank && "MONEY".equals(card.type())) {
                return Optional.of("MONEY");
            }
            boolean inProperties = propertyCards().stream().anyMatch(candidate -> candidate.id().equals(card.id()));
            if (inProperties && card.isPropertyLike()) {
                return Optional.of("PROPERTY");
            }
            return Optional.empty();
        }

        private static boolean isSupportedTradeKind(String offeredKind, String requestedKind) {
            return ("PROPERTY".equals(offeredKind) && "MONEY".equals(requestedKind))
                    || ("MONEY".equals(offeredKind) && "PROPERTY".equals(requestedKind))
                    || ("PROPERTY".equals(offeredKind) && "PROPERTY".equals(requestedKind));
        }
    }

    public record PropertyRowView(
            String rowId,
            String anchorColor,
            boolean complete,
            int rentValue,
            boolean hasHouse,
            boolean hasHotel,
            List<CardView> cards) {

        private static PropertyRowView from(PropertyRowSnapshot snapshot) {
            return new PropertyRowView(
                    snapshot.rowId(),
                    snapshot.anchorColor(),
                    snapshot.complete(),
                    snapshot.rentValue(),
                    snapshot.hasHouse(),
                    snapshot.hasHotel(),
                    snapshot.cards().stream().map(CardView::from).toList());
        }

        public String label() {
            StringBuilder label = new StringBuilder(humanize(anchorColor));
            if (complete) {
                label.append(" complete");
            }
            if (hasHouse) {
                label.append(" + House");
            }
            if (hasHotel) {
                label.append(" + Hotel");
            }
            return label.toString();
        }

        public int requiredToComplete() {
            try {
                return PropertyColor.fullSetSize(PropertyColor.valueOf(anchorColor));
            } catch (IllegalArgumentException ignored) {
                // Fall back to the card metadata for an unknown color.
            }
            return cards.stream()
                    .mapToInt(CardView::requiredToComplete)
                    .filter(required -> required > 0)
                    .findFirst()
                    .orElse(cards.size());
        }

        public String progressLabel() {
            int required = requiredToComplete();
            if (required <= 0) {
                return cards.size() + " cards";
            }
            return cards.size() + "/" + required + " cards";
        }

        public boolean canAcceptImprovement(String actionType) {
            if (!complete || isRailOrUtility(anchorColor)) {
                return false;
            }
            if ("HOUSE".equals(actionType)) {
                return !hasHouse && !hasHotel;
            }
            if ("HOTEL".equals(actionType)) {
                return hasHouse && !hasHotel;
            }
            return false;
        }

        private static boolean isRailOrUtility(String color) {
            return "RAILROAD".equals(color) || "UTILITY".equals(color);
        }
    }

    public record CardView(
            String id,
            String type,
            String displayName,
            int bankValueM,
            int valueM,
            String propertyColor,
            int requiredToComplete,
            List<String> allowedColors,
            String actionType,
            List<String> rentColorOptions) {

        private static CardView from(CardSnapshot snapshot) {
            return new CardView(
                    snapshot.id(),
                    snapshot.type(),
                    snapshot.displayName(),
                    snapshot.bankValueM(),
                    snapshot.valueM(),
                    snapshot.propertyColor(),
                    snapshot.requiredToComplete(),
                    snapshot.allowedColors(),
                    snapshot.actionType(),
                    snapshot.rentColorOptions());
        }

        public String title() {
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            return id;
        }

        public boolean isAction(String expectedActionType) {
            return actionType.equals(expectedActionType);
        }

        public boolean isPropertyLike() {
            return "PROPERTY".equals(type) || "PROPERTY_WILD".equals(type);
        }

        public boolean canUseForDebtPayment() {
            return bankValueM > 0 || (isPropertyLike() && valueM > 0);
        }
    }

    public record PendingDebtView(
            String debtId,
            String creditorPlayerId,
            String debtorPlayerId,
            int amountM,
            String sourceAction,
            String sourceCardId) {

        private static PendingDebtView from(PendingDebtSnapshot snapshot) {
            return new PendingDebtView(
                    snapshot.debtId(),
                    snapshot.creditorPlayerId(),
                    snapshot.debtorPlayerId(),
                    snapshot.amountM(),
                    snapshot.sourceAction(),
                    snapshot.sourceCardId());
        }
    }

    public record PendingTradeView(
            String tradeId,
            String proposerPlayerId,
            String targetPlayerId,
            List<String> offeredCardIds,
            List<String> requestedCardIds,
            String status,
            String createdAt,
            String respondedAt) {

        private static PendingTradeView from(PendingTradeSnapshot snapshot) {
            return new PendingTradeView(
                    snapshot.tradeId(),
                    snapshot.proposerPlayerId(),
                    snapshot.targetPlayerId(),
                    snapshot.offeredCardIds(),
                    snapshot.requestedCardIds(),
                    snapshot.status(),
                    snapshot.createdAt(),
                    snapshot.respondedAt());
        }

        public boolean pending() {
            return "PENDING".equals(status);
        }
    }

    public record PendingActionView(
            String actionId,
            String actorPlayerId,
            String actionType,
            String actionCardId,
            String doubleRentCardId,
            String propertyColor,
            int amountM,
            String targetCardId,
            String targetPropertyRowId,
            String offeredCardId,
            String requestedCardId,
            List<String> targetPlayerIds,
            Map<String, String> responses,
            String createdAt) {

        private static PendingActionView from(PendingActionSnapshot snapshot) {
            return new PendingActionView(
                    snapshot.actionId(),
                    snapshot.actorPlayerId(),
                    snapshot.actionType(),
                    snapshot.actionCardId(),
                    snapshot.doubleRentCardId(),
                    snapshot.propertyColor(),
                    snapshot.amountM(),
                    snapshot.targetCardId(),
                    snapshot.targetPropertyRowId(),
                    snapshot.offeredCardId(),
                    snapshot.requestedCardId(),
                    snapshot.targetPlayerIds(),
                    snapshot.responses(),
                    snapshot.createdAt());
        }

        public boolean awaits(String playerId) {
            if (playerId == null || !targetPlayerIds.contains(playerId)) {
                return false;
            }
            return "PENDING".equals(responses.getOrDefault(playerId, "PENDING"));
        }
    }

    static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static PlayerPhase parsePlayerPhase(String value) {
        if (value == null || value.isBlank()) {
            return PlayerPhase.WAITING;
        }
        try {
            return PlayerPhase.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PlayerPhase.WAITING;
        }
    }
}
