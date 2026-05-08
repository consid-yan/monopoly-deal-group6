# Contributing

Thank you for contributing to the Monopoly Deal Group 6 project.

> For a Chinese summary, see [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md).

## 1. General Rules

- All code changes must go through a Pull Request.
- Do not push directly to the `main` branch.
- Keep changes focused and small when possible.
- Discuss major changes before implementation if they affect multiple modules.

## 2. Branch Naming

Use the following branch naming format:

- `feat/<issue-id>-<short-name>`
- `fix/<issue-id>-<short-name>`
- `docs/<topic>`
- `refactor/<topic>`
- `test/<topic>`

Examples:
- `feat/12-card-factory`
- `fix/24-network-sync`
- `docs/readme-update`

## 3. Commit Messages

Please use the following format:

```text
type(scope): short description
```

Examples:
- `feat(core): add turn manager`
- `fix(network): prevent duplicate command`
- `test(card): add card factory tests`
- `docs(readme): update setup instructions`

Recommended types:
- `feat`
- `fix`
- `test`
- `docs`
- `refactor`

## 4. Pull Request Process

Before opening a PR, please check:

- The project builds successfully.
- Relevant tests pass.
- The code is formatted properly.
- The PR description explains what changed and why.

A PR should include:
- A short summary of the change
- How the change was tested
- Any known side effects or limitations

Before merging:
- At least one teammate should review the PR.
- CI must pass.

## 5. Code Style

Please follow these basic rules:

- Use PascalCase for class names.
- Use camelCase for method and variable names.
- Keep one class responsible for one clear purpose.
- Do not put game rules inside the UI layer.
- Do not let the `core` package depend on JavaFX.
- Do not let the `network` package update UI components directly.

## 6. Testing

Testing is required for core logic changes.

- Add or update unit tests when you change `core`.
- Add integration tests for important game flows.
- Add regression tests when fixing bugs.
- Make sure tests pass before submitting a PR.

Examples of important test areas:
- Turn switching
- Card factory creation
- Rent calculation
- Action card resolution
- Network message handling

## 7. Communication

If your change affects multiple modules, please discuss it in the Issue before implementing it.

This is especially important for:
- Network protocol changes
- Game state changes
- Major rule changes

## 8. Questions

If you are not sure how to contribute, just open an Issue.

Thank you for helping improve the project!
