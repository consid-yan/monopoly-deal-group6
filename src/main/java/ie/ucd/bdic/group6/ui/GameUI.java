package ie.ucd.bdic.group6.ui;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.CardType;
import ie.ucd.bdic.group6.core.card.MoneyCard;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.card.StandardDeckFactory;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class GameUI {

    private static final DataFormat CARD_ID_FORMAT = new DataFormat("application/x-monopoly-deal-card-id");
    private static final double CARD_WIDTH = 94;
    private static final double CARD_HEIGHT = 128;
    private static final double COMPACT_CARD_WIDTH = 64;
    private static final double COMPACT_CARD_HEIGHT = 88;
    private static final double HAND_TOP_Y = 20;
    private static final double HAND_HOVER_Y = 2;
    private static final double HAND_MIN_STEP = 28;
    private static final double HAND_MAX_STEP = 52;
    private static final double HAND_HOVER_SPREAD = 30;

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
    private HBox alexHandBacks;

    @FXML
    private FlowPane alexBankZone;

    @FXML
    private FlowPane alexPropertyZone;

    @FXML
    private HBox morganHandBacks;

    @FXML
    private FlowPane morganBankZone;

    @FXML
    private FlowPane morganPropertyZone;

    @FXML
    private HBox rileyHandBacks;

    @FXML
    private FlowPane rileyBankZone;

    @FXML
    private FlowPane rileyPropertyZone;

    @FXML
    private TextArea gameLogArea;

    @FXML
    private TextArea feedbackArea;

    private final Map<String, Card> cardsById = new LinkedHashMap<>();
    private final Map<String, Node> cardNodes = new HashMap<>();
    private final List<String> handCardIds = new ArrayList<>();
    private final List<DropZone> dropZones = new ArrayList<>();
    private final Map<FlowPane, DropZone> zonesByPane = new IdentityHashMap<>();
    private final Map<PropertyColor, DropZone> propertyZones = new EnumMap<>(PropertyColor.class);

    private String selectedCardId;
    private DragOrigin activeDragOrigin;

    @FXML
    private void initialize() {
        activePlayerField.setText("You");
        turnStatusField.setText("Action 1 of 3");
        identityField.setText("Jordan");
        connectionField.setText("Connected");
        targetPlayerField.setText("Riley");

        registerDropZones();
        handPane.widthProperty().addListener((observable, oldValue, newValue) -> layoutHand(null));
        seedPrototypeTable();
        Platform.runLater(() -> layoutHand(null));

        gameLogArea.setText("Prototype table ready.\nYour private hand is stacked at the bottom.");
        feedbackArea.setText("Hover your hand to peek. Drag cards from hand to valid public table zones.");
    }

    @FXML
    private void playSelectedCard() {
        String cardId = selectedOrFirstCardId();
        if (cardId == null) {
            feedbackArea.setText("No card is available to play.");
            return;
        }

        Card card = cardsById.get(cardId);
        DropZone targetZone = defaultPlayZone(card);
        if (targetZone == null) {
            feedbackArea.setText(displayName(card) + " has no prototype play zone.");
            return;
        }

        moveCard(cardId, targetZone, "Played");
    }

    @FXML
    private void discardSelectedCard() {
        String cardId = selectedOrFirstCardId();
        if (cardId == null) {
            feedbackArea.setText("No card is available to discard.");
            return;
        }

        moveCard(cardId, zonesByPane.get(discardZone), "Discarded");
    }

    @FXML
    private void endTurn() {
        activePlayerField.setText("Alex");
        turnStatusField.setText("Waiting for next turn");
        appendLog("Turn ended. Waiting for Alex.");
        feedbackArea.setText("Prototype mode: only your own hand shows faces; opponents keep hidden card backs.");
    }

    @FXML
    private void payDebt() {
        String payment = summarizeZone(paymentZone);
        if (payment.isBlank()) {
            feedbackArea.setText("Drop money, property, or wild cards into Payment first.");
            return;
        }
        appendLog("Paid debt using " + payment + ".");
        feedbackArea.setText("Payment accepted in UI prototype mode.");
    }

    @FXML
    private void proposeTrade() {
        String offered = summarizeZone(tradeOfferZone);
        String requested = summarizeZone(tradeRequestZone);
        if (offered.isBlank() || requested.isBlank()) {
            feedbackArea.setText("Trade prototype needs cards in both Trade Offer and Trade Request.");
            return;
        }

        appendLog("Proposed trade with " + targetPlayerField.getText() + ": " + offered + " for " + requested + ".");
        feedbackArea.setText("Trade request staged in prototype mode.");
    }

    @FXML
    private void acceptTrade() {
        appendLog("Accepted the pending trade.");
        feedbackArea.setText("Trade completed in prototype mode. Move public cards manually to reflect the result.");
    }

    @FXML
    private void rejectTrade() {
        appendLog("Rejected the pending trade.");
        feedbackArea.setText("Trade rejected. Drag public cards back from trade zones if needed.");
    }

    @FXML
    private void backToLobby() {
        SceneManager.showLobby();
    }

    @FXML
    private void openHowToPlay() {
        SceneManager.showHowToPlay();
    }

    @FXML
    private void finishDemo() {
        SceneManager.showResult();
    }

    private void registerDropZones() {
        dropZones.clear();
        zonesByPane.clear();
        propertyZones.clear();

        register(bankZone, ZoneKind.BANK, "Your Bank", null, true);
        register(actionZone, ZoneKind.ACTION, "Action Cards", null, false);
        register(paymentZone, ZoneKind.PAYMENT, "Payment", null, true);
        register(tradeOfferZone, ZoneKind.TRADE, "Trade Offer", null, true);
        register(tradeRequestZone, ZoneKind.TRADE, "Trade Request", null, true);
        register(discardZone, ZoneKind.DISCARD, "Discard Pile", null, false);

        register(brownPropertyZone, ZoneKind.PROPERTY_COLOR, "Brown Set", PropertyColor.BROWN, false);
        register(lightBluePropertyZone, ZoneKind.PROPERTY_COLOR, "Light Blue Set", PropertyColor.LIGHT_BLUE, false);
        register(pinkPropertyZone, ZoneKind.PROPERTY_COLOR, "Pink Set", PropertyColor.PINK, false);
        register(orangePropertyZone, ZoneKind.PROPERTY_COLOR, "Orange Set", PropertyColor.ORANGE, false);
        register(redPropertyZone, ZoneKind.PROPERTY_COLOR, "Red Set", PropertyColor.RED, false);
        register(yellowPropertyZone, ZoneKind.PROPERTY_COLOR, "Yellow Set", PropertyColor.YELLOW, false);
        register(greenPropertyZone, ZoneKind.PROPERTY_COLOR, "Green Set", PropertyColor.GREEN, false);
        register(darkBluePropertyZone, ZoneKind.PROPERTY_COLOR, "Dark Blue Set", PropertyColor.DARK_BLUE, false);
        register(railroadPropertyZone, ZoneKind.PROPERTY_COLOR, "Railroad Set", PropertyColor.RAILROAD, false);
        register(utilityPropertyZone, ZoneKind.PROPERTY_COLOR, "Utility Set", PropertyColor.UTILITY, false);

        register(alexBankZone, ZoneKind.PUBLIC_BANK, "Alex Bank", null, true);
        register(alexPropertyZone, ZoneKind.PUBLIC_PROPERTY, "Alex Properties", null, true);
        register(morganBankZone, ZoneKind.PUBLIC_BANK, "Morgan Bank", null, true);
        register(morganPropertyZone, ZoneKind.PUBLIC_PROPERTY, "Morgan Properties", null, true);
        register(rileyBankZone, ZoneKind.PUBLIC_BANK, "Riley Bank", null, true);
        register(rileyPropertyZone, ZoneKind.PUBLIC_PROPERTY, "Riley Properties", null, true);
    }

    private void register(FlowPane pane, ZoneKind kind, String label, PropertyColor color, boolean compact) {
        DropZone zone = new DropZone(pane, kind, label, color, compact);
        dropZones.add(zone);
        zonesByPane.put(pane, zone);
        if (color != null) {
            propertyZones.put(color, zone);
        }
        configureDropZone(zone);
    }

    private void configureDropZone(DropZone zone) {
        zone.pane.setOnDragOver(event -> {
            String cardId = draggedCardId(event.getDragboard());
            if (canAccept(zone, cardsById.get(cardId))) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        zone.pane.setOnDragEntered(event -> {
            String cardId = draggedCardId(event.getDragboard());
            Card card = cardsById.get(cardId);
            if (card != null) {
                boolean accepted = canAccept(zone, card);
                toggleStyle(zone.pane, "drop-zone-valid", accepted);
                toggleStyle(zone.pane, "drop-zone-invalid", !accepted);
            }
            event.consume();
        });

        zone.pane.setOnDragExited(event -> {
            clearDropHighlight(zone.pane);
            event.consume();
        });

        zone.pane.setOnDragDropped(event -> {
            String cardId = draggedCardId(event.getDragboard());
            Card card = cardsById.get(cardId);
            boolean accepted = canAccept(zone, card);
            if (accepted) {
                moveCard(cardId, zone, "Moved");
            } else if (card != null) {
                returnToOrigin(activeDragOrigin);
                feedbackArea.setText(displayName(card) + " cannot be placed in " + zone.label + ".");
            }
            clearAllDropHighlights();
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void seedPrototypeTable() {
        dropZones.forEach(zone -> zone.pane.getChildren().clear());
        handPane.getChildren().clear();
        cardsById.clear();
        cardNodes.clear();
        handCardIds.clear();

        List<Card> deck = StandardDeckFactory.createUnshuffledDeck();
        Set<String> used = new HashSet<>();

        addToHand(pick(deck, used, money(3), "3M money"));
        addToHand(pick(deck, used, money(5), "5M money"));
        addToHand(pick(deck, used, property(PropertyColor.RED), "red property"));
        addToHand(pick(deck, used, property(PropertyColor.DARK_BLUE), "dark blue property"));
        addToHand(pick(deck, used, wild(PropertyColor.RED, PropertyColor.YELLOW), "red/yellow wild"));
        addToHand(pick(deck, used, action(ActionType.DEAL_BREAKER), "Deal Breaker"));
        addToHand(pick(deck, used, action(ActionType.RENT_DARK_BLUE_OR_GREEN), "dark blue/green rent"));

        placeOnTable(pick(deck, used, money(1), "1M money"), zonesByPane.get(bankZone));
        placeOnTable(pick(deck, used, money(2), "2M money"), zonesByPane.get(bankZone));
        placeOnTable(pick(deck, used, action(ActionType.PASS_GO), "Pass Go"), zonesByPane.get(bankZone));

        placeOnTable(pick(deck, used, property(PropertyColor.BROWN), "brown property"), propertyZones.get(PropertyColor.BROWN));
        placeOnTable(pick(deck, used, property(PropertyColor.LIGHT_BLUE), "light blue property"), propertyZones.get(PropertyColor.LIGHT_BLUE));
        placeOnTable(pick(deck, used, property(PropertyColor.GREEN), "green property"), propertyZones.get(PropertyColor.GREEN));

        placeOnTable(pick(deck, used, action(ActionType.JUST_SAY_NO), "Just Say No"), zonesByPane.get(discardZone));
        placeOnTable(pick(deck, used, money(4), "4M money"), zonesByPane.get(paymentZone));
        placeOnTable(pick(deck, used, property(PropertyColor.PINK), "pink property"), zonesByPane.get(tradeOfferZone));
        placeOnTable(pick(deck, used, money(10), "10M money"), zonesByPane.get(tradeRequestZone));

        placeOnTable(pick(deck, used, money(5), "Alex bank card"), zonesByPane.get(alexBankZone));
        placeOnTable(pick(deck, used, property(PropertyColor.DARK_BLUE), "Alex property"), zonesByPane.get(alexPropertyZone));
        placeOnTable(pick(deck, used, money(2), "Morgan bank card"), zonesByPane.get(morganBankZone));
        placeOnTable(pick(deck, used, property(PropertyColor.RAILROAD), "Morgan property"), zonesByPane.get(morganPropertyZone));
        placeOnTable(pick(deck, used, money(4), "Riley bank card"), zonesByPane.get(rileyBankZone));
        placeOnTable(pick(deck, used, property(PropertyColor.YELLOW), "Riley property"), zonesByPane.get(rileyPropertyZone));

        populateOpponentHandBacks(alexHandBacks, 5);
        populateOpponentHandBacks(morganHandBacks, 4);
        populateOpponentHandBacks(rileyHandBacks, 6);
        layoutHand(null);
    }

    private void addToHand(Card card) {
        cardsById.put(card.id(), card);
        Node node = createCardNode(card);
        cardNodes.put(card.id(), node);
        handCardIds.add(card.id());
        applyCardSize(node, false);
        node.setManaged(false);
        handPane.getChildren().add(node);
    }

    private void placeOnTable(Card card, DropZone zone) {
        cardsById.put(card.id(), card);
        Node node = createCardNode(card);
        cardNodes.put(card.id(), node);
        applyCardSize(node, zone.compact);
        zone.pane.getChildren().add(node);
    }

    private Node createCardNode(Card card) {
        VBox cardBox = new VBox(3);
        cardBox.setAlignment(Pos.TOP_CENTER);
        cardBox.setUserData(card.id());
        cardBox.getStyleClass().addAll("deal-card", typeStyle(card));

        Region colorBand = new Region();
        colorBand.getStyleClass().add("card-color-band");
        colorBand.setStyle("-fx-background-color: " + colorBandCss(card) + ";");

        HBox header = new HBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label(typeLabel(card));
        typeLabel.getStyleClass().add("card-type-label");
        HBox.setHgrow(typeLabel, Priority.ALWAYS);
        Label valueBadge = new Label(valueBadge(card));
        valueBadge.getStyleClass().add("card-value-badge");
        header.getChildren().addAll(typeLabel, valueBadge);

        StackPane art = new StackPane();
        art.getStyleClass().add("card-art-placeholder");
        Label artText = new Label("AI\nART");
        artText.getStyleClass().add("card-art-text");
        art.getChildren().add(artText);

        Label title = new Label(displayName(card));
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label detail = new Label(cardDetail(card));
        detail.getStyleClass().add("card-detail");
        detail.setWrapText(true);

        cardBox.getChildren().addAll(colorBand, header, art, title, detail);
        configureCardInteraction(cardBox, card);
        return cardBox;
    }

    private void configureCardInteraction(Node cardNode, Card card) {
        cardNode.setOnMouseClicked(event -> {
            selectCard(card.id());
            feedbackArea.setText("Selected " + displayName(card) + ".");
            event.consume();
        });

        cardNode.setOnMouseEntered(event -> {
            int handIndex = handCardIds.indexOf(card.id());
            if (handIndex >= 0) {
                layoutHand(handIndex);
            }
            event.consume();
        });

        cardNode.setOnMouseExited(event -> {
            if (handCardIds.contains(card.id()) && activeDragOrigin == null) {
                layoutHand(null);
            }
            event.consume();
        });

        cardNode.setOnDragDetected(event -> {
            selectCard(card.id());
            activeDragOrigin = originFor(card.id());
            toggleStyle(cardNode, "deal-card-dragging", true);

            Dragboard dragboard = cardNode.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(CARD_ID_FORMAT, card.id());
            content.putString(displayName(card));
            dragboard.setContent(content);
            dragboard.setDragView(cardNode.snapshot(new SnapshotParameters(), null));
            event.consume();
        });

        cardNode.setOnDragDone(event -> {
            toggleStyle(cardNode, "deal-card-dragging", false);
            if (event.getTransferMode() == null) {
                returnToOrigin(activeDragOrigin);
                if (activeDragOrigin != null) {
                    Card originCard = cardsById.get(activeDragOrigin.cardId());
                    feedbackArea.setText(displayName(originCard) + " returned to its original position.");
                }
            }
            activeDragOrigin = null;
            clearAllDropHighlights();
            layoutHand(null);
            event.consume();
        });
    }

    private void moveCard(String cardId, DropZone targetZone, String verb) {
        Node node = cardNodes.get(cardId);
        Card card = cardsById.get(cardId);
        if (node == null || card == null || targetZone == null) {
            return;
        }

        removeFromCurrentParent(cardId, node);
        applyCardSize(node, targetZone.compact);
        node.setManaged(true);
        node.setLayoutX(0);
        node.setLayoutY(0);
        targetZone.pane.getChildren().add(node);
        selectCard(cardId);
        layoutHand(null);

        appendLog(verb + " " + displayName(card) + " to " + targetZone.label + ".");
        feedbackArea.setText(displayName(card) + " is now public in " + targetZone.label + ".");
    }

    private void removeFromCurrentParent(String cardId, Node node) {
        handCardIds.remove(cardId);
        if (node.getParent() instanceof Pane parentPane) {
            parentPane.getChildren().remove(node);
        }
    }

    private DragOrigin originFor(String cardId) {
        int handIndex = handCardIds.indexOf(cardId);
        if (handIndex >= 0) {
            return new DragOrigin(cardId, null, handIndex);
        }

        Node node = cardNodes.get(cardId);
        if (node != null && node.getParent() instanceof FlowPane flowPane) {
            return new DragOrigin(cardId, zonesByPane.get(flowPane), -1);
        }
        return new DragOrigin(cardId, null, -1);
    }

    private void returnToOrigin(DragOrigin origin) {
        if (origin == null) {
            return;
        }

        Node node = cardNodes.get(origin.cardId());
        if (node == null) {
            return;
        }

        removeFromCurrentParent(origin.cardId(), node);
        if (origin.fromHand()) {
            int index = Math.max(0, Math.min(origin.handIndex(), handCardIds.size()));
            handCardIds.add(index, origin.cardId());
            applyCardSize(node, false);
            node.setManaged(false);
            handPane.getChildren().add(node);
            layoutHand(null);
            return;
        }

        if (origin.sourceZone() != null) {
            applyCardSize(node, origin.sourceZone().compact());
            node.setManaged(true);
            origin.sourceZone().pane().getChildren().add(node);
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
        step = Math.max(HAND_MIN_STEP, Math.min(HAND_MAX_STEP, step));

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

    private boolean canAccept(DropZone zone, Card card) {
        if (zone == null || card == null) {
            return false;
        }

        return switch (zone.kind) {
            case DISCARD -> true;
            case BANK, PUBLIC_BANK -> card.bankValueM().isPresent();
            case ACTION -> card.type() == CardType.ACTION;
            case PAYMENT, TRADE -> card.type() == CardType.MONEY
                    || card.type() == CardType.PROPERTY
                    || card.type() == CardType.PROPERTY_WILD;
            case PUBLIC_PROPERTY -> card.type() == CardType.PROPERTY || card.type() == CardType.PROPERTY_WILD;
            case PROPERTY_COLOR -> acceptsPropertyColor(zone.color, card);
        };
    }

    private boolean acceptsPropertyColor(PropertyColor color, Card card) {
        if (card instanceof PropertyCard propertyCard) {
            return propertyCard.color() == color;
        }
        if (card instanceof PropertyWildCard wildCard) {
            return wildCard.allows(color);
        }
        return false;
    }

    private DropZone defaultPlayZone(Card card) {
        if (card instanceof PropertyCard propertyCard) {
            return propertyZones.get(propertyCard.color());
        }
        if (card instanceof PropertyWildCard wildCard) {
            return propertyZones.get(wildCard.allowedColors().iterator().next());
        }
        if (card.type() == CardType.ACTION) {
            return zonesByPane.get(actionZone);
        }
        if (card.bankValueM().isPresent()) {
            return zonesByPane.get(bankZone);
        }
        return null;
    }

    private String selectedOrFirstCardId() {
        if (selectedCardId != null && cardNodes.containsKey(selectedCardId)) {
            return selectedCardId;
        }
        if (handCardIds.isEmpty()) {
            return null;
        }
        selectCard(handCardIds.getFirst());
        return handCardIds.getFirst();
    }

    private void selectCard(String cardId) {
        selectedCardId = cardId;
        cardNodes.forEach((id, node) -> toggleStyle(node, "deal-card-selected", id.equals(cardId)));
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

    private String draggedCardId(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasContent(CARD_ID_FORMAT)) {
            return null;
        }
        Object value = dragboard.getContent(CARD_ID_FORMAT);
        return value instanceof String cardId ? cardId : null;
    }

    private void clearAllDropHighlights() {
        dropZones.forEach(zone -> clearDropHighlight(zone.pane));
    }

    private void clearDropHighlight(Node node) {
        toggleStyle(node, "drop-zone-valid", false);
        toggleStyle(node, "drop-zone-invalid", false);
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

    private String summarizeZone(FlowPane zone) {
        return zone.getChildren().stream()
                .map(Node::getUserData)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(cardsById::get)
                .map(this::displayName)
                .collect(Collectors.joining(", "));
    }

    private Card pick(List<Card> deck, Set<String> used, Predicate<Card> predicate, String description) {
        for (Card card : deck) {
            if (!used.contains(card.id()) && predicate.test(card)) {
                used.add(card.id());
                return card;
            }
        }
        throw new IllegalStateException("Prototype deck is missing " + description + ".");
    }

    private Predicate<Card> money(int valueM) {
        return card -> card instanceof MoneyCard moneyCard && moneyCard.valueM() == valueM;
    }

    private Predicate<Card> property(PropertyColor color) {
        return card -> card instanceof PropertyCard propertyCard && propertyCard.color() == color;
    }

    private Predicate<Card> wild(PropertyColor firstColor, PropertyColor secondColor) {
        return card -> card instanceof PropertyWildCard wildCard
                && wildCard.allowedColors().contains(firstColor)
                && wildCard.allowedColors().contains(secondColor);
    }

    private Predicate<Card> action(ActionType actionType) {
        return card -> card instanceof ActionCard actionCard && actionCard.actionType() == actionType;
    }

    private String displayName(Card card) {
        if (card == null) {
            return "Card";
        }
        if (card instanceof MoneyCard moneyCard) {
            return "$" + moneyCard.valueM() + "M";
        }
        return card.displayName();
    }

    private String cardDetail(Card card) {
        if (card instanceof PropertyCard propertyCard) {
            return colorLabel(propertyCard.color()) + " property set";
        }
        if (card instanceof PropertyWildCard wildCard) {
            return "Wild: " + formatColors(wildCard.allowedColors());
        }
        if (card instanceof ActionCard actionCard) {
            return actionCard.actionType().name().replace('_', ' ');
        }
        return "Money card";
    }

    private String typeLabel(Card card) {
        return switch (card.type()) {
            case MONEY -> "MONEY";
            case PROPERTY -> "PROPERTY";
            case PROPERTY_WILD -> "WILD";
            case ACTION -> "ACTION";
        };
    }

    private String valueBadge(Card card) {
        OptionalInt bankValue = card.bankValueM();
        if (bankValue.isPresent()) {
            return "$" + bankValue.getAsInt() + "M";
        }
        if (card instanceof PropertyCard propertyCard) {
            return propertyCard.requiredToComplete() + " SET";
        }
        return "ANY";
    }

    private String typeStyle(Card card) {
        return "deal-card-" + card.type().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String colorBandCss(Card card) {
        if (card instanceof PropertyCard propertyCard) {
            return propertyCss(propertyCard.color());
        }
        if (card instanceof PropertyWildCard wildCard) {
            List<String> colors = wildCard.allowedColors().stream().map(this::propertyCss).toList();
            if (colors.size() == 1) {
                return colors.getFirst();
            }
            if (colors.size() == PropertyColor.values().length) {
                return "linear-gradient(to right, #8b5a2b, #8ad8ff, #d94ca8, #f28a2e, #d83240, #f2d64b, #248f51, #244a96)";
            }
            return "linear-gradient(to right, " + colors.get(0) + " 0%, " + colors.get(0) + " 50%, "
                    + colors.get(1) + " 50%, " + colors.get(1) + " 100%)";
        }
        if (card.type() == CardType.ACTION) {
            return "#a61d24";
        }
        return "#23845b";
    }

    private String propertyCss(PropertyColor color) {
        return switch (color) {
            case BROWN -> "#8b5a2b";
            case LIGHT_BLUE -> "#8ad8ff";
            case PINK -> "#d94ca8";
            case ORANGE -> "#f28a2e";
            case RED -> "#d83240";
            case YELLOW -> "#f2d64b";
            case GREEN -> "#248f51";
            case DARK_BLUE -> "#244a96";
            case RAILROAD -> "#d8dde3";
            case UTILITY -> "#98a2ad";
        };
    }

    private String formatColors(Set<PropertyColor> colors) {
        if (colors.size() == PropertyColor.values().length) {
            return "Any color";
        }
        return colors.stream().map(this::colorLabel).collect(Collectors.joining(" / "));
    }

    private String colorLabel(PropertyColor color) {
        return switch (color) {
            case BROWN -> "Brown";
            case LIGHT_BLUE -> "Light Blue";
            case PINK -> "Pink";
            case ORANGE -> "Orange";
            case RED -> "Red";
            case YELLOW -> "Yellow";
            case GREEN -> "Green";
            case DARK_BLUE -> "Dark Blue";
            case RAILROAD -> "Railroad";
            case UTILITY -> "Utility";
        };
    }

    private void appendLog(String message) {
        if (gameLogArea.getText().isBlank()) {
            gameLogArea.setText(message);
            return;
        }
        gameLogArea.appendText("\n" + message);
    }

    private enum ZoneKind {
        BANK,
        PROPERTY_COLOR,
        ACTION,
        PAYMENT,
        TRADE,
        DISCARD,
        PUBLIC_BANK,
        PUBLIC_PROPERTY
    }

    private record DropZone(FlowPane pane, ZoneKind kind, String label, PropertyColor color, boolean compact) {
    }

    private record DragOrigin(String cardId, DropZone sourceZone, int handIndex) {

        private boolean fromHand() {
            return handIndex >= 0;
        }
    }
}
