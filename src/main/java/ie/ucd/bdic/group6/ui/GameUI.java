package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.app.GameSessionCoordinator;
import ie.ucd.bdic.group6.command.CommandType;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
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

    private final GameSessionCoordinator context;
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
    private TextField roomCodeField;
    @FXML
    private Label deckSummaryLabel;
    @FXML
    private Label discardPileSummaryLabel;
    @FXML
    private Label selectedCardSummaryLabel;
    @FXML
    private HBox opponentsRow;
    @FXML
    private ScrollPane handScrollPane;
    @FXML
    private HBox handCardsHBox;
    @FXML
    private HBox bankZone;
    @FXML
    private FlowPane tableActivityZone;
    @FXML
    private ScrollPane propertyRowsScroll;
    @FXML
    private FlowPane propertyRowsZone;
    @FXML
    private TextArea gameLogArea;
    @FXML
    private Label statusMessageLabel;
    @FXML
    private Button playSelectedButton;
    @FXML
    private Button discardSelectedButton;
    @FXML
    private Button endTurnButton;
    @FXML
    private Button proposeTradeButton;

    private boolean pendingPromptShowing;

    public GameUI(GameSessionCoordinator context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        configurePropertyRowsScrolling();
        context.setSnapshotListener(this::render);
        context.setMessageListener(this::showServerMessage);
        render(context.latestSnapshot());
    }

    private void configurePropertyRowsScrolling() {
        if (propertyRowsScroll == null || propertyRowsZone == null) {
            return;
        }
        Runnable updateWrapLength = () -> {
            double width = propertyRowsScroll.getViewportBounds().getWidth();
            if (width > 0) {
                propertyRowsZone.setPrefWrapLength(width);
            }
        };
        propertyRowsScroll.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> updateWrapLength.run());
        Platform.runLater(updateWrapLength);
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
        int cardsToDiscard = snapshot.cardsToDiscard();
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
    private void proposeTrade() {
        UiSnapshot.PlayerView currentPlayer = snapshot.currentPlayer(context.playerId()).orElse(null);
        if (currentPlayer == null) {
            showFeedback("You are not in this game.");
            return;
        }
        List<UiSnapshot.PlayerView> eligibleOpponents = snapshot.opponents(context.playerId()).stream()
                .filter(player -> !player.defeated())
                .toList();
        List<UiSnapshot.CardView> ownCards = currentPlayer.visibleCards().stream()
                .filter(offered -> eligibleOpponents.stream()
                        .anyMatch(player -> !player.supportedTradeRequestsFor(offered, currentPlayer).isEmpty()))
                .toList();
        Optional<UiSnapshot.CardView> offered = chooseOne("Offer Card", ownCards, this::cardChoiceLabel);
        if (offered.isEmpty()) {
            return;
        }
        List<UiSnapshot.PlayerView> targets = eligibleOpponents.stream()
                .filter(player -> !player.supportedTradeRequestsFor(offered.get(), currentPlayer).isEmpty())
                .toList();
        Optional<UiSnapshot.PlayerView> target = chooseOne("Trade Target", targets, UiSnapshot.PlayerView::displayName);
        if (target.isEmpty()) {
            return;
        }
        Optional<UiSnapshot.CardView> requested = chooseOne(
                "Request Card",
                target.get().supportedTradeRequestsFor(offered.get(), currentPlayer),
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
        identityField.setText(snapshot.playerName(context.playerId()));
        roomCodeField.setText(context.roomCode());
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
        maybeShowPendingPrompts();
    }

    private void renderOwnZones() {
        UiSnapshot.PlayerView currentPlayer = snapshot.currentPlayer(context.playerId()).orElse(null);
        if (currentPlayer == null) {
            return;
        }
        if (bankZone != null) {
            if (currentPlayer.bankCards().isEmpty()) {
                bankZone.getChildren().add(infoChip("No money banked yet."));
            } else {
                currentPlayer.bankCards().forEach(card -> bankZone.getChildren().add(createCardNode(card, true, false)));
            }
        }
        renderPropertyRows(currentPlayer.propertyRows());

        for (UiSnapshot.CardView card : snapshot.currentPlayerHand()) {
            Node node = createCardNode(card, false, true);
            cardNodes.put(card.id(), node);
            handCardIds.add(card.id());
            handCardsHBox.getChildren().add(node);
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

    private void renderPropertyRows(List<UiSnapshot.PropertyRowView> rows) {
        if (propertyRowsZone == null) {
            return;
        }
        if (rows.isEmpty()) {
            propertyRowsZone.getChildren().add(infoChip("No properties in play yet."));
            return;
        }
        for (UiSnapshot.PropertyRowView row : rows) {
            propertyRowsZone.getChildren().add(createPropertyRowNode(row));
        }
    }

    private Node createPropertyRowNode(UiSnapshot.PropertyRowView row) {
        VBox rowBox = new VBox(6);
        rowBox.setMinWidth(184);
        rowBox.setPrefWidth(220);
        rowBox.getStyleClass().add("property-row-card");

        Label label = new Label(row.label());
        label.getStyleClass().addAll("zone-heading", propertyLabelStyle(row.anchorColor()));
        label.setMaxWidth(206);

        Label summary = new Label(row.progressLabel() + " | Rent $" + row.rentValue() + "M");
        summary.getStyleClass().add("zone-caption");
        summary.setMaxWidth(206);

        FlowPane cards = new FlowPane(5, 5);
        row.cards().forEach(card -> cards.getChildren().add(createCardNode(card, true, false)));

        rowBox.getChildren().addAll(label, summary, cards);
        Tooltip.install(rowBox, new Tooltip("Row ID: " + row.rowId()));
        return rowBox;
    }

    private void renderPendingZones() {
        if (tableActivityZone == null) {
            return;
        }
        snapshot.tableActivityLines().stream()
                .map(this::activityChip)
                .forEach(tableActivityZone.getChildren()::add);
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
            case "HOUSE", "HOTEL" -> chooseOwnPropertyRow(card.actionType()).map(row -> {
                payload.put("targetPropertyRowId", row.rowId());
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
        List<String> colors = snapshot.currentPlayer(context.playerId())
                .map(player -> player.rentableColorsFor(card))
                .orElse(List.of());
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
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer(player -> !player.stealablePropertyCards().isEmpty());
        if (target.isEmpty()) {
            return false;
        }
        Optional<UiSnapshot.CardView> card = chooseOne("Steal Property", target.get().stealablePropertyCards(), this::cardChoiceLabel);
        if (card.isEmpty()) {
            return false;
        }
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("targetCardId", card.get().id());
        return true;
    }

    private boolean configureForcedDeal(Map<String, Object> payload) {
        List<UiSnapshot.CardView> ownProperties = snapshot.currentPlayer(context.playerId())
                .map(UiSnapshot.PlayerView::stealablePropertyCards)
                .orElse(List.of());
        Optional<UiSnapshot.CardView> offered = chooseOne("Offer Property", ownProperties, this::cardChoiceLabel);
        if (offered.isEmpty()) {
            return false;
        }
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer(player -> !player.stealablePropertyCards().isEmpty());
        if (target.isEmpty()) {
            return false;
        }
        Optional<UiSnapshot.CardView> requested = chooseOne(
                "Request Property",
                target.get().stealablePropertyCards(),
                this::cardChoiceLabel);
        if (requested.isEmpty()) {
            return false;
        }
        payload.put("targetPlayerId", target.get().playerId());
        payload.put("offeredCardId", offered.get().id());
        payload.put("requestedCardId", requested.get().id());
        return true;
    }

    private boolean configureDealBreaker(Map<String, Object> payload) {
        Optional<UiSnapshot.PlayerView> target = chooseTargetPlayer(player -> player.propertyRows().stream()
                .anyMatch(UiSnapshot.PropertyRowView::complete));
        if (target.isEmpty()) {
            return false;
        }
        List<UiSnapshot.PropertyRowView> completeRows = target.get().propertyRows().stream()
                .filter(UiSnapshot.PropertyRowView::complete).toList();
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
        return chooseTargetPlayer(_ -> true);
    }

    private Optional<UiSnapshot.PlayerView> chooseTargetPlayer(Predicate<UiSnapshot.PlayerView> filter) {
        List<UiSnapshot.PlayerView> targets = snapshot.opponents(context.playerId()).stream()
                .filter(player -> !player.defeated())
                .filter(filter)
                .toList();
        return chooseOne("Target Player", targets, UiSnapshot.PlayerView::displayName);
    }

    private Optional<UiSnapshot.PropertyRowView> chooseOwnPropertyRow(String actionType) {
        return chooseOne("Property Set", snapshot.currentPlayer(context.playerId())
                        .map(player -> player.improvableRows(actionType))
                        .orElse(List.of()),
                value -> value.label() + " (" + value.rowId() + ")");
    }

    private void maybeShowPendingPrompts() {
        if (pendingPromptShowing || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Platform.runLater(this::showNextPendingPrompt);
    }

    private void showNextPendingPrompt() {
        if (pendingPromptShowing || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (UiSnapshot.PendingActionView action : snapshot.pendingActionsAwaiting(context.playerId())) {
            showActionPrompt(action);
            return;
        }
        for (UiSnapshot.PendingDebtView debt : snapshot.pendingDebtsForDebtor(context.playerId())) {
            promptDebtPayment(debt);
            return;
        }
        if (snapshot.canRespondToTrade(context.playerId())) {
            for (UiSnapshot.PendingTradeView trade : snapshot.pendingTradesForTarget(context.playerId())) {
                showTradePrompt(trade);
                return;
            }
        }
    }

    private void showActionPrompt(UiSnapshot.PendingActionView action) {
        pendingPromptShowing = true;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Action Response");
        dialog.setHeaderText(UiSnapshot.humanize(action.actionType()) + " from "
                + snapshot.playerName(action.actorPlayerId()));
        dialog.setContentText(buildActionPromptDetails(action));

        ButtonType allow = new ButtonType("Allow", ButtonBar.ButtonData.OK_DONE);
        ButtonType justSayNo = new ButtonType("Just Say No", ButtonBar.ButtonData.NO);
        dialog.getDialogPane().getButtonTypes().addAll(allow, justSayNo);
        dialog.setOnCloseRequest(event -> event.consume());

        boolean hasJustSayNo = snapshot.currentPlayerHand().stream()
                .anyMatch(card -> card.isAction("JUST_SAY_NO"));
        Button justSayNoButton = (Button) dialog.getDialogPane().lookupButton(justSayNo);
        if (justSayNoButton != null) {
            justSayNoButton.setDisable(!hasJustSayNo);
        }

        Optional<ButtonType> result = dialog.showAndWait();
        pendingPromptShowing = false;

        if (result.isEmpty()) {
            maybeShowPendingPrompts();
            return;
        }
        if (result.get() == allow) {
            submitActionResponse(action, true);
            return;
        }
        submitActionResponse(action, false);
    }

    private void promptDebtPayment(UiSnapshot.PendingDebtView debt) {
        pendingPromptShowing = true;
        try {
            String debtId = debt.debtId();
            while (debtStillPending(debtId)) {
                UiSnapshot.PendingDebtView currentDebt = findPendingDebt(debtId).orElse(debt);
                if (!showDebtConfirmation(currentDebt)) {
                    continue;
                }
                List<UiSnapshot.CardView> paymentOptions = snapshot.currentPlayer(context.playerId())
                        .map(UiSnapshot.PlayerView::paymentCards)
                        .orElse(List.of());
                if (paymentOptions.isEmpty()) {
                    showFeedback("No bank or property cards are available to pay this debt.");
                    continue;
                }
                Optional<List<UiSnapshot.CardView>> selected = choosePaymentCards(
                        paymentOptions,
                        currentDebt.amountM());
                if (selected.isEmpty()) {
                    continue;
                }
                Map<String, Object> payload = basePayload();
                payload.put("debtId", currentDebt.debtId());
                payload.put("paymentCardIds", selected.get().stream().map(UiSnapshot.CardView::id).toList());
                submit(CommandType.PAY_DEBT, payload, "Submitted payment.");
                break;
            }
        } finally {
            pendingPromptShowing = false;
        }
        maybeShowPendingPrompts();
    }

    private boolean debtStillPending(String debtId) {
        return findPendingDebt(debtId).isPresent();
    }

    private Optional<UiSnapshot.PendingDebtView> findPendingDebt(String debtId) {
        return snapshot.pendingDebtsForDebtor(context.playerId()).stream()
                .filter(debt -> debt.debtId().equals(debtId))
                .findFirst();
    }

    private boolean showDebtConfirmation(UiSnapshot.PendingDebtView debt) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Payment Required");
        dialog.setHeaderText("You owe " + snapshot.playerName(debt.creditorPlayerId())
                + " $" + debt.amountM() + "M");
        dialog.setContentText(buildDebtPromptDetails(debt));
        ButtonType payNow = new ButtonType("Pay Now", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(payNow);
        dialog.setOnCloseRequest(event -> event.consume());
        return dialog.showAndWait().filter(payNow::equals).isPresent();
    }

    private Optional<List<UiSnapshot.CardView>> choosePaymentCards(
            List<UiSnapshot.CardView> options,
            int dueAmountM) {
        if (options == null || options.isEmpty()) {
            return Optional.empty();
        }
        int maxPayableM = options.stream().mapToInt(this::paymentValueM).sum();

        Dialog<List<UiSnapshot.CardView>> dialog = new Dialog<>();
        dialog.setTitle("Select Payment Cards ($" + dueAmountM + "M due)");
        dialog.setHeaderText("Tick one or more cards to pay. No Ctrl key needed.");
        ButtonType okButtonType = englishOkButton();
        ButtonType cancelButtonType = englishCancelButton();
        dialog.getDialogPane().getButtonTypes().setAll(okButtonType, cancelButtonType);

        VBox content = new VBox(8);
        content.setPadding(new Insets(4, 0, 0, 0));
        Label summaryLabel = new Label(formatPaymentSelectionSummary(0, dueAmountM));
        summaryLabel.setWrapText(true);
        content.getChildren().add(summaryLabel);

        VBox cardRows = new VBox(4);
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (UiSnapshot.CardView card : options) {
            CheckBox checkBox = new CheckBox(paymentChoiceLabel(card));
            checkBox.setUserData(card);
            checkBox.selectedProperty().addListener((obs, wasSelected, isSelected) ->
                    summaryLabel.setText(formatPaymentSelectionSummary(
                            selectedPaymentValueM(checkBoxes),
                            dueAmountM)));
            checkBoxes.add(checkBox);
            cardRows.getChildren().add(checkBox);
        }

        ScrollPane scrollPane = new ScrollPane(cardRows);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(280, options.size() * 32.0 + 8));
        content.getChildren().add(scrollPane);
        dialog.getDialogPane().setContent(content);
        dialog.setOnCloseRequest(event -> event.consume());

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okButtonType);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            List<UiSnapshot.CardView> selected = selectedPaymentCards(checkBoxes);
            int totalSelectedM = selected.stream().mapToInt(this::paymentValueM).sum();
            if (totalSelectedM < dueAmountM && totalSelectedM < maxPayableM) {
                event.consume();
                showFeedback("Selected cards total $" + totalSelectedM
                        + "M. Pay at least $" + dueAmountM + "M or use all available cards.");
                summaryLabel.setText(formatPaymentSelectionSummary(totalSelectedM, dueAmountM));
            }
        });

        dialog.setResultConverter(button -> {
            if (button == cancelButtonType) {
                return List.<UiSnapshot.CardView>of();
            }
            if (button != okButtonType) {
                return null;
            }
            return selectedPaymentCards(checkBoxes);
        });

        return dialog.showAndWait()
                .filter(selected -> !selected.isEmpty());
    }

    private List<UiSnapshot.CardView> selectedPaymentCards(List<CheckBox> checkBoxes) {
        return checkBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(checkBox -> (UiSnapshot.CardView) checkBox.getUserData())
                .toList();
    }

    private int selectedPaymentValueM(List<CheckBox> checkBoxes) {
        return selectedPaymentCards(checkBoxes).stream().mapToInt(this::paymentValueM).sum();
    }

    private int paymentValueM(UiSnapshot.CardView card) {
        if (card.bankValueM() > 0) {
            return card.bankValueM();
        }
        return Math.max(0, card.valueM());
    }

    private String paymentChoiceLabel(UiSnapshot.CardView card) {
        return "$" + paymentValueM(card) + "M — " + cardChoiceLabel(card);
    }

    private String formatPaymentSelectionSummary(int selectedM, int dueM) {
        return "Selected: $" + selectedM + "M / $" + dueM + "M due";
    }

    private void showTradePrompt(UiSnapshot.PendingTradeView trade) {
        pendingPromptShowing = true;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Trade Offer");
        dialog.setHeaderText(snapshot.playerName(trade.proposerPlayerId()) + " proposed a trade");
        dialog.setContentText(describeTrade(trade));

        ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE);
        ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
        dialog.getDialogPane().getButtonTypes().addAll(accept, reject);
        dialog.setOnCloseRequest(event -> event.consume());

        Optional<ButtonType> result = dialog.showAndWait();
        pendingPromptShowing = false;

        if (result.isEmpty()) {
            maybeShowPendingPrompts();
            return;
        }
        submitTradeResponse(trade, result.get() == accept);
    }

    private void submitActionResponse(UiSnapshot.PendingActionView action, boolean accepted) {
        Map<String, Object> payload = basePayload();
        payload.put("actionId", action.actionId());
        payload.put("accepted", accepted);
        if (!accepted) {
            List<UiSnapshot.CardView> justSayNoCards = snapshot.currentPlayerHand().stream()
                    .filter(card -> card.isAction("JUST_SAY_NO"))
                    .toList();
            if (justSayNoCards.isEmpty()) {
                showFeedback("You need a Just Say No card to reject this action.");
                maybeShowPendingPrompts();
                return;
            }
            if (justSayNoCards.size() == 1) {
                payload.put("justSayNoCardId", justSayNoCards.getFirst().id());
            } else {
                Optional<UiSnapshot.CardView> justSayNo = chooseOne(
                        "Just Say No Card", justSayNoCards, this::cardChoiceLabel);
                if (justSayNo.isEmpty()) {
                    maybeShowPendingPrompts();
                    return;
                }
                payload.put("justSayNoCardId", justSayNo.get().id());
            }
        }
        submit(CommandType.RESPOND_ACTION, payload, accepted ? "Accepted action." : "Played Just Say No.");
    }

    private void submitTradeResponse(UiSnapshot.PendingTradeView trade, boolean accepted) {
        Map<String, Object> payload = basePayload();
        payload.put("tradeId", trade.tradeId());
        payload.put("accepted", accepted);
        submit(CommandType.RESPOND_TRADE, payload, accepted ? "Accepted trade." : "Rejected trade.");
    }

    private String buildActionPromptDetails(UiSnapshot.PendingActionView action) {
        StringBuilder details = new StringBuilder();
        details.append(snapshot.playerName(action.actorPlayerId()))
                .append(" played ")
                .append(UiSnapshot.humanize(action.actionType()));
        if (action.amountM() > 0) {
            details.append(" for $").append(action.amountM()).append("M");
        }
        if (action.propertyColor() != null && !action.propertyColor().isBlank()) {
            details.append(" on ").append(UiSnapshot.humanize(action.propertyColor()));
        }
        if (action.targetCardId() != null && !action.targetCardId().isBlank()) {
            snapshot.card(action.targetCardId()).ifPresentOrElse(
                    card -> details.append(". Target property: ").append(card.title()),
                    () -> details.append(". Target card: ").append(action.targetCardId()));
        }
        if (action.targetPropertyRowId() != null && !action.targetPropertyRowId().isBlank()) {
            details.append(". Target set: ").append(action.targetPropertyRowId());
        }
        return details.toString();
    }

    private String buildDebtPromptDetails(UiSnapshot.PendingDebtView debt) {
        StringBuilder details = new StringBuilder();
        details.append("Source: ").append(UiSnapshot.humanize(debt.sourceAction()));
        if (debt.sourceCardId() != null && !debt.sourceCardId().isBlank()) {
            snapshot.card(debt.sourceCardId()).ifPresentOrElse(
                    card -> details.append(" (").append(card.title()).append(")"),
                    () -> details.append(" (").append(debt.sourceCardId()).append(")"));
        }
        details.append(". Select bank or property cards to pay.");
        return details.toString();
    }

    private String describeTrade(UiSnapshot.PendingTradeView trade) {
        String offered = formatCardIds(trade.offeredCardIds());
        String requested = formatCardIds(trade.requestedCardIds());
        return "Offered: " + offered + "\nRequested: " + requested;
    }

    private String formatCardIds(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return "none";
        }
        return cardIds.stream()
                .map(id -> snapshot.card(id).map(UiSnapshot.CardView::title).orElse(id))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private Optional<UiSnapshot.CardView> selectedHandCard() {
        return snapshot.currentPlayerHand().stream()
                .filter(card -> card.id().equals(selectedCardId))
                .findFirst();
    }

    private void submit(CommandType commandType, Map<String, Object> payload, String successMessage) {
        context.sendCommand(commandType, payload)
                .thenAccept(result -> {
                    if (result.accepted()) {
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
        ButtonType okButtonType = englishOkButton();
        ButtonType cancelButtonType = englishCancelButton();
        dialog.getDialogPane().getButtonTypes().setAll(okButtonType, cancelButtonType);
        ListView<Choice<UiSnapshot.CardView>> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setItems(FXCollections.observableArrayList(options.stream()
                .map(card -> new Choice<>(cardChoiceLabel(card), card))
                .toList()));
        dialog.getDialogPane().setContent(listView);
        dialog.setResultConverter(button -> {
            if (button != okButtonType) {
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

    private static ButtonType englishOkButton() {
        return new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    }

    private static ButtonType englishCancelButton() {
        return new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
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
        title.setMaxWidth(CARD_WIDTH - 14);
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);

        Label detail = new Label(cardDetail(card));
        detail.getStyleClass().add("card-detail");
        detail.setWrapText(true);
        detail.setMaxWidth(CARD_WIDTH - 14);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        detail.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);

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

    private Node activityChip(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(210);
        label.getStyleClass().addAll("zone-caption", "activity-chip");
        Tooltip.install(label, new Tooltip(text));
        return label;
    }

    private void clearTable() {
        clearZone(bankZone);
        clearZone(tableActivityZone);
        clearZone(propertyRowsZone);
        if (handCardsHBox != null) {
            handCardsHBox.getChildren().clear();
        }
        if (opponentsRow != null) {
            opponentsRow.getChildren().clear();
        }
        setLabelText(deckSummaryLabel, "0 cards");
        setLabelText(discardPileSummaryLabel, "0 cards");
    }

    private void clearZone(Pane zone) {
        if (zone != null) {
            zone.getChildren().clear();
        }
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
        int cardsToDiscard = snapshot.cardsToDiscard();
        if (cardsToDiscard > 0 && isYourTurn()) {
            selectedCardSummaryLabel.setText("Choose " + cardsToDiscard
                    + " card(s) to discard before ending your turn.");
            return;
        }
        if (isYourTurn()) {
            selectedCardSummaryLabel.setText("Select a card from your private hand, then choose Play Selected.");
            return;
        }
        String pendingHint = pendingResponseHint();
        selectedCardSummaryLabel.setText("Waiting for " + snapshot.playerName(snapshot.activePlayerId()) + "."
                + pendingHint);
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
        int cardsToDiscard = hasSnapshot ? snapshot.cardsToDiscard() : 0;

        setButtonText(playSelectedButton, hasSelection ? "Play Selected" : "Select a Card");
        setButtonText(discardSelectedButton, cardsToDiscard > 0
                ? "Discard " + cardsToDiscard + " Card" + (cardsToDiscard == 1 ? "" : "s")
                : "No Discard Needed");
        setButtonText(endTurnButton, yourTurn ? "End Turn" : "Waiting");

        setButtonDisabled(playSelectedButton, !yourTurn || !hasSelection);
        setButtonDisabled(discardSelectedButton, !yourTurn || cardsToDiscard <= 0);
        setButtonDisabled(endTurnButton, !yourTurn);
        setButtonDisabled(proposeTradeButton, !yourTurn || !canProposeTrade());
    }

    private String pendingResponseHint() {
        if (snapshot == null || snapshot.isEmpty()) {
            return "";
        }
        List<String> hints = new ArrayList<>();
        if (!snapshot.pendingActionsAwaiting(context.playerId()).isEmpty()) {
            hints.add("action response");
        }
        if (!snapshot.pendingDebtsForDebtor(context.playerId()).isEmpty()) {
            hints.add("debt payment");
        }
        if (hasPendingTradeForCurrentPlayer()) {
            hints.add(snapshot.canRespondToTrade(context.playerId())
                    ? "trade response"
                    : "trade response on your turn");
        }
        if (hints.isEmpty()) {
            return "";
        }
        return " Respond to pending " + String.join(" and ", hints) + " in the popup.";
    }

    private boolean isYourTurn() {
        return snapshot != null && snapshot.isTurnFor(context.playerId());
    }

    private boolean canProposeTrade() {
        return snapshot.canProposeTrade(context.playerId());
    }

    private boolean hasPendingTradeForCurrentPlayer() {
        return !snapshot.pendingTradesForTarget(context.playerId()).isEmpty();
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
        // HBox handles horizontal layout automatically.
        // Only manage card ordering and hover styling here.
        if (handCardsHBox == null) {
            return;
        }
        List<Node> orderedNodes = handCardIds.stream()
                .map(cardNodes::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!handCardsHBox.getChildren().equals(orderedNodes)) {
            handCardsHBox.getChildren().setAll(orderedNodes);
        }

        for (int i = 0; i < orderedNodes.size(); i++) {
            Node node = orderedNodes.get(i);
            boolean hovered = hoveredIndex != null && i == hoveredIndex;
            applyCardSize(node, false);
            toggleStyle(node, "hand-card-hovered", hovered);
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

    private String cardChoiceLabel(UiSnapshot.CardView card) {
        return card.title() + " (" + card.id() + ")";
    }

    private String typeStyle(UiSnapshot.CardView card) {
        return "deal-card-" + card.type().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String propertyLabelStyle(String color) {
        return "property-" + color.toLowerCase(Locale.ROOT).replace('_', '-');
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
            return "#6b1a1e";
        }
        return "#1a7a4a";
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
        setLabelText(statusMessageLabel, message);
    }

    private void showServerMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Platform.runLater(() -> {
            showFeedback(message);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Action Failed");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    private record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
