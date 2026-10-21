package ie.ucd.bdic.group6.core.engine;

import ie.ucd.bdic.group6.command.card.PlayCardCommand;
import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.card.PropertyCard;
import ie.ucd.bdic.group6.core.card.PropertyWildCard;
import ie.ucd.bdic.group6.core.common.OperationError;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.engine.pending.PendingAction;
import ie.ucd.bdic.group6.core.engine.pending.PendingDebt;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.player.PropertyTransferService;
import ie.ucd.bdic.group6.core.player.PlayerRollbackSnapshot;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.rules.PropertyRentRule;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.core.rules.ActionRule;
import ie.ucd.bdic.group6.exception.GameException;
import ie.ucd.bdic.group6.exception.InvalidCommandException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles card play, action execution, and pending-action resolution during active gameplay.
 */
public final class GameplayHandler {

    public interface Host {
        Player requirePlayer(GameSession session, String playerId);

        void requireNotDefeated(GameSession session, String playerId);

        Card requireCardInHand(Player player, String cardId);

        String requireText(String value, String name);

        PropertyColor parsePropertyColor(String raw);

        void requireSuccess(OperationResult<?> result);

        void drawCards(Player player, ActiveGameState activeGame, int count);
    }

    public enum PlayMode {
        BANK,
        PROPERTY,
        ACTION
    }

    public record PropertyCardLocation(Card card, PropertySet row) {
    }

    private final ActionRule actionRule;
    private final Host host;

    public GameplayHandler(ActionRule actionRule, Host host) {
        this.actionRule = Objects.requireNonNull(actionRule, "actionRule");
        this.host = Objects.requireNonNull(host, "host");
    }

    public static boolean isRentAction(ActionType actionType) {
        return switch (actionType) {
            case RENT_BROWN_OR_LIGHT_BLUE,
                    RENT_PINK_OR_ORANGE,
                    RENT_RED_OR_YELLOW,
                    RENT_DARK_BLUE_OR_GREEN,
                    RENT_RAILROAD_OR_UTILITY,
                    WILD_RENT -> true;
            default -> false;
        };
    }

    public PlayMode resolvePlayMode(PlayCardCommand command, Card card) {
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
            throw new InvalidCommandException("INVALID_PLAY_MODE", "Unsupported play mode: " + rawPlayMode, e);
        }
    }

    /**
     * [Gameplay Step 2a-BANK: Bank a Money Card]
     * Moves a money card from the player's hand into their bank.
     * Banked cards can later be used to pay rent, debts, or as trade currency.
     * Costs 1 action point.
     */
    public void playBankCard(Player player, Card card) {
        host.requireSuccess(player.tryBankFromHand(card));
    }

    /**
     * [Gameplay Step 2a-PROPERTY: Play a Property Card]
     * Places a property or wild card from hand onto the player's property board.
     * Can add to an existing row of the same color, or start a new row.
     * Wild cards must specify which color they are being played as.
     * Does NOT cost an action when played as the first card of a new row via wild card.
     * Costs 1 action point.
     */
    public void playPropertyCard(PlayCardCommand command, Player player, Card card) {
        if (!(card instanceof PropertyCard) && !(card instanceof PropertyWildCard)) {
            throw new InvalidCommandException("INVALID_PLAY_MODE", "Only property cards can be played as properties");
        }

        PropertyColor anchorColor = resolvePropertyColor(command, card);
        PropertyColor wildChoice = card instanceof PropertyWildCard ? anchorColor : null;

        Optional<PropertySet> selectedRow = resolveOwnedPropertyRow(command, player);
        if (selectedRow.isPresent()) {
            PropertySet row = selectedRow.get();
            if (!row.canAdd(card, wildChoice)) {
                throw new GameException(
                        "RECEIVER_ROW_REJECTS_CARD",
                        "The selected property row rejects this card");
            }
            host.requireSuccess(player.tryPlayPropertyFromHand(row, card, wildChoice));
            return;
        }

        host.requireSuccess(player.tryStartNewPropertyRowFromHand(anchorColor, card, wildChoice));
    }

    /**
     * [Gameplay Step 2a-ACTION: Play an Action Card]
     * Uses an action card from the player's hand. Dispatches to the specific handler:
     *   PASS_GO       → draw 2 extra cards (card goes to discard)
     *   DEBT_COLLECTOR → target one player to pay 5M
     *   ITS_MY_BIRTHDAY → every other player pays 2M
     *   RENT (6 dual + WILD) → charge rent based on owned property sets
     *   HOUSE / HOTEL → improve a complete set to increase rent value
     *   SLY_DEAL      → steal one property card from an incomplete set
     *   FORCED_DEAL    → swap one of your properties for one of theirs
     *   DEAL_BREAKER   → steal an entire complete property set
     * Costs 1 action point (2 if paired with Double The Rent on a rent card).
     * Most actions create a PendingAction so targets can respond with Just Say No.
     */
    public void playActionCard(
            PlayCardCommand command,
            GameSession session,
            Player player,
            Card card,
            ActiveGameState activeGame) {
        if (!(card instanceof ActionCard actionCard)) {
            throw new InvalidCommandException("INVALID_PLAY_MODE", "Only action cards can be played as actions");
        }

        switch (actionCard.actionType()) {
            case PASS_GO -> {
                removeActionFromHand(player, actionCard);
                host.drawCards(player, activeGame, 2);
                activeGame.discard(actionCard);
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
            case SLY_DEAL -> executeSlyDeal(command, session, player, actionCard, activeGame);
            case FORCED_DEAL -> executeForcedDeal(command, session, player, actionCard, activeGame);
            case DEAL_BREAKER -> executeDealBreaker(command, session, player, actionCard, activeGame);
            case JUST_SAY_NO, DOUBLE_THE_RENT ->
                    actionRule.validatePlayableAsAction(actionCard.actionType());
        }
    }

    /**
     * [Gameplay Step 3-JSN: Spend Just Say No]
     * A player uses a Just Say No card from their hand to reject a pending action targeting them.
     * The Just Say No card is discarded. If all targets have responded, the action is resolved.
     */
    public void spendJustSayNo(
            GameSession session,
            ActiveGameState activeGame,
            String responderId,
            String justSayNoCardId) {
        Player responder = host.requirePlayer(session, responderId);
        Card card = host.requireCardInHand(responder, host.requireText(justSayNoCardId, "justSayNoCardId"));
        if (!(card instanceof ActionCard actionCard) || actionCard.actionType() != ActionType.JUST_SAY_NO) {
            throw new GameException("JUST_SAY_NO_REQUIRED", "A Just Say No card is required to reject the action");
        }
        discardActionFromHand(responder, actionCard, activeGame);
    }

    /**
     * [Gameplay Step 3-APPLY: Apply Resolved Action]
     * After all targets have responded to a pending action (accepted or rejected via Just Say No),
     * this method applies the actual effect:
     *   Money actions → create PendingDebt entries for each accepting target
     *   SLY_DEAL      → transfer the stolen property card
     *   FORCED_DEAL   → swap the two property cards atomically
     *   DEAL_BREAKER  → transfer the entire complete set
     */
    public void applyResolvedPendingAction(
            GameSession session,
            ActiveGameState activeGame,
            PendingAction pendingAction) {
        List<String> acceptedTargets = pendingAction.acceptedTargetIds().stream()
                .filter(playerId -> !session.isDefeated(playerId))
                .toList();
        if (acceptedTargets.isEmpty()) {
            return;
        }

        switch (pendingAction.actionType()) {
            case DEBT_COLLECTOR, ITS_MY_BIRTHDAY,
                    RENT_BROWN_OR_LIGHT_BLUE,
                    RENT_PINK_OR_ORANGE,
                    RENT_RED_OR_YELLOW,
                    RENT_DARK_BLUE_OR_GREEN,
                    RENT_RAILROAD_OR_UTILITY,
                    WILD_RENT -> createDebtsForAcceptedTargets(activeGame, pendingAction, acceptedTargets);
            case SLY_DEAL -> applySlyDeal(session, pendingAction, acceptedTargets.getFirst());
            case FORCED_DEAL -> applyForcedDeal(session, pendingAction, acceptedTargets.getFirst());
            case DEAL_BREAKER -> applyDealBreaker(session, pendingAction, acceptedTargets.getFirst());
            default -> throw new GameException(
                    "UNSUPPORTED_PENDING_ACTION",
                    "Cannot resolve pending action " + pendingAction.actionType());
        }
    }

    public OperationResult<Void> movePropertyCardToBestRow(
            Player sender,
            Player receiver,
            Card card,
            PropertySet sourceRow) {
        PropertyPlacement placement = placementForTransferredProperty(card, sourceRow);
        OperationResult<PropertySet> moved = PropertyTransferService.transferPropertyCardToNewRow(
                sender,
                receiver,
                card,
                placement.anchorColor(),
                placement.wildChoice());
        if (!moved.isSuccess()) {
            return OperationResult.fail(
                    moved.errorCode().orElse(OperationError.INTERNAL_ERROR),
                    moved.errorMessage().orElse(""));
        }
        return OperationResult.ok();
    }

    public Optional<PropertyCardLocation> findPropertyCardLocation(Player player, String cardId) {
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

    private void executeDebtCollector(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = host.requireText(command.getTargetPlayerId(), "targetPlayerId");
        actionRule.validateDebtCollector(player, targetPlayerId);
        host.requirePlayer(session, targetPlayerId);
        host.requireNotDefeated(session, targetPlayerId);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .amountM(5)
                .targetPlayerIds(List.of(targetPlayerId))
                .build());
    }

    private void executeItsMyBirthday(
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        List<String> debtorIds = activeGame.turnManager().turnOrder().stream()
                .filter(playerId -> !playerId.equals(player.id()))
                .filter(playerId -> !session.isDefeated(playerId))
                .toList();
        debtorIds.forEach(playerId -> host.requirePlayer(session, playerId));
        actionRule.validateItsMyBirthday(debtorIds);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .amountM(2)
                .targetPlayerIds(debtorIds)
                .build());
    }

    private void executeRentCard(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        PropertyColor rentColor = resolveRentColor(command, player, actionCard);
        int rentM = PropertyRentRule.maxRentForAnchor(player.propertyBoard(), rentColor);
        actionRule.validateRentAmount(rentColor, rentM);
        ActionCard doubleRent = resolveDoubleRentCard(command, player);
        if (doubleRent != null) {
            rentM *= 2;
        }
        List<Player> targets = resolveRentTargets(command, session, player, actionCard, activeGame);
        actionRule.validateRentTargets(targets);
        discardActionFromHand(player, actionCard, activeGame);
        if (doubleRent != null) {
            discardActionFromHand(player, doubleRent, activeGame);
        }
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .doubleRentCardId(doubleRent == null ? null : doubleRent.id())
                .propertyColor(rentColor)
                .amountM(rentM)
                .targetPlayerIds(targets.stream().map(Player::id).toList())
                .build());
    }

    private void executeSlyDeal(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = host.requireText(command.getTargetPlayerId(), "targetPlayerId");
        Player target = host.requirePlayer(session, targetPlayerId);
        host.requireNotDefeated(session, targetPlayerId);
        String targetCardId = host.requireText(command.getTargetCardId(), "targetCardId");
        Card targetCard = findPropertyCardLocation(target, targetCardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Target property card not found"))
                .card();
        actionRule.validateSlyDeal(player, target, targetCard);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .targetCardId(targetCardId)
                .targetPlayerIds(List.of(targetPlayerId))
                .build());
    }

    private void executeForcedDeal(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = host.requireText(command.getTargetPlayerId(), "targetPlayerId");
        Player target = host.requirePlayer(session, targetPlayerId);
        host.requireNotDefeated(session, targetPlayerId);
        String offeredCardId = host.requireText(command.getOfferedCardId(), "offeredCardId");
        String requestedCardId = host.requireText(command.getRequestedCardId(), "requestedCardId");
        Card offered = findPropertyCardLocation(player, offeredCardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Offered property card not found"))
                .card();
        Card requested = findPropertyCardLocation(target, requestedCardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Requested property card not found"))
                .card();
        actionRule.validateForcedDeal(player, target, offered, requested);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .offeredCardId(offeredCardId)
                .requestedCardId(requestedCardId)
                .targetPlayerIds(List.of(targetPlayerId))
                .build());
    }

    private void executeDealBreaker(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = host.requireText(command.getTargetPlayerId(), "targetPlayerId");
        Player target = host.requirePlayer(session, targetPlayerId);
        host.requireNotDefeated(session, targetPlayerId);
        String rowId = host.requireText(command.getTargetPropertyRowId(), "targetPropertyRowId");
        PropertySet row = target.findPropertyRow(rowId)
                .orElseThrow(() -> new GameException("PROPERTY_ROW_NOT_FOUND", "Target property row not found"));
        actionRule.validateDealBreaker(player, target, row);
        discardActionFromHand(player, actionCard, activeGame);
        activeGame.addPendingAction(pendingActionFor(player, actionCard)
                .targetPropertyRowId(rowId)
                .targetPlayerIds(List.of(targetPlayerId))
                .build());
    }

    private PendingAction.Builder pendingActionFor(Player player, ActionCard actionCard) {
        return PendingAction.builder()
                .actorPlayerId(player.id())
                .actionType(actionCard.actionType())
                .actionCardId(actionCard.id());
    }

    private void executeImprovementCard(
            PlayCardCommand command,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        PropertySet targetRow = resolveImprovementRow(command, player);
        actionRule.validateImprovementTarget(targetRow, actionCard.actionType());
        OperationResult<ActionCard> spent = actionCard.actionType() == ActionType.HOUSE
                ? player.trySpendHouseCardOnRow(actionCard, targetRow)
                : player.trySpendHotelCardOnRow(actionCard, targetRow);
        host.requireSuccess(spent);
        activeGame.discard(spent.value().orElse(actionCard));
    }

    private void createDebtsForAcceptedTargets(
            ActiveGameState activeGame,
            PendingAction pendingAction,
            List<String> targetIds) {
        for (String debtorId : targetIds) {
            activeGame.addPendingDebt(PendingDebt.builder()
                    .creditorPlayerId(pendingAction.actorPlayerId())
                    .debtorPlayerId(debtorId)
                    .amountM(pendingAction.amountM())
                    .sourceAction(pendingAction.actionType())
                    .sourceCardId(pendingAction.actionCardId())
                    .build());
        }
    }

    private void applySlyDeal(GameSession session, PendingAction pendingAction, String targetPlayerId) {
        Player actor = host.requirePlayer(session, pendingAction.actorPlayerId());
        Player target = host.requirePlayer(session, targetPlayerId);
        String cardId = pendingAction.targetCardId().orElseThrow();
        PropertyCardLocation targetCard = findPropertyCardLocation(target, cardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Target property card not found"));
        if (targetCard.row().isComplete()) {
            throw new GameException("CANNOT_STEAL_FROM_COMPLETE_SET", "Sly Deal cannot steal from a complete set");
        }
        host.requireSuccess(movePropertyCardToBestRow(target, actor, targetCard.card(), targetCard.row()));
    }

    private void applyForcedDeal(GameSession session, PendingAction pendingAction, String targetPlayerId) {
        Player actor = host.requirePlayer(session, pendingAction.actorPlayerId());
        Player target = host.requirePlayer(session, targetPlayerId);
        String offeredCardId = pendingAction.offeredCardId().orElseThrow();
        String requestedCardId = pendingAction.requestedCardId().orElseThrow();
        PropertyCardLocation offered = findPropertyCardLocation(actor, offeredCardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Offered property card not found"));
        PropertyCardLocation requested = findPropertyCardLocation(target, requestedCardId)
                .orElseThrow(() -> new GameException("CARD_NOT_TRADEABLE", "Requested property card not found"));
        if (offered.row().isComplete() || requested.row().isComplete()) {
            throw new GameException("CANNOT_STEAL_FROM_COMPLETE_SET", "Forced Deal cannot swap cards from complete sets");
        }

        PlayerRollbackSnapshot actorSnapshot = actor.snapshot();
        PlayerRollbackSnapshot targetSnapshot = target.snapshot();
        try {
            host.requireSuccess(movePropertyCardToBestRow(actor, target, offered.card(), offered.row()));
            host.requireSuccess(movePropertyCardToBestRow(target, actor, requested.card(), requested.row()));
        } catch (RuntimeException e) {
            actor.restore(actorSnapshot);
            target.restore(targetSnapshot);
            throw e;
        }
    }

    private void applyDealBreaker(GameSession session, PendingAction pendingAction, String targetPlayerId) {
        Player actor = host.requirePlayer(session, pendingAction.actorPlayerId());
        Player target = host.requirePlayer(session, targetPlayerId);
        String rowId = pendingAction.targetPropertyRowId().orElseThrow();
        PropertySet row = target.findPropertyRow(rowId)
                .orElseThrow(() -> new GameException("PROPERTY_ROW_NOT_FOUND", "Target property row not found"));
        if (!row.isComplete()) {
            throw new GameException("NO_COMPLETE_SET", "Deal Breaker requires a complete target set");
        }
        host.requireSuccess(PropertyTransferService.transferEntireRow(target, actor, row));
    }

    private ActionCard resolveDoubleRentCard(PlayCardCommand command, Player player) {
        String doubleRentCardId = command.getDoubleRentCardId();
        if (doubleRentCardId == null || doubleRentCardId.isBlank()) {
            return null;
        }
        Card card = host.requireCardInHand(player, doubleRentCardId);
        actionRule.validateDoubleRentCard(card);
        return (ActionCard) card;
    }

    private PropertyColor resolveRentColor(PlayCardCommand command, Player player, ActionCard actionCard) {
        String rawColor = command.getPropertyColor();
        if (rawColor != null && !rawColor.isBlank()) {
            PropertyColor color = host.parsePropertyColor(rawColor);
            actionRule.validateRentColorForCard(actionCard, color);
            return color;
        }

        List<PropertyColor> availableColors = PropertyRentRule.rentableAnchors(player.propertyBoard()).stream()
                .filter(color -> actionCard.actionType() == ActionType.WILD_RENT
                        || actionCard.rentColorOptions().contains(color))
                .toList();
        actionRule.validateInferableRentColors(availableColors);
        return availableColors.getFirst();
    }

    private List<Player> resolveRentTargets(
            PlayCardCommand command,
            GameSession session,
            Player player,
            ActionCard actionCard,
            ActiveGameState activeGame) {
        String targetPlayerId = command.getTargetPlayerId();
        if (actionCard.actionType() == ActionType.WILD_RENT) {
            targetPlayerId = host.requireText(targetPlayerId, "targetPlayerId");
            actionRule.validateWildRentTarget(player, targetPlayerId);
            host.requireNotDefeated(session, targetPlayerId);
            return List.of(host.requirePlayer(session, targetPlayerId));
        }
        return activeGame.turnManager().turnOrder().stream()
                .filter(playerId -> !playerId.equals(player.id()))
                .filter(playerId -> !session.isDefeated(playerId))
                .map(playerId -> host.requirePlayer(session, playerId))
                .toList();
    }

    private PropertySet resolveImprovementRow(PlayCardCommand command, Player player) {
        Optional<PropertySet> selectedRow = resolveOwnedPropertyRow(command, player);
        if (selectedRow.isPresent()) {
            return selectedRow.get();
        }

        String rawColor = command.getPropertyColor();
        if (rawColor == null || rawColor.isBlank()) {
            throw new InvalidCommandException(
                    "PROPERTY_ROW_REQUIRED",
                    "targetPropertyRowId or propertyColor is required");
        }
        PropertyColor color = host.parsePropertyColor(rawColor);
        return player.propertyBoard().rowsView().stream()
                .filter(row -> row.anchorColor() == color && row.isComplete())
                .findFirst()
                .orElseThrow(() -> new GameException(
                        "NO_COMPLETE_SET",
                        "No complete property set exists for " + color));
    }

    private void discardActionFromHand(Player player, ActionCard actionCard, ActiveGameState activeGame) {
        removeActionFromHand(player, actionCard);
        activeGame.discard(actionCard);
    }

    private void removeActionFromHand(Player player, ActionCard actionCard) {
        if (!player.hand().remove(actionCard)) {
            throw new GameException("CARD_NOT_IN_HAND", "Action card is not in hand: " + actionCard.id());
        }
    }

    private PropertyColor resolvePropertyColor(PlayCardCommand command, Card card) {
        String rawColor = command.getPropertyColor();
        if ((rawColor == null || rawColor.isBlank()) && card instanceof PropertyCard propertyCard) {
            return propertyCard.color();
        }
        if (rawColor == null || rawColor.isBlank()) {
            throw new InvalidCommandException(
                    "PROPERTY_COLOR_REQUIRED",
                    "propertyColor is required for wild property cards");
        }
        PropertyColor color = host.parsePropertyColor(rawColor);
        if (card instanceof PropertyCard propertyCard && propertyCard.color() != color) {
            throw new GameException("PROPERTY_COLOR_MISMATCH", "Property card cannot be played to " + color);
        }
        if (card instanceof PropertyWildCard wildCard && !wildCard.allowedColors().contains(color)) {
            throw new GameException("PROPERTY_COLOR_MISMATCH", "Wild card does not support color " + color);
        }
        return color;
    }

    private Optional<PropertySet> resolveOwnedPropertyRow(PlayCardCommand command, Player player) {
        String rowId = command.getTargetPropertyRowId();
        if (rowId != null && !rowId.isBlank()) {
            return Optional.of(player.findPropertyRow(rowId)
                    .orElseThrow(() -> new GameException(
                            "PROPERTY_ROW_NOT_FOUND",
                            "No property row exists with id " + rowId)));
        }
        return resolvePropertyRowIndex(command)
                .map(index -> {
                    List<PropertySet> rows = player.propertyBoard().rowsView();
                    if (index < 0 || index >= rows.size()) {
                        throw new GameException("PROPERTY_ROW_NOT_FOUND", "No property row exists at index " + index);
                    }
                    return rows.get(index);
                });
    }

    private Optional<Integer> resolvePropertyRowIndex(PlayCardCommand command) {
        Object legacyIndex = command.payload().get("propertyRowIndex");
        String rawIndex = legacyIndex == null ? null : legacyIndex.toString();
        if (rawIndex == null || rawIndex.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(rawIndex.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(
                    "INVALID_PROPERTY_ROW_INDEX",
                    "propertyRowIndex must be a number",
                    e);
        }
    }

    private PropertyPlacement placementForTransferredProperty(Card card, PropertySet sourceRow) {
        if (card instanceof PropertyCard propertyCard) {
            return new PropertyPlacement(propertyCard.color(), null);
        }
        if (card instanceof PropertyWildCard wildCard) {
            PropertyColor choice = sourceRow.wildAssignmentFor(wildCard.id())
                    .orElse(sourceRow.anchorColor());
            return new PropertyPlacement(choice, choice);
        }
        throw new GameException("PROPERTY_CANNOT_PAY_DEBT", "Only property cards can be transferred as properties");
    }

    private record PropertyPlacement(PropertyColor anchorColor, PropertyColor wildChoice) {
    }
}
