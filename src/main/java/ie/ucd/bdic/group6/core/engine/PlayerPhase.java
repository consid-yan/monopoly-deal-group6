package ie.ucd.bdic.group6.core.engine;

public enum PlayerPhase {
    WAITING(false, false, false, false),
    RESPONDING(false, true, false, false),
    ACTING(true, false, true, false),
    DISCARDING(true, false, false, true);

    private final boolean allowsHandSelection;
    private final boolean allowsResponse;
    private final boolean allowsNormalTurnActions;
    private final boolean allowsDiscard;

    PlayerPhase(
            boolean allowsHandSelection,
            boolean allowsResponse,
            boolean allowsNormalTurnActions,
            boolean allowsDiscard) {
        this.allowsHandSelection = allowsHandSelection;
        this.allowsResponse = allowsResponse;
        this.allowsNormalTurnActions = allowsNormalTurnActions;
        this.allowsDiscard = allowsDiscard;
    }

    public boolean allowsHandSelection() {
        return allowsHandSelection;
    }

    public boolean allowsResponse() {
        return allowsResponse;
    }

    public boolean allowsNormalTurnActions() {
        return allowsNormalTurnActions;
    }

    public boolean allowsDiscard() {
        return allowsDiscard;
    }
}
