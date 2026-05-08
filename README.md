# Monopoly Deal Group 6

[![CI](https://github.com/consid-yan/monopoly-deal-group6/actions/workflows/ci.yml/badge.svg)](https://github.com/consid-yan/monopoly-deal-group6/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-red)
![JavaFX](https://img.shields.io/badge/JavaFX-25-blue)
![Build](https://img.shields.io/badge/build-Maven-orange)
![Tests](https://img.shields.io/badge/tests-JUnit%205-brightgreen)

A Java and JavaFX implementation of the Monopoly Deal card game, developed for the **BDIC COMP2008J Software Engineering** module.

Monopoly Deal Group 6 is organized as a modular multiplayer card-game application. The project uses a layered architecture that separates application startup, request mapping, command dispatch, game-domain concepts, networking, and JavaFX presentation. This organization keeps the code approachable for contributors and makes gameplay, interface, and communication features easier to develop independently.

## Team Members

- [Mouxiang Yan](https://github.com/consid-yan)
- [Dongrui Gu](https://github.com/ordinaryman-dev)
- [Yiming Liu](https://github.com/24372325)
- [Tian Gao](https://github.com/sp3981)
- [Yilong Ma](https://github.com/MaYilongNick)

## Tech Stack

- Java 25
- JavaFX 25
- Maven
- JUnit 5
- GitHub Actions
- TCP/IP socket networking

## Highlights

- Architecture for a 2 to 5 player Monopoly Deal experience
- Command-based actions for starting games, playing cards, trading, and ending turns
- Layered controller and facade flow for clean request handling
- Core packages for cards, properties, rules, player state, and game sessions
- JavaFX packages for desktop presentation
- Client and server packages for multiplayer communication
- Maven-based build, dependency management, and test execution
- Continuous integration through GitHub Actions

## Requirements

- JDK 25 or later
- Maven 3.9 or later
- IntelliJ IDEA, Eclipse, VS Code, or another Java IDE

## Getting Started

Clone the repository:

```bash
git clone https://github.com/consid-yan/monopoly-deal-group6.git
cd monopoly-deal-group6
```

Build and run tests:

```bash
mvn clean test
```

Run the configured application entry point:

```bash
mvn javafx:run
```

## Running in an IDE

1. Open the project as a Maven project.
2. Use JDK 25 as the project SDK.
3. Wait for Maven dependencies to import.
4. Run `ie.ucd.bdic.group6.app.AppStart`.

## Project Structure

```text
src/main/java/ie/ucd/bdic/group6/
+-- app/          Application entry point and dependency wiring
+-- command/      Gameplay command interface and command records
+-- controller/   Request intake, DTOs, and request-to-command mapping
+-- core/         Cards, engine, players, properties, and rules
+-- exception/    Project-specific exception types
+-- facade/       Command dispatch facade, handlers, and result model
+-- network/      Client and server communication packages
+-- ui/           JavaFX UI packages
```

Tests live under `src/test/java/ie/ucd/bdic/group6/`.

Project documents live under `docs/`:

- [Game Requirements](docs/Game%20Requirements.md)
- [Project Structure](docs/architecture/Project%20Structure.md)
- [Project Plan](docs/plan/Project%20Plan.md)

## Architecture Overview

The application is organized around a command-driven flow:

1. `GameController` receives a `ControllerRequest`.
2. `RequestMapper` converts request data into command records such as `StartGameCommand`, `PlayCardCommand`, `EndTurnCommand`, and `TradeCommand`.
3. `GameFacade` routes each command to a matching `CommandHandler`.
4. Handlers return a `GameResult`.

This flow allows UI and networking layers to submit player actions through the same application boundary while keeping game execution centralized.

## Game Scope

The game design follows the Monopoly Deal card-game format and includes:

- Session creation and player joining
- Turn management
- Property, money, and action cards
- Payment validation
- Simplified trading rules for the course project
- Three full property sets as the win condition
- Multiplayer state synchronization
- Disconnect and rejoin handling
- JavaFX gameplay and how-to-play screens

## Media

Replace the placeholder image paths below with screenshots or demo assets.

### Main Menu

![Main Menu](./docs/media/main-menu.png)

### Gameplay

![Gameplay Screen](./docs/media/gameplay.png)

### How to Play

![How to Play Screen](./docs/media/how-to-play.png)

### Multiplayer Session

![Multiplayer Session](./docs/media/multiplayer-session.png)

## Useful Maven Commands

```bash
mvn clean test
mvn clean package
mvn javafx:run
```

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening issues or pull requests. A Chinese version is also available in [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md).

## License

This project is licensed under the terms of the [Apache License 2.0](LICENSE).

## Contact

For questions or suggestions, contact [Mouxiang Yan](mailto:mouxiang.yan@ucdconnect.ie).
