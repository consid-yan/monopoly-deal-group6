package ie.ucd.bdic.group6.core.rules;

import ie.ucd.bdic.group6.core.card.ActionCard;
import ie.ucd.bdic.group6.core.card.ActionType;
import ie.ucd.bdic.group6.core.card.Card;
import ie.ucd.bdic.group6.core.common.OperationResult;
import ie.ucd.bdic.group6.core.engine.GameSession;
import ie.ucd.bdic.group6.core.engine.SessionStatus;
import ie.ucd.bdic.group6.core.engine.TurnManager;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;
import ie.ucd.bdic.group6.exception.GameException;
import ie.ucd.bdic.group6.exception.InvalidCommandException;
import ie.ucd.bdic.group6.exception.InvalidGameStateException;

import java.util.List;
import java.util.Objects;

/**
 * Session/turn gates and action-card playability checks.
 * Side effects (discard, pending actions, transfers) remain in {@code GameEngine}.
 */
public class ActionRule {

    public void requireLobby(GameSession session) {
        if (session.status() != SessionStatus.LOBBY) {
            throw new InvalidGameStateException("INVALID_SESSION_STATE", "This command is only allowed in the lobby");
        }
    }

    public void requireActive(GameSession session) {
        if (session.status() != SessionStatus.ACTIVE) {
            throw new InvalidGameStateException("INVALID_SESSION_STATE", "This command requires an active game");
        }
    }

    public void requireActivePlayer(TurnManager turnManager, String playerId) {
        if (!turnManager.isActivePlayer(playerId)) {
            throw new InvalidGameStateException("NOT_ACTIVE_PLAYER", "It is not this player's turn");
        }
    }

    public void requireActionsAvailable(TurnManager turnManager, int requiredActions) {
        Objects.requireNonNull(turnManager, "turnManager");
        if (requiredActions < 0) {
            throw new IllegalArgumentException("requiredActions must be non-negative");
        }
        if (turnManager.actionsRemaining() < requiredActions) {
            throw new InvalidGameStateException(
                    "ACTION_LIMIT_REACHED",
                    "This play requires " + requiredActions + " action(s) but only "
                            + turnManager.actionsRemaining() + " remain");
        }
    }

    public void validatePlayableAsAction(ActionType actionType) {
        Objects.requireNonNull(actionType, "actionType");
        switch (actionType) {
            case JUST_SAY_NO ->
                throw new GameException(
                        "JUST_SAY_NO_RESPONSE_ONLY",
                        "Just Say No can only be played through respond action");
            case DOUBLE_THE_RENT ->
                throw new GameException(
                        "UNSUPPORTED_ACTION",
                        "Double The Rent must be paired with a rent card");
            default -> { }
        }
    }

    public void validateDebtCollector(Player actor, String targetPlayerId) {
        Objects.requireNonNull(actor, "actor");
        if (actor.id().equals(targetPlayerId)) {
            throw new GameException("INVALID_TARGET_PLAYER", "Debt Collector cannot target yourself");
        }
    }

    public void validateItsMyBirthday(List<String> debtorIds) {
        Objects.requireNonNull(debtorIds, "debtorIds");
        if (debtorIds.isEmpty()) {
            throw new GameException("NO_TARGET_PLAYERS", "Birthday requires at least one target player");
        }
    }

    public void validateRentColorForCard(ActionCard rentCard, PropertyColor color) {
        Objects.requireNonNull(rentCard, "rentCard");
        Objects.requireNonNull(color, "color");
        if (rentCard.actionType() != ActionType.WILD_RENT && !rentCard.rentColorOptions().contains(color)) {
            throw new InvalidCommandException("INVALID_RENT_COLOR", "Rent card cannot charge " + color);
        }
    }

    public void validateInferableRentColors(List<PropertyColor> availableColors) {
        Objects.requireNonNull(availableColors, "availableColors");
        if (availableColors.isEmpty()) {
            throw new GameException("NO_RENTABLE_PROPERTY", "No rentable property matches this rent card");
        }
        if (availableColors.size() > 1) {
            throw new InvalidCommandException(
                    "PROPERTY_COLOR_REQUIRED",
                    "propertyColor is required when multiple rents apply");
        }
    }

    public void validateRentAmount(PropertyColor rentColor, int rentM) {
        Objects.requireNonNull(rentColor, "rentColor");
        if (rentM <= 0) {
            throw new GameException("NO_RENTABLE_PROPERTY", "No rentable set for " + rentColor);
        }
    }

    public void validateRentTargets(List<Player> targets) {
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            throw new GameException("NO_TARGET_PLAYERS", "Rent requires at least one target player");
        }
    }

    public void validateWildRentTarget(Player actor, String targetPlayerId) {
        Objects.requireNonNull(actor, "actor");
        if (targetPlayerId.equals(actor.id())) {
            throw new GameException("INVALID_TARGET_PLAYER", "Rent cannot target yourself");
        }
    }

    public void validateDoubleRentCard(Card card) {
        Objects.requireNonNull(card, "card");
        if (!(card instanceof ActionCard actionCard) || actionCard.actionType() != ActionType.DOUBLE_THE_RENT) {
            throw new GameException("DOUBLE_RENT_REQUIRED", "doubleRentCardId must identify a Double The Rent card");
        }
    }

    public void validateSlyDeal(Player actor, Player target, Card targetCard) {
        Objects.requireNonNull(targetCard, "targetCard");
        requireDifferentPlayers(actor, target, "Sly Deal cannot target yourself");
        if (!target.propertyBoard().canRemoveCardForSteal(targetCard)) {
            throw new GameException("CANNOT_STEAL_FROM_COMPLETE_SET", "Sly Deal cannot steal from a complete set");
        }
    }

    public void validateForcedDeal(Player actor, Player target, Card offered, Card requested) {
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(requested, "requested");
        requireDifferentPlayers(actor, target, "Forced Deal cannot target yourself");
        if (!actor.propertyBoard().canRemoveCardForSteal(offered)
                || !target.propertyBoard().canRemoveCardForSteal(requested)) {
            throw new GameException(
                    "CANNOT_STEAL_FROM_COMPLETE_SET",
                    "Forced Deal cannot swap cards from complete sets");
        }
    }

    public void validateDealBreaker(Player actor, Player target, PropertySet row) {
        Objects.requireNonNull(row, "row");
        requireDifferentPlayers(actor, target, "Deal Breaker cannot target yourself");
        if (!row.isComplete()) {
            throw new GameException("NO_COMPLETE_SET", "Deal Breaker requires a complete target set");
        }
    }

    public void validateImprovementTarget(PropertySet row, ActionType improvementType) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(improvementType, "improvementType");
        OperationResult<Void> validation = improvementType == ActionType.HOUSE
                ? row.validateHouseStructure()
                : row.validateHotelStructure();
        if (!validation.isSuccess()) {
            throw new GameException(
                    validation.errorCode().map(Enum::name).orElse("OPERATION_FAILED"),
                    validation.errorMessage().orElse("row cannot accept " + improvementType + " improvement right now"));
        }
    }

    private void requireDifferentPlayers(Player actor, Player target, String selfTargetMessage) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (actor.id().equals(target.id())) {
            throw new GameException("INVALID_TARGET_PLAYER", selfTargetMessage);
        }
    }
}
