package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.command.CommandType;

import java.util.*;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.*;

public class GameUI {
    private static final double CARD_WIDTH = 94;
    private static final double CARD_HEIGHT = 128;
    private static final double COMPACT_CARD_WIDTH = 64;
    private static final double COMPACT_CARD_HEIGHT = 88;
    private static final double HAND_TOP_Y = 20;
    private static final double HAND_HOVER_Y = 2;
    private static final double HAND_MIN_STEP = 28;
    private static final double HAND_MAX_STEP = 52;
    private static final double HAND_HOVER_SPREAD = 30;

    private final UiSessionContext context;
    private final Map<String, Node> cardNodes = new LinkedHashMap<>();
    private final List<String> handCardIds = new ArrayList<>();
    private UiSnapshot snapshot = UiSnapshot.empty();
    private String selectedCardId;

    @FXML
    private TextField activePlayerField;
    @FXML
    private TextField turnStatusField;
    @FXML
    private TextField identityField;
    @FXML
    private TextField connectionField;
    @FXML
    private TextField targetPlayerField;
    @FXML
    private Label drawPileLabel;
    @FXML
    private Label deckSummaryLabel;
    @FXML
    private Label discardPileSummaryLabel;
    @FXML
    private Label selectedCardSummaryLabel;
    @FXML
    private HBox opponentsRow;
    @FXML
    private Pane handPane;
    @FXML
    private FlowPane bankZone;
    @FXML
    private FlowPane actionZone;
    @FXML
    private FlowPane paymentZone;
    @FXML
    private FlowPane tradeOfferZone;
    @FXML
    private FlowPane tradeRequestZone;
    @FXML
    private FlowPane discardZone;
    @FXML
    private FlowPane brownPropertyZone;
    @FXML
    private FlowPane lightBluePropertyZone;
    @FXML
    private FlowPane pinkPropertyZone;
    @FXML
    private FlowPane orangePropertyZone;
    @FXML
    private FlowPane redPropertyZone;
    @FXML
    private FlowPane yellowPropertyZone;
    @FXML
    private FlowPane greenPropertyZone;
    @FXML
    private FlowPane darkBluePropertyZone;
    @FXML
    private FlowPane railroadPropertyZone;
    @FXML
    private FlowPane utilityPropertyZone;
    @FXML
    private TextArea gameLogArea;
    @FXML
    private TextArea feedbackArea;
    @FXML
    private Button playSelectedButton;
    @FXML
    private Button discardSelectedButton;
    @FXML
    private Button endTurnButton;
    @FXML
    private Button proposeTradeButton;
    @FXML
    private Button payDebtButton;
    @FXML
    private Button acceptTradeButton;
    @FXML
    private Button rejectTradeButton;
    @FXML
    private Button acceptActionButton;
    @FXML
    private Button rejectActionButton;

    public GameUI(UiSessionContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        targetPlayerField.clear();
        targetPlayerField.setPromptText("Optional target player id");
        handPane.widthProperty().addListener((_, _, _) -> layoutHand(null));
        context.setSnapshotListener(this::render);
        context.setMessageListener(this::showFeedback);
        render(context.latestSnapshot());
        Platform.runLater(() -> layoutHand(null));
    }

    @FXML
    private void playSelectedCard() {
        UiSnapshot.CardView card = selectedHandCard().orElse(null);
        if (card == null) {
            showFeedback("Select a card from your hand first.");
            return;
        }

        Map<String, Object> payload = basePayload();
        payload.put("cardId", card.id());

        if ("MONEY".equals(card.type())) {
            payload.put("playMode", "BANK");
            submit(CommandType.PLAY_CARD, payload, "Played " + card.title() + " to your bank.");
            return;
        }

        if (card.isPropertyLike()) {
            payload.put("playMode", "PROPERTY");
            if ("PROPERTY_WILD".equals(card.type())) {
                Optional<String> color = chooseOne("Property Color", card.allowedColors(), UiSnapshot::humanize);
                if (color.isEmpty()) {
                    return;
                }
                payload.put("propertyColor", color.get());
            }
            submit(CommandType.PLAY_CARD, payload, "Played " + card.title() + ".");
            return;
        }

        if ("ACTION".equals(card.type())) {
            playActionCard(card, payload);
        }
    }

    @FXML
    private void discardSelectedCard() {
        int cardsToDiscard = snapshot.currentPlayerHand().size() - 7;
        if (cardsToDiscard <= 0) {
            showFeedback("Discarding is only required when your hand has more than 7 cards.");
            return;
        }

        List<String> cardIds;
        if (selectedCardId != null && cardsToDiscard == 1) {
            cardIds = List.of(selectedCardId);
        } else {
            List<UiSnapshot.CardView> selected = chooseCards(
                    "Discard Cards",
                    snapshot.currentPlayerHand(),
                    cardsToDiscard);
            if (selected.isEmpty()) {
                return;
            }
            cardIds = selected.stream().map(UiSnapshot.CardView::id).toList();
        }

        Map<String, Object> payload = basePayload();
        payload.put("discardedCardIds", cardIds);
        submit(CommandType.DISCARD_CARDS, payload, "Discarded selected cards.");
    }

    @FXML
    private void endTurn() {
        submit(CommandType.END_TURN, basePayload(), "Ended your turn.");
    }

    @FXML
    private void payDebt() {
        List<UiSnapshot.PendingDebtView> debts = snapshot.pendingDebts().stream()
                .filter(debt -> debt.debtorPlayerId().equals(context.playerId()))
                .toList();
        if (debts.isEmpty()) {
            showFeedback("You do not have a pending debt to pay.");
            return;
        }
        Optional<UiSnapshot.PendingDebtView> debt = chooseOne("Pay Debt", debts,
                value -> snapshot.playerName(value.creditorPlayerId()) + " - $" + value.amountM() + "M");
        if (debt.isEmpty()) {
            return;
        }
        List<UiSnapshot.CardView> paymentOptions = snapshot.currentPlayer(context.playerId())
                .map(UiSnapshot.PlayerView::visibleCards)
                .orElse(List.of());
        List<UiSnapshot.CardView> selected = chooseCards("Select Payment Cards", paymentOptions, 1);
        if (selected.isEmpty()) {
            return;
        }

        Map<String, Object> payload = basePayload();
        payload.put("debtId", debt.get().debtId());
        payload.put("paymentCardIds", selected.stream().map(UiSnapshot.CardView::id).toList());
        submit(CommandType.PAY_DEBT, payload, "Submitted payment.");
    }

    @FXML
    private void proposeTrade() {
        List<UiSnapshot.PlayerView> targets = snapshot.opponents(context.playerId()).stream()
                .filter(player -> !player.defeated())
                .toList();
        Optional<UiSnapshot.PlayerView> target = chooseOne("Trade Target", targets, UiSnapshot.PlayerView::displayName);
        if (target.isEmpty()) {
            return;
        }
        List<UiSnapshot.CardView> ownCards = snapshot.currentPlayer(context.playerId())
                .map(UiSnapshot.PlayerView::visibleCards)
                .orElse(List.of());
        Optional<UiSnapshot.CardView> offered = chooseOne("Offer Card", ownCards, this::cardChoiceLabel);
        if (offered.isEmpty()) {
            return;
        }
        Optional<UiSnapshot.CardView> requested = chooseOne(
                "Request Card",
                target.get().visibleCards(),
                this::cardChoiceLabel);
        if (requested.isEmpty()) {
            return;
        }

        Map<String, Object> payload = basePayload();
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("offeredCardIds", List.of(offered.get().id()));
        payload.put("requestedCardIds", List.of(requested.get().id()));
        submit(CommandType.PROPOSE_TRADE, payload, "Proposed trade to " + target.get().displayName() + ".");
    }

    @FXML
    private void acceptTrade() {
        respondToTrade(true);
    }

    @FXML
    private void rejectTrade() {
        respondToTrade(false);
    }

    @FXML
    private void acceptAction() {
        respondToAction(true);
    }

    @FXML
    private void rejectAction() {
        respondToAction(false);
    }

    @FXML
    private void backToLobby() {
        SceneManager.showLobby();
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    private void render(UiSnapshot nextSnapshot) {
        if (nextSnapshot == null || nextSnapshot.isEmpty()) {
            clearTable();
            updateSelectedCardSummary();
            updateButtonStates();
            showFeedback("Connect to a session from the main menu.");
            return;
        }
        if ("LOBBY".equals(nextSnapshot.status())) {
            SceneManager.showLobby();
            return;
        }
        if ("FINISHED".equals(nextSnapshot.status())) {
            SceneManager.showResult();
            return;
        }

        snapshot = nextSnapshot;
        cardNodes.clear();
        handCardIds.clear();
        clearTable();

        activePlayerField.setText(snapshot.playerName(snapshot.activePlayerId()));
        turnStatusField.setText("Actions " + snapshot.actionsUsed() + " used / "
                + snapshot.actionsRemaining() + " remaining");
        identityField.setText(snapshot.playerName(context.playerId()) + " (" + context.playerId() + ")");
        connectionField.setText(context.connectionLabel());
        drawPileLabel.setText("DRAW\n" + snapshot.deckSize());
        setLabelText(deckSummaryLabel, snapshot.deckSize() + " cards");
        setLabelText(discardPileSummaryLabel, snapshot.discardSize() + " cards");

        renderOwnZones();
        renderOpponents();
        renderPendingZones();
        renderLog();
        preserveSelection();
        layoutHand(null);
        updateSelectedCardSummary();
        updateButtonStates();
    }

    private void renderOwnZones() {
        UiSnapshot.PlayerView currentPlayer = snapshot.currentPlayer(context.playerId()).orElse(null);
        if (currentPlayer == null) {
            return;
        }
        currentPlayer.bankCards().forEach(card -> bankZone.getChildren().add(createCardNode(card, true, false)));
        renderPropertyRows(currentPlayer.propertyRows(), propertyZones(), true);

        for (UiSnapshot.CardView card : snapshot.currentPlayerHand()) {
            Node node = createCardNode(card, false, true);
            node.setManaged(false);
            cardNodes.put(card.id(), node);
            handCardIds.add(card.id());
            handPane.getChildren().add(node);
        }
    }

    private void renderOpponents() {
        opponentsRow.getChildren().clear();
        for (UiSnapshot.PlayerView opponent : snapshot.opponents(context.playerId()).stream().limit(4).toList()) {
            opponentsRow.getChildren().add(createOpponentSeat(opponent));
        }
    }

    private Node createOpponentSeat(UiSnapshot.PlayerView opponent) {
        VBox seat = new VBox(5);
        seat.setMinWidth(220);
        seat.setPrefWidth(220);
        seat.getStyleClass().add("opponent-seat");
        Label heading = new Label(opponent.displayName() + " - " + opponent.playerStatus());
        heading.getStyleClass().add("zone-heading");
        heading.setMaxWidth(206);

        Label summary = new Label("Hand " + opponent.handCount()
                + " | Bank $" + opponent.bankTotalValueM() + "M"
                + " | Sets " + opponent.completedSetCount() + "/3");
        summary.getStyleClass().add("zone-caption");
        summary.setMaxWidth(206);

        HBox handBacks = new HBox(5);
        handBacks.setAlignment(Pos.CENTER_LEFT);
        handBacks.getStyleClass().add("opponent-hand-backs");
        populateOpponentHandBacks(handBacks, opponent.handCount());

        FlowPane publicChips = new FlowPane(4, 4);
        publicChips.getStyleClass().add("opponent-public-chips");
        if (opponent.bankCards().isEmpty()) {
            publicChips.getChildren().add(opponentChip("Bank empty"));
        } else {
            opponent.bankCards().stream()
                    .limit(2)
                    .map(card -> opponentChip("Bank: " + card.title()))
                    .forEach(publicChips.getChildren()::add);
            addOverflowChip(publicChips, opponent.bankCards().size(), 2, "bank");
        }

        if (opponent.propertyRows().isEmpty()) {
            publicChips.getChildren().add(opponentChip("No properties"));
        } else {
            opponent.propertyRows().stream()
                    .limit(3)
                    .map(row -> opponentChip(row.label() + " (" + row.cards().size() + ")"))
                    .forEach(publicChips.getChildren()::add);
            addOverflowChip(publicChips, opponent.propertyRows().size(), 3, "sets");
        }

        seat.getChildren().addAll(heading, summary, handBacks, publicChips);
        return seat;
    }

    private Label opponentChip(String text) {
        Label label = new Label(text);
        label.setMaxWidth(96);
        label.setWrapText(false);
        label.getStyleClass().add("opponent-public-chip");
        Tooltip.install(label, new Tooltip(text));
        return label;
    }

    private void addOverflowChip(FlowPane pane, int size, int visibleCount, String label) {
        int overflow = size - visibleCount;
        if (overflow > 0) {
            pane.getChildren().add(opponentChip("+" + overflow + " " + label));
        }
    }

    private void renderPropertyRows(List<UiSnapshot.PropertyRowView> rows, Map<String, FlowPane> zones, boolean compact) {
        for (UiSnapshot.PropertyRowView row : rows) {
            FlowPane zone = zones.get(row.anchorColor());
            if (zone == null) {
                continue;
            }
            VBox rowBox = new VBox(4);
            Label label = new Label(row.label());
            label.getStyleClass().add("zone-caption");
            FlowPane cards = new FlowPane(5, 5);
            row.cards().forEach(card -> cards.getChildren().add(createCardNode(card, compact, false)));
            rowBox.getChildren().addAll(label, cards);
            Tooltip.install(rowBox, new Tooltip("Row ID: " + row.rowId()));
            zone.getChildren().add(rowBox);
        }
    }

    private void renderPendingZones() {
        discardZone.getChildren().add(infoChip("Discard pile: " + snapshot.discardSize() + " cards"));
        snapshot.pendingActions().forEach(action -> actionZone.getChildren().add(infoChip(
                "Pending " + UiSnapshot.humanize(action.actionType())
                        + " from " + snapshot.playerName(action.actorPlayerId()))));
        snapshot.pendingDebts().forEach(debt -> paymentZone.getChildren().add(infoChip(
                snapshot.playerName(debt.debtorPlayerId()) + " owes "
                        + snapshot.playerName(debt.creditorPlayerId()) + " $" + debt.amountM() + "M")));
        snapshot.pendingTrades().forEach(trade -> {
            tradeOfferZone.getChildren().add(infoChip("Offer: " + cardLabels(trade.offeredCardIds())));
            tradeRequestZone.getChildren().add(infoChip("Request: " + cardLabels(trade.requestedCardIds())));
        });
    }

    private void renderLog() {
        List<String> lines = new ArrayList<>();
        lines.add("Status: " + snapshot.status());
        lines.add("Active: " + snapshot.playerName(snapshot.activePlayerId()));
        lines.add("Players: " + snapshot.players().size() + " / " + snapshot.maxPlayers());
        lines.add("Pending debts: " + snapshot.pendingDebts().size());
        lines.add("Pending trades: " + snapshot.pendingTrades().size());
        lines.add("Pending actions: " + snapshot.pendingActions().size());
        if (!snapshot.forfeitedPlayerIds().isEmpty()) {
            lines.add("Forfeits: " + String.join(", ", snapshot.forfeitedPlayerIds()));
        }
        MessageUI.showLines(gameLogArea, lines);
    }

    private void playActionCard(UiSnapshot.CardView card, Map<String, Object> payload) {
        if (card.bankValueM() > 0) {
            Optional<String> mode = chooseOne("Play Action Card", List.of("ACTION", "BANK"),
                    value -> "ACTION".equals(value) ? "Use action effect" : "Bank for $" + card.bankValueM() + "M");
            if (mode.isEmpty()) {
                return;
            }
            payload.put("playMode", mode.get());
            if ("BANK".equals(mode.get())) {
                submit(CommandType.PLAY_CARD, payload, "Banked " + card.title() + ".");
                return;
            }
        } else {
            payload.put("playMode", "ACTION");
        }

        if (!configureActionPayload(card, payload)) {
            return;
        }
        submit(CommandType.PLAY_CARD, payload, "Played " + card.title() + ".");
    }

    private boolean configureActionPayload(UiSnapshot.CardView card, Map<String, Object> payload) {
        return switch (card.actionType()) {
            case "PASS_GO", "ITS_MY_BIRTHDAY" -> true;
            case "DEBT_COLLECTOR" -> chooseTargetPlayer().map(target -> {
                payload.put("targetPlayerId", target.playerId());
                return true;
            }).orElse(false);
            case "RENT_BROWN_OR_LIGHT_BLUE", "RENT_PINK_OR_ORANGE", "RENT_RED_OR_YELLOW",
                    "RENT_DARK_BLUE_OR_GREEN", "RENT_RAILROAD_OR_UTILITY" -> {
                Optional<String> color = chooseRentColor(card);
                if (color.isEmpty()) {
                    yield false;
                }
                payload.put("propertyColor", color.get());
                chooseDoubleRent(card).ifPresent(doubleRent -> payload.put("doubleRentCardId", doubleRent.id()));
                yield true;
            }
            case "WILD_RENT" -> {
                Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer();
                Optional<String> color = chooseRentColor(card);
                if (target.isEmpty() || color.isEmpty()) {
                    yield false;
                }
                payload.put("targetPlayerId", target.get().playerId());
                payload.put("propertyColor", color.get());
                chooseDoubleRent(card).ifPresent(doubleRent -> payload.put("doubleRentCardId", doubleRent.id()));
                yield true;
            }
            case "HOUSE", "HOTEL" -> chooseOwnPropertyRow().map(row -> {
                int index = snapshot.currentPlayer(context.playerId()).orElseThrow().propertyRows().indexOf(row);
                payload.put("propertyRowIndex", Integer.toString(index));
                payload.put("propertyColor", row.anchorColor());
                return true;
            }).orElse(false);
            case "SLY_DEAL" -> configureSlyDeal(payload);
            case "FORCED_DEAL" -> configureForcedDeal(payload);
            case "DEAL_BREAKER" -> configureDealBreaker(payload);
            case "JUST_SAY_NO" -> {
                showFeedback("Use Just Say No when responding to a pending action.");
                yield false;
            }
            case "DOUBLE_THE_RENT" -> {
                showFeedback("Double The Rent must be paired with a rent card, or banked.");
                yield false;
            }
            default -> {
                showFeedback("Unsupported action card: " + card.actionType());
                yield false;
            }
        };
    }

    private Optional<String> chooseRentColor(UiSnapshot.CardView card) {
        List<String> colors = card.rentColorOptions().isEmpty()
                ? snapshot.currentPlayer(context.playerId()).stream()
                        .flatMap(player -> player.propertyRows().stream())
                        .map(UiSnapshot.PropertyRowView::anchorColor)
                        .distinct()
                        .toList()
                : card.rentColorOptions();
        if (colors.size() == 1) {
            return Optional.of(colors.getFirst());
        }
        return chooseOne("Rent Color", colors, UiSnapshot::humanize);
    }

    private Optional<UiSnapshot.CardView> chooseDoubleRent(UiSnapshot.CardView rentCard) {
        List<UiSnapshot.CardView> doubleRentCards = snapshot.currentPlayerHand().stream()
                .filter(card -> !card.id().equals(rentCard.id()))
                .filter(card -> card.isAction("DOUBLE_THE_RENT"))
                .toList();
        if (doubleRentCards.isEmpty() || snapshot.actionsRemaining() < 2) {
            return Optional.empty();
        }
        List<Choice<UiSnapshot.CardView>> choices = new ArrayList<>();
        choices.add(new Choice<>("No Double The Rent", null));
        doubleRentCards.forEach(card -> choices.add(new Choice<>(cardChoiceLabel(card), card)));
        Optional<Choice<UiSnapshot.CardView>> selected = chooseChoice("Double The Rent", choices);
        return selected.map(Choice::value);
    }

    private boolean configureSlyDeal(Map<String, Object> payload) {
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer();
        if (target.isEmpty()) {
            return false;
        }
        Optional<UiSnapshot.CardView> card = chooseOne("Steal Property", target.get().propertyCards(), this::cardChoiceLabel);
        if (card.isEmpty()) {
            return false;
        }
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("targetCardId", card.get().id());
        return true;
    }

    private boolean configureForcedDeal(Map<String, Object> payload) {
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer();
        if (target.isEmpty()) {
            return false;
        }
        List<UiSnapshot.CardView> ownProperties = snapshot.currentPlayer(context.playerId())
                .map(UiSnapshot.PlayerView::propertyCards)
                .orElse(List.of());
        Optional<UiSnapshot.CardView> offered = chooseOne("Offer Property", ownProperties, this::cardChoiceLabel);
        Optional<UiSnapshot.CardView> requested = chooseOne("Request Property", target.get().propertyCards(), this::cardChoiceLabel);
        if (offered.isEmpty() || requested.isEmpty()) {
            return false;
        }
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("offeredCardId", offered.get().id());
        payload.put("requestedCardId", requested.get().id());
        return true;
    }

    private boolean configureDealBreaker(Map<String, Object> payload) {
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer();
        if (target.isEmpty()) {
            return false;
        }
        List<UiSnapshot.PropertyRowView> completeRows = target.get().propertyRows().stream()
                .filter(UiSnapshot.PropertyRowView::complete)
                .toList();
        Optional<UiSnapshot.PropertyRowView> row = chooseOne("Steal Complete Set", completeRows,
                value -> value.label() + " (" + value.rowId() + ")");
        if (row.isEmpty()) {
            return false;
        }
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("targetPropertyRowId", row.get().rowId());
        return true;
    }

    private Optional<UiSnapshot.PlayerView> chooseTargetPlayer() {
        String typedTarget = targetPlayerField.getText() == null ? "" : targetPlayerField.getText().trim();
        if (!typedTarget.isBlank()) {
            Optional<UiSnapshot.PlayerView> typed = snapshot.player(typedTarget);
            if (typed.isPresent() && !typed.get().playerId().equals(context.playerId())) {
                return typed;
            }
        }
        List<UiSnapshot.PlayerView> targets = snapshot.opponents(context.playerId()).stream()
                .filter(player -> !player.defeated())
                .toList();
        return chooseOne("Target Player", targets, UiSnapshot.PlayerView::displayName);
    }

    private Optional<UiSnapshot.PropertyRowView> chooseOwnPropertyRow() {
        return chooseOne("Property Set", snapshot.currentPlayer(context.playerId())
                        .map(UiSnapshot.PlayerView::propertyRows)
                        .orElse(List.of()),
                value -> value.label() + " (" + value.rowId() + ")");
    }

    private void respondToTrade(boolean accepted) {
        List<UiSnapshot.PendingTradeView> trades = snapshot.pendingTrades().stream()
                .filter(trade -> trade.targetPlayerId().equals(context.playerId()))
                .filter(trade -> "PENDING".equals(trade.status()))
                .toList();
        Optional<UiSnapshot.PendingTradeView> trade = chooseOne("Trade Response", trades,
                value -> snapshot.playerName(value.proposerPlayerId()));
        if (trade.isEmpty()) {
            showFeedback("No pending trade is waiting for your response.");
            return;
        }
        Map<String, Object> payload = basePayload();
        payload.put("tradeId", trade.get().tradeId());
        payload.put("accepted", accepted);
        submit(CommandType.RESPOND_TRADE, payload, accepted ? "Accepted trade." : "Rejected trade.");
    }

    private void respondToAction(boolean accepted) {
        List<UiSnapshot.PendingActionView> actions = snapshot.pendingActions().stream()
                .filter(action -> action.awaits(context.playerId()))
                .toList();
        Optional<UiSnapshot.PendingActionView> action = chooseOne("Action Response", actions,
                value -> UiSnapshot.humanize(value.actionType()) + " from " + snapshot.playerName(value.actorPlayerId()));
        if (action.isEmpty()) {
            showFeedback("No pending action is waiting for your response.");
            return;
        }
        Map<String, Object> payload = basePayload();
        payload.put("actionId", action.get().actionId());
        payload.put("accepted", accepted);
        if (!accepted) {
            List<UiSnapshot.CardView> justSayNoCards = snapshot.currentPlayerHand().stream()
                    .filter(card -> card.isAction("JUST_SAY_NO"))
                    .toList();
            Optional<UiSnapshot.CardView> justSayNo = chooseOne("Just Say No Card", justSayNoCards, this::cardChoiceLabel);
            if (justSayNo.isEmpty()) {
                return;
            }
            payload.put("justSayNoCardId", justSayNo.get().id());
        }
        submit(CommandType.RESPOND_ACTION, payload, accepted ? "Accepted action." : "Played Just Say No.");
    }

    private Optional<UiSnapshot.CardView> selectedHandCard() {
        return snapshot.currentPlayerHand().stream()
                .filter(card -> card.id().equals(selectedCardId))
                .findFirst();
    }

    private void submit(CommandType commandType, Map<String, Object> payload, String successMessage) {
        context.sendCommand(commandType, payload)
                .thenAccept(result -> {
                    if (result.success()) {
                        Platform.runLater(() -> showFeedback(successMessage));
                    }
                });
    }

    private Map<String, Object> basePayload() {
        return new LinkedHashMap<>(context.sessionCommandPayload());
    }

    private <T> Optional<T> chooseOne(String title, List<T> values, Function<T, String> labeler) {
        if (values == null || values.isEmpty()) {
            showFeedback("No choices available for " + title + ".");
            return Optional.empty();
        }
        return chooseChoice(title, values.stream()
                .map(value -> new Choice<>(labeler.apply(value), value))
                .toList())
                .map(Choice::value);
    }

    private <T> Optional<Choice<T>> chooseChoice(String title, List<Choice<T>> choices) {
        ChoiceDialog<Choice<T>> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(title);
        return dialog.showAndWait();
    }

    private List<UiSnapshot.CardView> chooseCards(String title, List<UiSnapshot.CardView> options, int minimum) {
        if (options == null || options.isEmpty()) {
            showFeedback("No cards are available for " + title + ".");
            return List.of();
        }
        Dialog<List<UiSnapshot.CardView>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Select at least " + minimum + " card(s).");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        ListView<Choice<UiSnapshot.CardView>> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setItems(FXCollections.observableArrayList(options.stream()
                .map(card -> new Choice<>(cardChoiceLabel(card), card))
                .toList()));
        dialog.getDialogPane().setContent(listView);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return List.of();
            }
            return listView.getSelectionModel().getSelectedItems().stream()
                    .map(Choice::value)
                    .toList();
        });
        List<UiSnapshot.CardView> selected = dialog.showAndWait().orElse(List.of());
        if (selected.size() < minimum) {
            showFeedback("Select at least " + minimum + " card(s).");
            return List.of();
        }
        return selected;
    }

    private Node createCardNode(UiSnapshot.CardView card, boolean compact, boolean selectable) {
        VBox cardBox = new VBox(3);
        cardBox.setAlignment(Pos.TOP_CENTER);
        cardBox.setUserData(card.id());
        cardBox.getStyleClass().addAll("deal-card", typeStyle(card));

        Region colorBand = new Region();
        colorBand.getStyleClass().add("card-color-band");
        colorBand.setStyle("-fx-background-color: " + colorBandCss(card) + ";");

        HBox header = new HBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label(card.type().isBlank() ? "CARD" : card.type());
        typeLabel.getStyleClass().add("card-type-label");
        HBox.setHgrow(typeLabel, Priority.ALWAYS);
        Label valueBadge = new Label(valueBadge(card));
        valueBadge.getStyleClass().add("card-value-badge");
        header.getChildren().addAll(typeLabel, valueBadge);

        StackPane art = new StackPane();
        art.getStyleClass().add("card-art-placeholder");
        Label artText = new Label(symbolFor(card));
        artText.getStyleClass().add("card-art-text");
        art.getChildren().add(artText);

        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label detail = new Label(cardDetail(card));
        detail.getStyleClass().add("card-detail");
        detail.setWrapText(true);

        cardBox.getChildren().addAll(colorBand, header, art, title, detail);
        applyCardSize(cardBox, compact);
        Tooltip.install(cardBox, new Tooltip(card.title() + "\nID: " + card.id()));
        if (selectable) {
            cardBox.setOnMouseClicked(event -> {
                selectCard(card.id());
                showFeedback("Selected " + card.title() + ".");
                event.consume();
            });
            cardBox.setOnMouseEntered(_ -> {
                int index = handCardIds.indexOf(card.id());
                if (index >= 0) {
                    layoutHand(index);
                }
            });
            cardBox.setOnMouseExited(_ -> {
                if (handCardIds.contains(card.id())) {
                    layoutHand(null);
                }
            });
        }
        return cardBox;
    }

    private Node infoChip(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("zone-caption");
        return label;
    }

    private void clearTable() {
        List.of(bankZone, actionZone, paymentZone, tradeOfferZone, tradeRequestZone, discardZone,
                        brownPropertyZone, lightBluePropertyZone, pinkPropertyZone, orangePropertyZone,
                        redPropertyZone, yellowPropertyZone, greenPropertyZone, darkBluePropertyZone,
                        railroadPropertyZone, utilityPropertyZone)
                .forEach(zone -> {
                    if (zone != null) {
                        zone.getChildren().clear();
                    }
                });
        if (handPane != null) {
            handPane.getChildren().clear();
        }
        if (opponentsRow != null) {
            opponentsRow.getChildren().clear();
        }
        if (drawPileLabel != null) {
            drawPileLabel.setText("DRAW\n0");
        }
        setLabelText(deckSummaryLabel, "0 cards");
        setLabelText(discardPileSummaryLabel, "0 cards");
    }

    private Map<String, FlowPane> propertyZones() {
        Map<String, FlowPane> zones = new LinkedHashMap<>();
        zones.put("BROWN", brownPropertyZone);
        zones.put("LIGHT_BLUE", lightBluePropertyZone);
        zones.put("PINK", pinkPropertyZone);
        zones.put("ORANGE", orangePropertyZone);
        zones.put("RED", redPropertyZone);
        zones.put("YELLOW", yellowPropertyZone);
        zones.put("GREEN", greenPropertyZone);
        zones.put("DARK_BLUE", darkBluePropertyZone);
        zones.put("RAILROAD", railroadPropertyZone);
        zones.put("UTILITY", utilityPropertyZone);
        return zones;
    }

    private void selectCard(String cardId) {
        selectedCardId = cardId;
        cardNodes.forEach((id, node) -> toggleStyle(node, "deal-card-selected", id.equals(cardId)));
        updateSelectedCardSummary();
        updateButtonStates();
    }

    private void preserveSelection() {
        if (selectedCardId == null || !cardNodes.containsKey(selectedCardId)) {
            selectedCardId = null;
            updateSelectedCardSummary();
            updateButtonStates();
            return;
        }
        selectCard(selectedCardId);
    }

    private void updateSelectedCardSummary() {
        if (selectedCardSummaryLabel == null) {
            return;
        }
        if (snapshot == null || snapshot.isEmpty()) {
            selectedCardSummaryLabel.setText("Connect to a session to view your private hand.");
            return;
        }
        Optional<UiSnapshot.CardView> selected = selectedHandCard();
        if (selected.isPresent()) {
            UiSnapshot.CardView card = selected.get();
            selectedCardSummaryLabel.setText("Selected: " + card.title() + " - " + selectedCardHint(card));
            return;
        }
        int cardsToDiscard = snapshot.currentPlayerHand().size() - 7;
        if (cardsToDiscard > 0 && isYourTurn()) {
            selectedCardSummaryLabel.setText("Choose " + cardsToDiscard
                    + " card(s) to discard before ending your turn.");
            return;
        }
        if (isYourTurn()) {
            selectedCardSummaryLabel.setText("Select a card from your private hand, then choose Play Selected.");
            return;
        }
        selectedCardSummaryLabel.setText("Waiting for " + snapshot.playerName(snapshot.activePlayerId())
                + ". You can still answer pending trade, debt, or action prompts.");
    }

    private String selectedCardHint(UiSnapshot.CardView card) {
        if ("MONEY".equals(card.type())) {
            return "bank for $" + card.bankValueM() + "M.";
        }
        if (card.isPropertyLike()) {
            return "play to your property area.";
        }
        if ("ACTION".equals(card.type()) && card.bankValueM() > 0) {
            return "use the action effect or bank for $" + card.bankValueM() + "M.";
        }
        if ("ACTION".equals(card.type())) {
            return "use the action effect.";
        }
        return "ready to play.";
    }

    private void updateButtonStates() {
        boolean hasSnapshot = snapshot != null && !snapshot.isEmpty();
        boolean yourTurn = hasSnapshot && isYourTurn();
        boolean hasSelection = hasSnapshot && selectedHandCard().isPresent();
        int cardsToDiscard = hasSnapshot ? snapshot.currentPlayerHand().size() - 7 : 0;

        setButtonText(playSelectedButton, hasSelection ? "Play Selected" : "Select a Card");
        setButtonText(discardSelectedButton, cardsToDiscard > 0
                ? "Discard " + cardsToDiscard + " Card" + (cardsToDiscard == 1 ? "" : "s")
                : "No Discard Needed");
        setButtonText(endTurnButton, yourTurn ? "End Turn" : "Waiting");

        setButtonDisabled(playSelectedButton, !yourTurn || !hasSelection);
        setButtonDisabled(discardSelectedButton, !yourTurn || cardsToDiscard <= 0);
        setButtonDisabled(endTurnButton, !yourTurn);
        setButtonDisabled(proposeTradeButton, !yourTurn || !canProposeTrade());
        setButtonDisabled(payDebtButton, !hasPendingDebtForCurrentPlayer());
        setButtonDisabled(acceptTradeButton, !hasPendingTradeForCurrentPlayer());
        setButtonDisabled(rejectTradeButton, !hasPendingTradeForCurrentPlayer());
        setButtonDisabled(acceptActionButton, !hasPendingActionForCurrentPlayer());
        setButtonDisabled(rejectActionButton, !hasPendingActionForCurrentPlayer());
    }

    private boolean isYourTurn() {
        return snapshot != null && context.playerId().equals(snapshot.activePlayerId());
    }

    private boolean canProposeTrade() {
        return snapshot.opponents(context.playerId()).stream().anyMatch(player -> !player.defeated())
                && snapshot.currentPlayer(context.playerId())
                .map(player -> !player.visibleCards().isEmpty())
                .orElse(false);
    }

    private boolean hasPendingDebtForCurrentPlayer() {
        return snapshot.pendingDebts().stream()
                .anyMatch(debt -> debt.debtorPlayerId().equals(context.playerId()));
    }

    private boolean hasPendingTradeForCurrentPlayer() {
        return snapshot.pendingTrades().stream()
                .filter(trade -> trade.targetPlayerId().equals(context.playerId()))
                .anyMatch(trade -> "PENDING".equals(trade.status()));
    }

    private boolean hasPendingActionForCurrentPlayer() {
        return snapshot.pendingActions().stream().anyMatch(action -> action.awaits(context.playerId()));
    }

    private void setButtonDisabled(Button button, boolean disabled) {
        if (button != null) {
            button.setDisable(disabled);
        }
    }

    private void setButtonText(Button button, String text) {
        if (button != null) {
            button.setText(text);
        }
    }

    private void setLabelText(Label label, String text) {
        if (label != null) {
            label.setText(text);
        }
    }

    private void layoutHand(Integer hoveredIndex) {
        if (handPane == null) {
            return;
        }
        List<Node> orderedNodes = handCardIds.stream()
                .map(cardNodes::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!handPane.getChildren().equals(orderedNodes)) {
            handPane.getChildren().setAll(orderedNodes);
        }

        int count = orderedNodes.size();
        if (count == 0) {
            return;
        }

        double width = handPane.getWidth() > 0 ? handPane.getWidth() : handPane.getPrefWidth();
        if (width <= 0) {
            width = 760;
        }
        double availableForSteps = Math.max(0, width - CARD_WIDTH - 28);
        double step = count == 1 ? 0 : availableForSteps / (count - 1);
        step = Math.clamp(step, HAND_MIN_STEP, HAND_MAX_STEP);

        boolean hasHover = hoveredIndex != null && hoveredIndex >= 0 && hoveredIndex < count;
        double spread = hasHover ? Math.min(HAND_HOVER_SPREAD, step * 0.8) : 0;
        double totalWidth = CARD_WIDTH + (count - 1) * step + (hasHover ? spread * 2 : 0);
        double startX = Math.max(10, (width - totalWidth) / 2);

        for (int i = 0; i < count; i++) {
            Node node = orderedNodes.get(i);
            double x = startX + i * step;
            if (hasHover && i < hoveredIndex) {
                x -= spread;
            } else if (hasHover && i > hoveredIndex) {
                x += spread;
            }
            boolean hovered = hasHover && i == hoveredIndex;
            node.setLayoutX(x);
            node.setLayoutY(hovered ? HAND_HOVER_Y : HAND_TOP_Y);
            node.setManaged(false);
            applyCardSize(node, false);
            toggleStyle(node, "hand-card-hovered", hovered);
            node.toFront();
        }
        if (hasHover) {
            orderedNodes.get(hoveredIndex).toFront();
        }
    }

    private void populateOpponentHandBacks(HBox container, int count) {
        container.getChildren().clear();
        int visibleBacks = Math.min(count, 5);
        for (int i = 0; i < visibleBacks; i++) {
            Region cardBack = new Region();
            cardBack.getStyleClass().add("card-back");
            container.getChildren().add(cardBack);
        }
        Label countLabel = new Label(count + " cards");
        countLabel.getStyleClass().add("opponent-hand-count");
        container.getChildren().add(countLabel);
    }

    private void applyCardSize(Node node, boolean compact) {
        toggleStyle(node, "compact-deal-card", compact);
        if (node instanceof Region region) {
            double width = compact ? COMPACT_CARD_WIDTH : CARD_WIDTH;
            double height = compact ? COMPACT_CARD_HEIGHT : CARD_HEIGHT;
            region.setMinSize(width, height);
            region.setPrefSize(width, height);
            region.setMaxSize(width, height);
        }
    }

    private void toggleStyle(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private String cardLabels(List<String> cardIds) {
        return cardIds.stream()
                .map(cardId -> snapshot.card(cardId).map(UiSnapshot.CardView::title).orElse(cardId))
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String cardChoiceLabel(UiSnapshot.CardView card) {
        return card.title() + " (" + card.id() + ")";
    }

    private String typeStyle(UiSnapshot.CardView card) {
        return "deal-card-" + card.type().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String valueBadge(UiSnapshot.CardView card) {
        if (card.bankValueM() > 0) {
            return "$" + card.bankValueM() + "M";
        }
        if (card.requiredToComplete() > 0) {
            return card.requiredToComplete() + " SET";
        }
        return "";
    }

    private String symbolFor(UiSnapshot.CardView card) {
        return switch (card.type()) {
            case "MONEY" -> "$";
            case "PROPERTY", "PROPERTY_WILD" -> "SET";
            case "ACTION" -> "ACT";
            default -> "";
        };
    }

    private String cardDetail(UiSnapshot.CardView card) {
        if ("PROPERTY".equals(card.type())) {
            return UiSnapshot.humanize(card.propertyColor()) + " property";
        }
        if ("PROPERTY_WILD".equals(card.type())) {
            return "Wild: " + card.allowedColors().stream()
                    .map(UiSnapshot::humanize)
                    .reduce((left, right) -> left + " / " + right)
                    .orElse("");
        }
        if ("ACTION".equals(card.type())) {
            return UiSnapshot.humanize(card.actionType());
        }
        if (card.valueM() > 0) {
            return "$" + card.valueM() + "M money";
        }
        return "";
    }

    private String colorBandCss(UiSnapshot.CardView card) {
        if ("PROPERTY".equals(card.type())) {
            return propertyCss(card.propertyColor());
        }
        if ("PROPERTY_WILD".equals(card.type())) {
            List<String> colors = card.allowedColors().stream().map(this::propertyCss).toList();
            if (colors.isEmpty()) {
                return "#98a2ad";
            }
            if (colors.size() == 1) {
                return colors.getFirst();
            }
            return "linear-gradient(to right, " + colors.get(0) + " 0%, " + colors.get(0) + " 50%, "
                    + colors.get(1) + " 50%, " + colors.get(1) + " 100%)";
        }
        if ("ACTION".equals(card.type())) {
            return "#a61d24";
        }
        return "#23845b";
    }

    private String propertyCss(String color) {
        return switch (color) {
            case "BROWN" -> "#8b5a2b";
            case "LIGHT_BLUE" -> "#8ad8ff";
            case "PINK" -> "#d94ca8";
            case "ORANGE" -> "#f28a2e";
            case "RED" -> "#d83240";
            case "YELLOW" -> "#f2d64b";
            case "GREEN" -> "#248f51";
            case "DARK_BLUE" -> "#244a96";
            case "RAILROAD" -> "#d8dde3";
            default -> "#98a2ad";
        };
    }

    private void showFeedback(String message) {
        MessageUI.show(feedbackArea, message);
    }

    private record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
