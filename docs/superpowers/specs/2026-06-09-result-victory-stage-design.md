# Result Victory Stage Design

## Goal

Replace the form-like game result screen with a compact victory stage that feels consistent with Monopoly Deal's existing green felt, warm gold, and playing-card visual language.

## Visual Direction

The result screen uses the approved "Victory Stage" direction:

- Deep green felt background with the same subtle pattern and oversized diamond corner decorations used by the menu and game screens.
- A centered Monopoly Deal wordmark followed by a crown medal, a small `MATCH COMPLETE` eyebrow, and the winner's name as the primary focal point.
- One concise victory-reason line. The existing large match-summary text area is removed.
- Three compact statistic cards for completed property sets, bank value, and final rank.
- A compact final-standings list. The winner row receives a gold highlight and crown icon; remaining players use numbered ranks.
- Centered `Main Menu`, `Play Again`, and `Exit` actions using the established menu button styles.

## Layout

The screen remains responsive within the existing JavaFX scene:

1. Decorative background layers fill the `StackPane`.
2. A centered content column contains the brand, winner stage, statistics, standings, and actions.
3. The content column has a bounded maximum width so the victory information remains focused on large windows.
4. The standings area grows vertically when necessary and scrolls for unusually large player lists.

## Data And Behavior

`ResultUI` continues to receive completed `UiSnapshot` instances from `GameSessionCoordinator`.

For a finished match it renders:

- Winner display name.
- A concise reason: completed property sets, last active player after forfeits, or last active player.
- Winner's completed set count.
- Winner's bank total value.
- Winner's final rank as `1st`.
- Player standings with the winner first, followed by remaining players sorted by completed set count descending, bank value descending, then display name.

For missing or unfinished snapshots, the screen shows a concise status message in the victory-reason area and clears the winner statistics and standings.

Existing actions remain unchanged:

- `Play Again` leaves the current session and returns to the menu.
- `Main Menu` leaves the current session and returns to the menu.
- `Exit` closes the application.

## Implementation Boundaries

- `result.fxml` owns the new visual hierarchy and control identifiers.
- `ResultUI.java` owns result formatting and ranking data.
- `style.css` owns result-screen-specific visual treatment while reusing shared menu styles.
- `ResultUITest.java` verifies concise reason text, ranking order, and standings formatting.
- No game engine, snapshot contract, network, or session behavior changes are required.

## Testing And Verification

- Add unit tests for completed-set wins, forfeiture wins, and standings order.
- Add resource-level checks that the result FXML uses the victory-stage structure and no longer contains the old form controls or match-summary text area.
- Run the result UI tests and then the complete Maven test suite.
- Launch the JavaFX application and visually verify the result screen at the normal desktop window size.
