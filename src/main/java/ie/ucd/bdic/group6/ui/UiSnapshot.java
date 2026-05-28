package ie.ucd.bdic.group6.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class UiSnapshot {
    private static final UiSnapshot EMPTY = new UiSnapshot(Map.of());

    private final Map<String, Object> data;
    private final List<PlayerView> players;
    private final List<CardView> currentPlayerHand;
    private final List<PendingDebtView> pendingDebts;
    private final List<PendingTradeView> pendingTrades;
    private final List<PendingActionView> pendingActions;
    private final List<String> forfeitedPlayerIds;

    private UiSnapshot(Map<String, Object> data) {
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.players = maps(this.data.get("players")).stream().map(PlayerView::from).toList();
        this.currentPlayerHand = maps(this.data.get("currentPlayerHand")).stream().map(CardView::from).toList();
        this.pendingDebts = maps(this.data.get("pendingDebts")).stream().map(PendingDebtView::from).toList();
        this.pendingTrades = maps(this.data.get("pendingTrades")).stream().map(PendingTradeView::from).toList();
        this.pendingActions = maps(this.data.get("pendingActions")).stream().map(PendingActionView::from).toList();
        this.forfeitedPlayerIds = strings(this.data.get("forfeitedPlayerIds"));
    }

    public static UiSnapshot empty() {
        return EMPTY;
    }

    public static UiSnapshot from(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return empty();
        }
        return new UiSnapshot(data);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Map<String, Object> data() {
        return data;
    }

    public String sessionId() {
        return text(data.get("sessionId"));
    }

    public String sessionCode() {
        return text(data.get("sessionCode"));
    }

    public String status() {
        return text(data.get("status"));
    }

    public String hostPlayerId() {
        return text(data.get("hostPlayerId"));
    }

    public int maxPlayers() {
        return number(data.get("maxPlayers"));
    }

    public String activePlayerId() {
        return text(data.get("activePlayerId"));
    }

    public int actionsUsed() {
        return number(data.get("actionsUsed"));
    }

    public int actionsRemaining() {
        return number(data.get("actionsRemaining"));
    }

    public String winnerPlayerId() {
        return text(data.get("winnerPlayerId"));
    }

    public int deckSize() {
        return number(data.get("deckSize"));
    }

    public int discardSize() {
        return number(data.get("discardSize"));
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
        return forfeitedPlayerIds;
    }

    public Optional<PlayerView> player(String playerId) {
        return players.stream().filter(player -> player.playerId().equals(playerId)).findFirst();
    }

    public Optional<PlayerView> currentPlayer(String playerId) {
        return player(playerId);
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

    public record PlayerView(
            String playerId,
            String name,
            String playerStatus,
            boolean connected,
            String disconnectedAt,
            boolean defeated,
            int handCount,
            int bankTotalValueM,
            List<CardView> bankCards,
            List<PropertyRowView> propertyRows) {

        private static PlayerView from(Map<String, Object> data) {
            return new PlayerView(
                    text(data.get("playerId")),
                    text(data.get("name")),
                    text(data.get("playerStatus")),
                    bool(data.get("connected")),
                    text(data.get("disconnectedAt")),
                    bool(data.get("defeated")),
                    number(data.get("handCount")),
                    number(data.get("bankTotalValueM")),
                    maps(data.get("bankCards")).stream().map(CardView::from).toList(),
                    maps(data.get("propertyRows")).stream().map(PropertyRowView::from).toList());
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

        public List<CardView> visibleCards() {
            List<CardView> cards = new ArrayList<>();
            cards.addAll(bankCards);
            cards.addAll(propertyCards());
            return List.copyOf(cards);
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

        private static PropertyRowView from(Map<String, Object> data) {
            return new PropertyRowView(
                    text(data.get("rowId")),
                    text(data.get("anchorColor")),
                    bool(data.get("complete")),
                    number(data.get("rentValue")),
                    bool(data.get("hasHouse")),
                    bool(data.get("hasHotel")),
                    maps(data.get("cards")).stream().map(CardView::from).toList());
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

        private static CardView from(Map<String, Object> data) {
            return new CardView(
                    text(data.get("id")),
                    text(data.get("type")),
                    text(data.get("displayName")),
                    number(data.get("bankValueM")),
                    number(data.get("valueM")),
                    text(data.get("propertyColor")),
                    number(data.get("requiredToComplete")),
                    strings(data.get("allowedColors")),
                    text(data.get("actionType")),
                    strings(data.get("rentColorOptions")));
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
    }

    public record PendingDebtView(
            String debtId,
            String creditorPlayerId,
            String debtorPlayerId,
            int amountM,
            String sourceAction,
            String sourceCardId) {

        private static PendingDebtView from(Map<String, Object> data) {
            return new PendingDebtView(
                    text(data.get("debtId")),
                    text(data.get("creditorPlayerId")),
                    text(data.get("debtorPlayerId")),
                    number(data.get("amountM")),
                    text(data.get("sourceAction")),
                    text(data.get("sourceCardId")));
        }
    }

    public record PendingTradeView(
            String tradeId,
            String proposerPlayerId,
            String targetPlayerId,
            List<String> offeredCardIds,
            List<String> requestedCardIds,
            String status) {

        private static PendingTradeView from(Map<String, Object> data) {
            return new PendingTradeView(
                    text(data.get("tradeId")),
                    text(data.get("proposerPlayerId")),
                    text(data.get("targetPlayerId")),
                    strings(data.get("offeredCardIds")),
                    strings(data.get("requestedCardIds")),
                    text(data.get("status")));
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
            Map<String, String> responses) {

        private static PendingActionView from(Map<String, Object> data) {
            return new PendingActionView(
                    text(data.get("actionId")),
                    text(data.get("actorPlayerId")),
                    text(data.get("actionType")),
                    text(data.get("actionCardId")),
                    text(data.get("doubleRentCardId")),
                    text(data.get("propertyColor")),
                    number(data.get("amountM")),
                    text(data.get("targetCardId")),
                    text(data.get("targetPropertyRowId")),
                    text(data.get("offeredCardId")),
                    text(data.get("requestedCardId")),
                    strings(data.get("targetPlayerIds")),
                    stringMap(data.get("responses")));
        }

        public boolean awaits(String playerId) {
            return targetPlayerIds.contains(playerId) && !responses.containsKey(playerId);
        }
    }

    static List<Map<String, Object>> maps(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object value : iterable) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, entryValue) -> copy.put(Objects.toString(key, ""), entryValue));
                values.add(Map.copyOf(copy));
            }
        }
        return List.copyOf(values);
    }

    static List<String> strings(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object value : iterable) {
            String text = text(value);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(Objects.toString(key, ""), text(value)));
        return Map.copyOf(copy);
    }

    static String text(Object raw) {
        if (raw == null) {
            return "";
        }
        return raw.toString();
    }

    static int number(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null || raw.toString().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean bool(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(text(raw));
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
}
