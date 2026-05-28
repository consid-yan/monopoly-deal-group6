# Software Requirements Specification

## Monopoly Deal Group 6

---

## 1. Introduction

### 1.1 Purpose

This Software Requirements Specification defines the requirements for **Monopoly Deal Group 6**, a digital multiplayer implementation of the Monopoly Deal card game. The document identifies:

* **User requirements**
* **System requirements**
* **Functional requirements**
* **Non-functional requirements**

The purpose of the system is to allow **2 to 5 players** to play Monopoly Deal digitally with correct rule enforcement, multiplayer synchronization, turn control, simplified project-specific trading, and disconnect recovery.

### 1.2 Assumptions and Project Decisions

The following project decisions are assumed in this document:

1. The game uses the **standard Monopoly Deal win condition**: a player wins by completing **3 full property sets**.
2. The game uses the **standard draw and hand-limit rules** of Monopoly Deal.
3. The game supports **2 to 5 players**.
4. Player disconnect and rejoin are **supported**.
5. Trading uses a **simplified course-project rule set**, not the full official Monopoly Deal trading behavior.

---

## 2. Overall Description

### 2.1 Product Perspective

The project is a multiplayer digital card game system structured into controller, facade, core logic, networking, and UI layers. Based on the supplied project structure, the system includes:

* **Controller layer** for request intake and mapping
* **Facade layer** for use-case coordination
* **Core layer** for cards, players, rules, and game engine behavior
* **Network layer** for server-client communication
* **UI layer** for gameplay interaction and how-to-play support

### 2.2 Product Functions

At a high level, the system shall:

* create and manage game sessions,
* allow players to join and start a game,
* manage turns,
* let players play valid cards,
* process actions, payments, and trades,
* enforce game rules,
* synchronize game state between clients,
* recover from disconnection,
* determine and display the winner.

---

## 3. User Requirements

User requirements describe what players need from the system.

### UR-01 Start a Game

The user shall be able to create and start a new Monopoly Deal game session.

### UR-02 Join a Game

The user shall be able to join a game session as a player.

### UR-03 Supported Player Count

The user shall be able to play the game with **2 to 5 players**.

### UR-04 View Game State

The user shall be able to view the relevant game state, including:

* their own hand,
* their banked money,
* their property sets,
* the active player,
* public information about other players.

### UR-05 Play Cards

The user shall be able to play valid cards during their turn according to the adopted Monopoly Deal rules.

### UR-06 Draw and End Turn

The user shall be able to draw cards and end their turn according to the standard rules.

### UR-07 Pay Debts

The user shall be able to make required payments using valid money and/or property cards.

### UR-08 Trade with Other Players

The user shall be able to perform trades using the simplified project trade mechanism.

### UR-09 Receive Error Feedback

The user shall be informed when an attempted action is invalid.

### UR-10 Access Instructions

The user shall be able to access a “How to Play” guide.

### UR-11 See Game Result

The user shall be informed when the game ends and shall be shown the winner.

### UR-12 Continue After Disconnect

The user shall be able to continue participating in the game after a temporary disconnect, subject to the project’s reconnection rules.

---

## 4. System Requirements

System requirements specify how the software shall behave to satisfy the user requirements.

---

## 4.1 Functional Requirements

### 4.1.1 Game Session Management

**FR-01 Session Creation**
The system shall allow a new game session to be created.

**FR-02 Player Registration**
The system shall allow players to join a game session before the game starts.

**FR-03 Player Limit Enforcement**
The system shall allow a game to start only when the number of players is between **2 and 5 inclusive**.

**FR-04 Unique Player Identity**
The system shall assign each player a unique identity within the session.

**FR-05 Game Initialization**
When a game starts, the system shall initialize:

* the deck,
* the players,
* the turn order,
* each player's initial hand

according to the adopted Monopoly Deal rules.

---

### 4.1.2 Core Gameplay

**FR-06 Card Type Support**
The system shall support at least these card types:

* Property Cards
* Money Cards
* Action Cards

**FR-07 Rule Enforcement**
The system shall enforce the adopted Monopoly Deal rules throughout gameplay.

**FR-08 Turn Management**
The system shall maintain exactly one active player turn at any time.

**FR-09 Standard Turn Draw Rule**
At the start of a player’s turn, the system shall draw cards according to the standard Monopoly Deal rules.

**FR-10 Card Play Validation**
The system shall validate each attempted card play before applying it to the game state.

**FR-11 Property Card Placement**
The system shall allow a valid Property Card to be placed into the correct property area of the active player.

**FR-12 Money Card Placement**
The system shall allow a valid Money Card to be placed into the active player’s bank.

**FR-13 Action Card Execution**
The system shall execute the effect of a valid Action Card according to the adopted rules.

**FR-14 Action Limit Enforcement**
The system shall enforce the number of actions allowed per turn according to the adopted Monopoly Deal rules.

**FR-15 Invalid Action Rejection**
The system shall reject any attempted action that violates turn order, card rules, payment rules, or trade rules.

**FR-16 Invalid Action Feedback**
When an action is rejected, the system shall provide the player with a reason for rejection.

---

### 4.1.3 Payment Handling

**FR-17 Payment Processing**
The system shall support required player payments using valid combinations of banked money and/or properties according to the adopted rules.

**FR-18 Payment Validation**
The system shall validate whether a proposed payment is legal before applying it.

---

### 4.1.4 Simplified Trade Rules

**FR-19 Simplified Trade Scope**
The system shall support only the following trade combinations in the simplified course-project version:

* Property Card for Money Card
* Money Card for Property Card
* Property Card for Property Card

**FR-20 Invalid Trade Type Rejection**
The system shall reject any proposed trade in which the offered and requested cards do not match one of the three supported exchange scenarios defined in FR-19.

**FR-21 Trade Initiation Restriction**
The system shall allow **Player A** to initiate a trade request only during **Player A’s turn**.

**FR-22 Trade Response Restriction**
The system shall allow **Player B** to accept or reject a trade request only during **Player B’s turn**.

**FR-23 Sequential Mutual Agreement Rule**
The system shall treat a trade as valid only when both involved players explicitly initiate and agree to the trade in sequence according to the simplified trade protocol.

**FR-24 Trade Completion Rule**
The system shall execute a trade only after the sequential mutual agreement defined in FR-23 has been completed successfully.

**FR-25 Trade Rejection Handling**
If either player rejects the trade, or if the sequence of confirmation is incomplete, the system shall cancel the trade and leave both players’ cards unchanged.

**FR-26 Trade State Update**
When a valid trade is completed, the system shall transfer the involved cards to the correct players and synchronize the updated game state across all connected clients.

---

### 4.1.5 Turn Completion and Hand Limit

**FR-27 End Turn Command**
The system shall allow the active player to end their turn.

**FR-28 End Turn Validation**
The system shall prevent a player from ending the turn when turn-ending conditions have not been satisfied.

**FR-29 Standard Hand Limit Rule**
The system shall enforce the standard Monopoly Deal hand-limit rule at the correct point in the turn.

---

### 4.1.6 Win Detection and Results

**FR-30 Win Condition Evaluation**
The system shall continuously evaluate whether any player has met the win condition.

**FR-31 Standard Win Rule**
The system shall declare a player the winner when that player completes **3 full property sets** according to the standard Monopoly Deal rules used by the project.

**FR-32 Game Termination**
Once a winner is declared, the system shall terminate active gameplay and reject further game actions.

**FR-33 Result Display**
When the game ends, the system shall display the winner and final result information.

---

### 4.1.7 UI and Information Visibility

**FR-34 Game State Display**
The system shall display the current relevant game state through the user interface.

**FR-35 Private and Public Information Separation**
The system shall display a player’s own hand privately and shall display only public information for other players.

**FR-36 How-To-Play Support**
The system shall provide a “How to Play” interface or screen.

---

### 4.1.8 Networking and Disconnect Recovery

**FR-37 Multiplayer Synchronization**
The system shall synchronize the authoritative game state across all connected clients after each valid action.

**FR-38 Disconnect Detection**
The system shall detect when a player disconnects from the active session.

**FR-39 Rejoin by Same Account/Session**
The system shall allow a disconnected player to rejoin the game only through the **same account/session identity** previously associated with that player.

**FR-40 Session Validity Window**
The system shall preserve the disconnected player’s session state for **90 seconds** after disconnection.

**FR-41 Turn Timeout for Disconnected Player**
If it is the disconnected player’s turn, the system shall automatically skip that turn after **30 seconds**.

**FR-42 Game Continuity on Disconnect**
The system shall allow the game to continue after a player disconnects; the game shall not pause waiting for reconnection.

**FR-43 Rejoin State Restoration**
If the player reconnects within the 90-second validity window, the system shall restore that player to the current game session using their preserved identity and current game state.

**FR-44 Forfeit on Expired Reconnection Window**
If the player does not reconnect within **90 seconds**, the system shall mark that player as **Defeated/Forfeit**.

**FR-45 Card Discard on Forfeit**
When a player is marked as Defeated/Forfeit due to failure to reconnect in time, the system shall discard that player’s cards according to the project rules.

**FR-46 Command Support**
The system shall support at least these gameplay commands:

* create session,
* join session,
* start game,
* play card,
* pay debt,
* propose trade,
* respond to trade,
* discard cards,
* end turn,
* rejoin session.

---

## 4.2 Non-Functional Requirements

### 4.2.1 Usability

**NFR-01 Interface Clarity**
The system shall display the active player, available game information, and relevant user actions clearly enough for a player to follow turn progression after reading the in-game instructions once.

**NFR-02 Learnability**
A new user shall be able to start a game, play a valid card, and end a turn within **10 minutes** of first use.

**NFR-03 Help Availability**
The system shall make the “How to Play” guide accessible through the user interface.

---

### 4.2.2 Performance

**NFR-04 Local Response Time**
For a valid local action, the system shall update the game state and interface within **1 second**.

**NFR-05 Multiplayer Update Delay**
Under normal network conditions, a valid action shall be reflected on all connected clients within **2 seconds**.

**NFR-06 Turn Flow Responsiveness**
The system shall skip a disconnected player’s turn within **30 seconds** so that gameplay remains responsive.

---

### 4.2.3 Reliability and Robustness

**NFR-07 Invalid Input Robustness**
The system shall not crash when a player submits an invalid command or invalid move.

**NFR-08 Session State Integrity**
The system shall maintain a consistent authoritative game state throughout the session.

**NFR-09 Disconnect Recovery Reliability**
The system shall preserve a disconnected player’s recoverable session data for at least **90 seconds** without corrupting the ongoing game state.

**NFR-10 Trade Atomicity**
A trade shall either be completed in full for both players or not be completed at all.

---

### 4.2.4 Security and Privacy

**NFR-11 Private Hand Confidentiality**
A player’s hand shall not be visible to other players through the normal user interface.

**NFR-12 Controlled Visibility**
Only public game information shall be shared across players.

---

### 4.2.5 Maintainability and Modifiability

**NFR-13 Testability**
Each functional requirement shall be verifiable through unit tests, integration tests, or acceptance tests.

**NFR-14 Modular Rule Design**
The implementation shall allow action rules, payment rules, and win rules to be modified without requiring changes to unrelated components.

**NFR-15 Architectural Separation**
The implementation shall preserve separation of concerns between controller, facade, core game logic, networking, and UI components.

---

### 4.2.6 Portability

**NFR-16 Java Runtime Compatibility**
The system shall run in the Java environment defined by the project configuration and dependencies.

---

## 5. Traceability Matrix

| User Requirement                | Supporting System Requirements                                         |
| ------------------------------- | ---------------------------------------------------------------------- |
| UR-01 Start a Game              | FR-01, FR-03, FR-05, FR-46                                             |
| UR-02 Join a Game               | FR-02, FR-04                                                           |
| UR-03 Supported Player Count    | FR-03                                                                  |
| UR-04 View Game State           | FR-34, FR-35, FR-37                                                    |
| UR-05 Play Cards                | FR-06, FR-07, FR-10, FR-11, FR-12, FR-13                               |
| UR-06 Draw and End Turn         | FR-09, FR-27, FR-28, FR-29                                             |
| UR-07 Pay Debts                 | FR-17, FR-18                                                           |
| UR-08 Trade with Other Players  | FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26                 |
| UR-09 Receive Error Feedback    | FR-15, FR-16, NFR-07                                                   |
| UR-10 Access Instructions       | FR-36, NFR-03                                                          |
| UR-11 See Game Result           | FR-30, FR-31, FR-32, FR-33                                             |
| UR-12 Continue After Disconnect | FR-38, FR-39, FR-40, FR-41, FR-42, FR-43, FR-44, FR-45, NFR-06, NFR-09 |

---

## 6. Summary of Functional and Non-Functional Requirements

### 6.1 Functional Requirements Identified

The functional requirements specify what the system does, including:

* session creation,
* joining and initialization,
* turn management,
* card drawing and play,
* payment handling,
* simplified trading,
* hand-limit enforcement,
* win detection,
* state synchronization,
* reconnect handling,
* and result display.

These are captured in **FR-01 to FR-46**.

### 6.2 Non-Functional Requirements Identified

The non-functional requirements specify the quality constraints on the system, including:

* usability,
* learnability,
* performance,
* responsiveness,
* robustness,
* state integrity,
* privacy,
* maintainability,
* modifiability,
* architectural separation,
* and portability.

These are captured in **NFR-01 to NFR-16**.
