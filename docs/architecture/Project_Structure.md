# Project Structure

This document reflects the current Maven and JavaFX layout. Generated build output such as `target/`, local IDE metadata, and local tool notes are intentionally omitted.

```text
monopoly-deal-group6/
├── docs/
│   ├── architecture/
│   │   └── Project Structure.md
│   ├── diagrams/
│   │   ├── class/
│   │   │   ├── Application, Command & Facade Layer.png
│   │   │   ├── Core Domain.png
│   │   │   └── Network, UI & Exception.png
│   │   ├── sequence/
│   │   │   ├── Game Initialization Flow.png
│   │   │   ├── Happy Path.png
│   │   │   └── Network Synchronization.png
│   │   ├── use-case/
│   │       ├── Command & Input.png
│   │       ├── Game Engine & Rules.png
│   │       ├── Global Overview Diagram.png
│   │       └── Session & Network.png
│   ├── plan/
│   │   └── Project Plan.md
│   └── Game Requirements.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── ie/
│   │   │       ├── ucd/
│   │   │           ├── bdic/
│   │   │               ├── group6/
│   │   │                   ├── app/
│   │   │                   │   ├── AppConfig.java
│   │   │                   │   ├── AppStart.java
│   │   │                   │   └── GameSessionCoordinator.java
│   │   │                   ├── command/
│   │   │                   │   ├── AbstractCommand.java
│   │   │                   │   ├── Command.java
│   │   │                   │   ├── CommandType.java
│   │   │                   │   ├── CreateSessionCommand.java
│   │   │                   │   ├── DiscardCardsCommand.java
│   │   │                   │   ├── EndTurnCommand.java
│   │   │                   │   ├── JoinSessionCommand.java
│   │   │                   │   ├── PayDebtCommand.java
│   │   │                   │   ├── PlayCardCommand.java
│   │   │                   │   ├── PlayerDisconnectedCommand.java
│   │   │                   │   ├── ProcessDisconnectTimeoutsCommand.java
│   │   │                   │   ├── ProposeTradeCommand.java
│   │   │                   │   ├── RejoinSessionCommand.java
│   │   │                   │   ├── RespondActionCommand.java
│   │   │                   │   ├── RespondTradeCommand.java
│   │   │                   │   └── StartGameCommand.java
│   │   │                   ├── controller/
│   │   │                   │   ├── dto/
│   │   │                   │   │   └── ControllerRequest.java
│   │   │                   │   ├── mapper/
│   │   │                   │   │   └── RequestMapper.java
│   │   │                   │   └── GameController.java
│   │   │                   ├── core/
│   │   │                   │   ├── card/
│   │   │                   │   │   ├── deck/
│   │   │                   │   │   │   ├── Deck.java
│   │   │                   │   │   │   ├── DeckDefinition.java
│   │   │                   │   │   │   └── StandardDeckFactory.java
│   │   │                   │   │   ├── ActionCard.java
│   │   │                   │   │   ├── ActionType.java
│   │   │                   │   │   ├── Card.java
│   │   │                   │   │   ├── CardType.java
│   │   │                   │   │   ├── MoneyCard.java
│   │   │                   │   │   ├── PropertyCard.java
│   │   │                   │   │   └── PropertyWildCard.java
│   │   │                   │   ├── common/
│   │   │                   │   │   ├── OperationError.java
│   │   │                   │   │   └── OperationResult.java
│   │   │                   │   ├── engine/
│   │   │                   │   │   ├── ActionExecutor.java
│   │   │                   │   │   ├── ActiveGameState.java
│   │   │                   │   │   ├── GameEngine.java
│   │   │                   │   │   ├── GameSession.java
│   │   │                   │   │   ├── GameplayHandler.java
│   │   │                   │   │   ├── PendingAction.java
│   │   │                   │   │   ├── PendingDebt.java
│   │   │                   │   │   ├── PendingTrade.java
│   │   │                   │   │   ├── SessionSnapshotBuilder.java
│   │   │                   │   │   ├── SessionStatus.java
│   │   │                   │   │   └── TurnManager.java
│   │   │                   │   ├── player/
│   │   │                   │   │   ├── Bank.java
│   │   │                   │   │   ├── BankPaymentSolver.java
│   │   │                   │   │   ├── CardDiscardSink.java
│   │   │                   │   │   ├── Hand.java
│   │   │                   │   │   ├── Player.java
│   │   │                   │   │   ├── PlayerPropertyTransfers.java
│   │   │                   │   │   └── PlayerSnapshot.java
│   │   │                   │   ├── property/
│   │   │                   │   │   ├── PropertyBoard.java
│   │   │                   │   │   ├── PropertyColor.java
│   │   │                   │   │   ├── PropertyPaymentRule.java
│   │   │                   │   │   ├── PropertyRentRule.java
│   │   │                   │   │   ├── PropertySet.java
│   │   │                   │   │   └── RentTable.java
│   │   │                   │   ├── rules/
│   │   │                   │   │   ├── ActionRule.java
│   │   │                   │   │   ├── CardBankingRule.java
│   │   │                   │   │   ├── DrawPileRule.java
│   │   │                   │   │   ├── PaymentRule.java
│   │   │                   │   │   └── WinRule.java
│   │   │                   │   ├── snapshot/
│   │   │                   │       ├── CardSnapshot.java
│   │   │                   │       ├── PendingActionSnapshot.java
│   │   │                   │       ├── PendingDebtSnapshot.java
│   │   │                   │       ├── PendingTradeSnapshot.java
│   │   │                   │       ├── PlayerStateSnapshot.java
│   │   │                   │       ├── PropertyRowSnapshot.java
│   │   │                   │       └── SessionSnapshot.java
│   │   │                   ├── exception/
│   │   │                   │   ├── GameException.java
│   │   │                   │   ├── InvalidCommandException.java
│   │   │                   │   ├── InvalidGameStateException.java
│   │   │                   │   ├── PlayerNotFoundException.java
│   │   │                   │   └── SessionNotFoundException.java
│   │   │                   ├── facade/
│   │   │                   │   ├── handler/
│   │   │                   │   │   ├── AbstractCommandHandler.java
│   │   │                   │   │   ├── CommandHandler.java
│   │   │                   │   │   ├── CreateSessionHandler.java
│   │   │                   │   │   ├── DiscardCardsHandler.java
│   │   │                   │   │   ├── EndTurnHandler.java
│   │   │                   │   │   ├── JoinSessionHandler.java
│   │   │                   │   │   ├── PayDebtHandler.java
│   │   │                   │   │   ├── PlayCardHandler.java
│   │   │                   │   │   ├── PlayerDisconnectedHandler.java
│   │   │                   │   │   ├── ProcessDisconnectTimeoutsHandler.java
│   │   │                   │   │   ├── ProposeTradeHandler.java
│   │   │                   │   │   ├── RejoinSessionHandler.java
│   │   │                   │   │   ├── RespondActionHandler.java
│   │   │                   │   │   ├── RespondTradeHandler.java
│   │   │                   │   │   └── StartGameHandler.java
│   │   │                   │   ├── model/
│   │   │                   │   │   └── CommandResult.java
│   │   │                   │   ├── Facade.java
│   │   │                   │   ├── GameFacade.java
│   │   │                   │   └── PlayerTableFacade.java
│   │   │                   ├── network/
│   │   │                   │   ├── client/
│   │   │                   │   │   ├── ClientReceiver.java
│   │   │                   │   │   └── GameClient.java
│   │   │                   │   ├── message/
│   │   │                   │   │   ├── ClientRequest.java
│   │   │                   │   │   ├── Message.java
│   │   │                   │   │   ├── NetworkPayloads.java
│   │   │                   │   │   ├── NoticeType.java
│   │   │                   │   │   ├── ServerNotice.java
│   │   │                   │   │   ├── ServerResponse.java
│   │   │                   │   │   └── ServerUpdate.java
│   │   │                   │   ├── server/
│   │   │                   │   │   └── GameServer.java
│   │   │                   │   ├── NetworkAddressResolver.java
│   │   │                   │   └── NetworkConfig.java
│   │   │                   ├── ui/
│   │   │                       ├── GameUI.java
│   │   │                       ├── HowToPlayUI.java
│   │   │                       ├── LobbyUI.java
│   │   │                       ├── MenuUI.java
│   │   │                       ├── MessageUI.java
│   │   │                       ├── ResultUI.java
│   │   │                       ├── SceneManager.java
│   │   │                       ├── UiDialogs.java
│   │   │                       ├── UiSnapshot.java
│   │   │                       └── UiTheme.java
│   │   ├── resources/
│   │       ├── css/
│   │       │   └── style.css
│   │       ├── fxml/
│   │       │   ├── game.fxml
│   │       │   ├── how-to-play.fxml
│   │       │   ├── lobby.fxml
│   │       │   ├── menu.fxml
│   │       │   └── result.fxml
│   │       ├── images/
│   │           ├── error.png
│   │           └── icon.png
│   ├── test/
│       ├── java/
│           ├── ie/
│               ├── ucd/
│                   ├── bdic/
│                       ├── group6/
│                           ├── core/
│                           │   ├── card/
│                           │   │   └── StandardDeckFactoryTest.java
│                           │   ├── engine/
│                           │   │   ├── ActionExecutorTest.java
│                           │   │   ├── DomainExceptionTypeTest.java
│                           │   │   └── TurnManagerTest.java
│                           │   ├── player/
│                           │   │   ├── BankPaymentSolverTest.java
│                           │   │   ├── HandDiscardTest.java
│                           │   │   ├── PlayerHouseSpendTest.java
│                           │   │   ├── PlayerPropertyTransfersTest.java
│                           │   │   └── PlayerSnapshotTest.java
│                           │   ├── property/
│                           │   │   ├── PropertyBoardRowIdTest.java
│                           │   │   ├── PropertyBoardTest.java
│                           │   │   ├── PropertyPaymentRuleTest.java
│                           │   │   ├── PropertyRentRuleTest.java
│                           │   │   ├── PropertySetTest.java
│                           │   │   └── RentTableTest.java
│                           │   ├── rules/
│                           │       ├── DrawPileRuleTest.java
│                           │       └── WinRuleTest.java
│                           ├── facade/
│                           │   └── GameFacadeTest.java
│                           ├── integration/
│                           │   └── NetworkIntegrationTest.java
│                           ├── ui/
│                               └── UiSnapshotTest.java
├── CONTRIBUTING.md
├── CONTRIBUTING.zh-CN.md
├── LICENSE
├── README.md
└── pom.xml
```

## Package Responsibilities

| Path | Responsibility |
| --- | --- |
| `app/` | Application startup and manual dependency wiring. |
| `command/` | Typed command objects and `CommandType` values for sessions, gameplay, payments, trades, discards, turns, and rejoin. |
| `controller/` | Request intake, request DTOs, and conversion from request data into command objects. |
| `core/card/` | Card hierarchy, deck definition, deck factory, action types, and action effect support. |
| `core/common/` | Shared operation-result and operation-error types used by domain logic. |
| `core/engine/` | Session lifecycle, turn management, active game state, pending debt/trade state, and action execution. |
| `core/player/` | Player hand, bank, property-transfer, payment-solver, and snapshot support. |
| `core/property/` | Property colors, property rows, rent tables, rent support, and payment support. |
| `core/rules/` | Validation rules for actions, payments, and winning. |
| `exception/` | Project-specific exception hierarchy. |
| `facade/` | Application facade, command handlers, and `CommandResult` response model. |
| `network/` | TCP client, server, and per-client game thread classes. |
| `ui/` | JavaFX screens, scene navigation, theme helpers, and message UI support. |

## Resources

JavaFX view definitions live in `src/main/resources/fxml/`, and the shared stylesheet lives in `src/main/resources/css/style.css`. There are no committed `images/` or `sounds/` resource directories at the moment.

## Tests

Tests live under `src/test/java/ie/ucd/bdic/group6/`. Current test coverage is organized around core card, engine, player, property, and rule behavior, plus facade tests. `testsupport/TestGameBuilder.java` provides helper methods for constructing focused unit-test fixtures.
