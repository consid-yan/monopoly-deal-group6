# Project Plan

## Monopoly Deal Multiplayer Card Game

- **Project window:** Thursday, April 23, 2026 to Tuesday, June 9, 2026
- **Sync cadence:** every 7 days, with weekly sync sessions on Thursdays
- **Team size:** 5 members, each contributing approximately 20%
- **Planning baseline:** April 19, 2026

---

## 1. Ownership and Evidence

| Member | Contribution | Main Ownership | Concrete Tasks | Required Evidence |
|---|---:|---|---|---|
| Mouxiang Yan | 20% | Networking and multiplayer integration | `GameServer`, `GameClient`, `GameThread`, state synchronization, reconnect handling, disconnect timeout, session identity | Multiplayer integration tests or demo scripts |
| Yilong Ma | 20% | Core card, player, and property model | `Card`, `ActionCard`, `MoneyCard`, `PropertyCard`, `Player`, `PropertySet`, deck setup, property-set logic | Unit tests for cards, players, property sets, and deck setup |
| Dongrui Gu | 20% | Controller, command, and facade flow | `ControllerRequest`, `RequestMapper`, command classes, `GameController`, `GameFacade`, handlers, error results | Facade/controller tests and invalid-command tests |
| Yiming Liu | 20% | Game engine and rules | `GameSession`, `TurnManager`, `ActionExecutor`, `ActionRule`, `PaymentRule`, `WinRule`, draw rules, hand limit, win checking | Unit tests for turns, payments, actions, and winning |
| Tian Gao | 20% | JavaFX UI and user-facing flow | `GameUI`, `HowToPlayUI`, main game screen, player state display, private/public info, result screen, error messages | UI screenshots, manual test checklist, how-to-play content |

---

## 2. Weekly Roadmap

| Session | Date | Main Milestone | Deliverables |
|---|---:|---|---|
| 1 | Apr 23 | Kickoff and architecture lock | Confirm shared scope, interfaces, feature boundaries, and milestone definitions. Each member produces a short design note for their area before implementation begins. |
| 2 | Apr 30 | Core domain baseline complete | Card, player, property, session, controller, networking skeleton, and static UI shells are available. Dependencies between model, engine, facade, network, and UI are agreed. |
| 3 | May 7 | Playable local engine through facade | A local game can start and process basic commands through controller/facade. Core rule tests and facade invalid-command tests begin. Command shapes should be frozen after this session unless a defect requires change. |
| 4 | May 14 | Local end-to-end game loop | Local gameplay supports start game, play card, trade, end turn, invalid actions, hand display, public player state, and win checks through the planned architecture. |
| 5 | May 21 | Multiplayer synchronization milestone | Server becomes authoritative for multiplayer state. Clients can send gameplay commands and receive synchronized game-state updates after valid actions and useful errors after invalid actions. |
| 6 | May 28 | Reconnect, disconnect, and UI completion | Reconnect by session identity, 90-second reconnect window, 30-second disconnected-turn skip, forfeit handling, result screen, how-to-play screen, and user-facing error messages are complete. |
| 7 | Jun 4 | Feature freeze and final integration begins | Non-essential feature work stops. Team focuses on bug fixes, integration stability, evidence collection, screenshots, demo scripts, and full acceptance testing. |
| Final | Jun 9 | Submission-ready release | Final build, final tests, documentation, screenshots, demo evidence, and presentation/submission materials are complete. |

---

## 3. Session Milestones

### Session 1 - April 23: Kickoff and Architecture Lock

**Goal:** make all major technical boundaries explicit before implementation accelerates.

**Mouxiang Yan**
- Define multiplayer flow from client launch to server join, command send, state broadcast, disconnect, and reconnect.
- Draft session identity rules for player rejoin.
- Identify the minimum network message types needed for the first multiplayer demo.

**Yilong Ma**
- Finalize core model responsibilities for cards, players, money, properties, property colors, and property sets.
- Confirm how deck setup represents property, money, and action cards.
- Identify model methods required by rules, UI, and network snapshots.

**Dongrui Gu**
- Define the controller/facade command boundary.
- Confirm request fields needed for start game, play card, trade, and end turn.
- Define success and error result expectations.

**Yiming Liu**
- Define `GameSession`, `TurnManager`, and rule-class responsibilities.
- Confirm adopted Monopoly Deal rule scope, including draw rules, hand limit, action limit, payments, simplified trading, and win detection.

**Tian Gao**
- Draft UI screen flow for main menu, lobby/start flow, game screen, how-to-play screen, error display, and result screen.
- Define which game-state fields the UI needs privately for the current player and publicly for other players.

**Exit criteria**
- Each member has a clear implementation target for Session 2.
- Interface-risk items are recorded before code diverges.

---

### Session 2 - April 30: Core Domain Baseline Complete

**Goal:** establish the domain objects and application skeletons that later work depends on.

**Mouxiang Yan**
- Implement or complete `GameServer`, `GameClient`, and `GameThread` skeletons.
- Support local connection setup and placeholder request/response exchange.
- Add a short multiplayer run note or script outline.

**Yilong Ma**
- Implement or complete `Card`, `ActionCard`, `MoneyCard`, `PropertyCard`, `Player`, `PropertySet`, `PropertyColor`, and deck setup.
- Provide methods for player hand, bank, properties, and property-set completion.
- Add first unit tests for basic card construction and player state changes.

**Dongrui Gu**
- Implement basic mapping from `ControllerRequest` to command records.
- Ensure command classes represent start game, play card, trade, and end turn consistently.
- Return clear errors for unsupported or malformed request types.

**Yiming Liu**
- Start `GameSession` and `TurnManager` on top of Yilong's model.
- Support session creation, player registration, game start constraints, and initial turn order.
- Add first tests for session start and active-player tracking.

**Tian Gao**
- Create static JavaFX navigation shell.
- Add placeholder screens for main game, how-to-play, and result display.
- Keep UI state mocked until facade/state views stabilize.

**Exit criteria**
- The project compiles.
- Domain objects and skeleton layers exist for all later integration work.
- Model interfaces required by the engine are stable enough for Session 3.

---

### Session 3 - May 7: Playable Local Engine Through Facade

**Goal:** make the game playable locally through the controller/facade boundary.

**Mouxiang Yan**
- Define the game-state snapshot shape needed for network clients.
- Confirm private versus public fields in snapshots.
- Prepare client/server integration points for Session 5.

**Yilong Ma**
- Complete tests for cards, players, property sets, and deck integrity.
- Fix any model gaps discovered by engine and facade work.
- Ensure property-set logic can support win checking.

**Dongrui Gu**
- Complete `GameFacade` dispatch and command handlers.
- Ensure valid and invalid commands produce structured `CommandResult` responses.
- Add facade/controller tests for accepted and rejected commands.

**Yiming Liu**
- Implement draw rules, action limits, payment basics, hand limit, and win checking.
- Connect rule validation to `ActionExecutor`.
- Add unit tests for turns, actions, payments, and wins.

**Tian Gao**
- Connect UI shell to facade-backed or mocked game state.
- Display active player, own hand, own bank, own properties, and public opponent summaries.
- Start user-facing error display from `CommandResult`.

**Exit criteria**
- A local non-network game flow can be exercised through controller/facade.
- Command shape freeze begins after this milestone.
- Tests cover the most important happy-path and invalid-command flows.

---

### Session 4 - May 14: Local End-to-End Game Loop

**Goal:** stabilize local gameplay before multiplayer becomes authoritative.

**Mouxiang Yan**
- Prepare multiplayer hooks for submitting commands through the same application boundary as local UI.
- Create a two-client demo plan using local machine networking.
- Identify any serialization blockers.

**Yilong Ma**
- Finish property-set completion edge cases.
- Support any missing state-view or serialization needs without moving rule logic into the model.
- Expand unit tests for model edge cases.

**Dongrui Gu**
- Harden controller to facade flow for start game, play card, trade, end turn, and invalid moves.
- Make error results suitable for UI and network display.
- Add tests for malformed requests and wrong-turn commands.

**Yiming Liu**
- Complete local gameplay loop behavior across multiple turns.
- Validate simplified trade sequence, payment behavior, hand limit, and game-over state.
- Add tests for complete turn cycles and win detection.

**Tian Gao**
- Implement the main game screen with hand, bank, properties, active player, action controls, public player info, and visible errors.
- Confirm UI does not expose other players' private hands.
- Prepare manual UI checklist draft.

**Exit criteria**
- Local end-to-end flow is demonstrable.
- UI can display meaningful game state.
- Multiplayer can now integrate without inventing separate gameplay rules.

---

### Session 5 - May 21: Multiplayer Synchronization Milestone

**Goal:** move from local gameplay to authoritative multiplayer synchronization.

**Mouxiang Yan**
- Integrate `GameServer`, `GameClient`, and `GameThread` with controller/facade flow.
- Make server authoritative for game state and command processing.
- Broadcast updated state to all clients after valid actions.
- Add demo scripts or documented steps for a two-client multiplayer test.

**Yilong Ma**
- Support model serialization or state-view needs discovered during network integration.
- Fix data consistency issues found during multiplayer testing.
- Keep model tests passing.

**Dongrui Gu**
- Ensure command errors serialize cleanly to clients.
- Keep facade responses stable for both UI and network callers.
- Add tests for invalid network-originated commands where practical.

**Yiming Liu**
- Verify the engine remains deterministic when commands arrive through networking.
- Ensure rejected actions do not mutate game state.
- Add tests for network-like command sequences using facade/controller boundaries.

**Tian Gao**
- Connect UI actions to network/client API where available.
- Show synchronized state updates from the server.
- Display server-returned errors clearly.

**Exit criteria**
- At least two local clients can connect to one server.
- Valid actions from one client update other clients.
- Invalid actions are rejected without crashing or corrupting state.

---

### Session 6 - May 28: Reconnect, Disconnect, and UI Completion

**Goal:** complete the reliability and user-facing requirements before feature freeze.

**Mouxiang Yan**
- Implement disconnect detection.
- Preserve recoverable session identity for 90 seconds.
- Skip a disconnected player's turn after 30 seconds when applicable.
- Mark a player as defeated/forfeit after the reconnect window expires.
- Add reconnect/disconnect integration tests or demo script evidence.

**Yilong Ma**
- Fix model edge cases found during reconnect and forfeit testing.
- Ensure forfeited player card handling follows project rules.
- Keep model and property-set tests complete.

**Dongrui Gu**
- Add invalid-command tests for recovery and disconnected-player paths.
- Ensure controller/facade rejects commands after game termination.
- Confirm error responses are clear and consistent.

**Yiming Liu**
- Validate disconnected-turn skip and forfeit interactions with turn order, action rules, and win rules.
- Add tests for skip, forfeit, and game-continuity behavior.
- Confirm game state remains valid after a player leaves.

**Tian Gao**
- Complete how-to-play content.
- Complete result screen.
- Complete user-facing error messages.
- Confirm private/public information separation through UI manual checks.
- Capture first UI screenshots.

**Exit criteria**
- Reconnect, timeout, and forfeit behavior work in a demo scenario.
- UI covers game flow from start to result.
- Feature work is ready to freeze after the next sync.

---

### Session 7 - June 4: Feature Freeze and Final Integration Begins

**Goal:** stop feature expansion and make the full product reliable.

**All members**
- Stop adding non-essential features.
- Focus on defects, test completion, documentation, evidence, and demo stability.
- Run the full acceptance test path with 2 to 5 players where possible.

**Mouxiang Yan**
- Run multiplayer demo scripts.
- Fix synchronization, reconnect, and timeout defects.
- Record integration evidence.

**Yilong Ma**
- Complete model unit test evidence.
- Fix any model defects from acceptance testing.

**Dongrui Gu**
- Complete facade/controller test evidence.
- Verify invalid-command behavior and error results.

**Yiming Liu**
- Complete rules/engine test evidence.
- Verify turns, payments, actions, hand limits, and winning.

**Tian Gao**
- Complete UI screenshots.
- Complete manual test checklist.
- Verify how-to-play content and result screen.

**Exit criteria**
- Team has a working release candidate.
- Remaining work is limited to bug fixes, documentation, packaging, and evidence.

---

### Final Deadline - June 9: Submission-Ready Release

**Goal:** deliver a tested, documented, demonstrable project.

**Team tasks**
- Run `mvn clean test`.
- Run final local multiplayer demo with at least two clients.
- Confirm acceptance scenarios are complete.
- Update README and project documentation where needed.
- Add final screenshots and demo evidence.
- Prepare final presentation or submission materials.
- Record known limitations honestly if any requirement is partially implemented.

**Exit criteria**
- Final build is available.
- Tests and manual checks are documented.
- Submission evidence is complete.

---

## 4. Acceptance Scenarios

### Core Gameplay

- Start a game with a valid number of players.
- Reject a game start with too few or too many players.
- Draw cards at the correct point in the turn.
- Play valid property, money, and action cards.
- Reject card plays made by the wrong player or with invalid targets.
- Enforce action limits and hand limits.
- Detect a winner after three full property sets.

### Trade and Payment

- Complete a supported property-for-money trade.
- Complete a supported money-for-property trade.
- Complete a supported property-for-property trade.
- Reject unsupported trade combinations.
- Validate payments using legal money and property choices.
- Ensure failed trades and invalid payments do not mutate game state.

### Multiplayer and Recovery

- Connect at least two clients to one server.
- Submit a valid command from one client and see updated state on all clients.
- Submit an invalid command and receive a useful error without crashing.
- Disconnect one player and keep the game running.
- Reconnect the same player within 90 seconds and restore current state.
- Skip a disconnected player's turn after 30 seconds.
- Mark a player as defeated/forfeit after the reconnect window expires.

### UI

- Display the current player's hand privately.
- Display public player state for opponents.
- Show active player, bank, properties, available actions, errors, and final result.
- Provide an accessible how-to-play screen.
- Preserve privacy by never showing another player's private hand in the normal UI.

---
