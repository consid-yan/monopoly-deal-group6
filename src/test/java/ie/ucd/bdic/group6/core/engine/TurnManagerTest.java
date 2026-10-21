package ie.ucd.bdic.group6.core.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnManagerTest {

    @Test
    void newTurnManagerStartsWithFirstPlayerAndZeroActions() {
        TurnManager tm = new TurnManager(List.of("p1", "p2", "p3"));
        assertEquals("p1", tm.activePlayerId());
        assertEquals(0, tm.actionsUsed());
        assertEquals(3, tm.actionsRemaining());
        assertTrue(tm.canRecordAction());
    }

    @Test
    void recordActionIncrementsCount() {
        TurnManager tm = new TurnManager(List.of("p1", "p2"));
        tm.recordAction();
        assertEquals(1, tm.actionsUsed());
        assertEquals(2, tm.actionsRemaining());
    }

    @Test
    void thirdActionExhaustsLimit() {
        TurnManager tm = new TurnManager(List.of("p1", "p2"));
        tm.recordAction();
        tm.recordAction();
        tm.recordAction();
        assertEquals(0, tm.actionsRemaining());
        assertFalse(tm.canRecordAction());
    }

    @Test
    void fourthActionThrowsIllegalState() {
        TurnManager tm = new TurnManager(List.of("p1", "p2"));
        tm.recordAction();
        tm.recordAction();
        tm.recordAction();
        assertThrows(IllegalStateException.class, tm::recordAction);
    }

    @Test
    void advanceToNextTurnResetsActionsAndCyclesPlayer() {
        TurnManager tm = new TurnManager(List.of("p1", "p2", "p3"));
        tm.recordAction();
        tm.recordAction();
        String next = tm.advanceToNextTurn();
        assertEquals("p2", next);
        assertEquals("p2", tm.activePlayerId());
        assertEquals(0, tm.actionsUsed());
        assertEquals(3, tm.actionsRemaining());
    }

    @Test
    void advanceWrapsAroundToFirstPlayer() {
        TurnManager tm = new TurnManager(List.of("p1", "p2"));
        tm.advanceToNextTurn();
        String wrapped = tm.advanceToNextTurn();
        assertEquals("p1", wrapped);
    }

    @Test
    void isActivePlayerReturnsTrueOnlyForCurrentPlayer() {
        TurnManager tm = new TurnManager(List.of("p1", "p2"));
        assertTrue(tm.isActivePlayer("p1"));
        assertFalse(tm.isActivePlayer("p2"));
        tm.advanceToNextTurn();
        assertFalse(tm.isActivePlayer("p1"));
        assertTrue(tm.isActivePlayer("p2"));
    }

    @Test
    void emptyTurnOrderThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(List.of()));
    }
}
