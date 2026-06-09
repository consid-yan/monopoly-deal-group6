package ie.ucd.bdic.group6.ui;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class GameUiState {
    private final Set<String> submittedDebtIds = ConcurrentHashMap.newKeySet();
    private String selectedCardId;
    private boolean commandInFlight;
    private boolean pendingPromptShowing;
    private boolean discardPhasePromptShown;

    String selectedCardId() {
        return selectedCardId;
    }

    void selectCard(String cardId) {
        selectedCardId = cardId;
    }

    void clearSelection() {
        selectedCardId = null;
    }

    boolean commandInFlight() {
        return commandInFlight;
    }

    void setCommandInFlight(boolean commandInFlight) {
        this.commandInFlight = commandInFlight;
    }

    boolean pendingPromptShowing() {
        return pendingPromptShowing;
    }

    void setPendingPromptShowing(boolean pendingPromptShowing) {
        this.pendingPromptShowing = pendingPromptShowing;
    }

    boolean discardPhasePromptShown() {
        return discardPhasePromptShown;
    }

    void markDiscardPhasePromptShown() {
        discardPhasePromptShown = true;
    }

    void clearDiscardPhasePromptShown() {
        discardPhasePromptShown = false;
    }

    Set<String> submittedDebtIds() {
        return submittedDebtIds;
    }

    void reset() {
        selectedCardId = null;
        commandInFlight = false;
        pendingPromptShowing = false;
        discardPhasePromptShown = false;
        submittedDebtIds.clear();
    }
}
