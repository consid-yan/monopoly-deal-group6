package ie.ucd.bdic.group6.core.player;

/**
 * Observes mutations on a player's table zones (hand/bank/properties).
 */
public interface PlayerZoneListener {

    default void onHandChanged(Player player) {
    }

    default void onBankChanged(Player player) {
    }

    default void onPropertyBoardChanged(Player player) {
    }
}
