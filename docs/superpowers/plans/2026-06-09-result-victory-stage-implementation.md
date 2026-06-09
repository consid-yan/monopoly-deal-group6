# Result Victory Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the form-like result page with a compact, game-styled victory stage that highlights the winner, key statistics, and final standings.

**Architecture:** Keep the existing `ResultUI` controller and `UiSnapshot` contract. Add pure package-visible formatting helpers for concise reason and standings rows, rebuild `result.fxml` around labels and a standings list, and add result-specific CSS that reuses the established menu theme.

**Tech Stack:** Java 25, JavaFX FXML/CSS, JUnit 5, Maven

---

### Task 1: Result Formatting Behavior

**Files:**
- Create: `src/test/java/ie/ucd/bdic/group6/ui/ResultUITest.java`
- Modify: `src/main/java/ie/ucd/bdic/group6/ui/ResultUI.java`

- [ ] **Step 1: Write failing tests for concise reason and winner-first standings**

Add tests that construct finished `UiSnapshot` values and assert:

```java
assertEquals("Completed 3 full property sets.", ResultUI.victoryReason(snapshot, winner));
assertEquals("Last active player after forfeits.", ResultUI.victoryReason(forfeitSnapshot, winner));
assertEquals(List.of(
        new ResultUI.StandingRow(1, true, "Winner", 1, 4),
        new ResultUI.StandingRow(2, false, "Collector", 3, 25)
), ResultUI.standingRows(forfeitSnapshot));
```

- [ ] **Step 2: Run the result test to verify it fails**

Run: `mvn -Dtest=ResultUITest test`

Expected: FAIL because `victoryReason`, `standingRows`, and `StandingRow` do not exist.

- [ ] **Step 3: Implement minimal result formatting helpers**

In `ResultUI.java`, add:

```java
record StandingRow(int rank, boolean winner, String displayName, int completedSets, int bankValueM) {
    String summary() {
        return completedSets + (completedSets == 1 ? " full set" : " full sets") + "  ·  Bank $" + bankValueM + "M";
    }
}
```

Add package-visible static helpers that put the winner first and sort remaining players by completed sets descending, bank value descending, then display name.

- [ ] **Step 4: Run the result test to verify it passes**

Run: `mvn -Dtest=ResultUITest test`

Expected: PASS.

### Task 2: Victory Stage FXML And Styles

**Files:**
- Modify: `src/test/java/ie/ucd/bdic/group6/ui/ResultUITest.java`
- Modify: `src/main/resources/fxml/result.fxml`
- Modify: `src/main/resources/css/style.css`

- [ ] **Step 1: Write failing resource structure tests**

Add tests that assert `result.fxml` contains:

```text
result-screen-root
result-winner-name
result-stat-card
result-standings-list
```

and does not contain:

```text
TextField
TextArea
TitledPane
```

Also assert `style.css` contains rules for `.result-winner-name`, `.result-stat-card`, `.result-standing-winner`, and `.result-crown-medal`.

- [ ] **Step 2: Run the result test to verify it fails**

Run: `mvn -Dtest=ResultUITest test`

Expected: FAIL because the old FXML still uses form controls and the new CSS classes do not exist.

- [ ] **Step 3: Replace result FXML with the approved victory-stage hierarchy**

Create a `StackPane` root with:

- Shared felt pattern and diamond decorations.
- Centered Monopoly Deal brand.
- Crown medal, match-complete label, winner name, and concise reason.
- Three stat cards bound to `completedSetsLabel`, `bankValueLabel`, and `finalRankLabel`.
- `ListView<StandingRow>` bound to `standingsList`.
- Centered menu-style action buttons.

- [ ] **Step 4: Add result-screen CSS**

Add focused result classes that:

- Constrain the result content width.
- Make the winner name and crown the visual focal point.
- Style stats as compact gold-bordered cards.
- Style standings as transparent rows with a gold winner highlight.
- Keep actions centered and consistent with menu buttons.

- [ ] **Step 5: Run the result test to verify it passes**

Run: `mvn -Dtest=ResultUITest test`

Expected: PASS.

### Task 3: Controller Rendering And Verification

**Files:**
- Modify: `src/main/java/ie/ucd/bdic/group6/ui/ResultUI.java`

- [ ] **Step 1: Wire the new FXML controls**

Replace old `TextField` and `TextArea` members with:

```java
Label winnerNameLabel;
Label victoryReasonLabel;
Label completedSetsLabel;
Label bankValueLabel;
Label finalRankLabel;
ListView<StandingRow> standingsList;
```

Install a cell factory that applies `result-standing-winner` for the winner and renders rank, player name, and summary.

- [ ] **Step 2: Render finished and unavailable states**

For finished snapshots, populate the winner stage and standings. For missing or unfinished snapshots, clear statistics and show the status in `victoryReasonLabel`.

- [ ] **Step 3: Run focused and full tests**

Run:

```powershell
mvn -Dtest=ResultUITest test
mvn test
```

Expected: both commands PASS.

- [ ] **Step 4: Launch and visually verify**

Run: `mvn javafx:run`

Verify the result page at the normal desktop window size:

- Winner is the primary focal point.
- Stats and standings remain readable.
- No old form-style panels or large summary text area remain.
- Actions are centered and visually consistent.

