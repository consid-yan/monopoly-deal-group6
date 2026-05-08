# 贡献指南（中文速读版）

感谢你参与 Monopoly Deal Group 6 项目。

> 英文正式版请看 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 1. 基本规则

- 所有代码修改都必须通过 Pull Request 合并。
- 不要直接提交到 `main` 分支。
- 改动尽量保持小而集中，不要一次改太多内容。
- 如果改动会影响多个模块，先在 Issue 里讨论再开始做。

## 2. 分支命名

建议使用下面这些格式：

- `feat/<issue-id>-<short-name>`
- `fix/<issue-id>-<short-name>`
- `docs/<topic>`
- `refactor/<topic>`
- `test/<topic>`

示例：
- `feat/12-card-factory`
- `fix/24-network-sync`
- `docs/readme-update`

## 3. Commit 信息

建议使用下面格式：

```text
type(scope): short description
```

示例：
- `feat(core): add turn manager`
- `fix(network): prevent duplicate command`
- `test(card): add card factory tests`
- `docs(readme): update setup instructions`

常用 type：
- `feat`
- `fix`
- `test`
- `docs`
- `refactor`

## 4. Pull Request 流程

在提交 PR 前，请确认：

- 项目可以正常编译。
- 相关测试已经通过。
- 代码格式是正确的。
- PR 描述清楚改了什么、为什么改。

一个 PR 最好包含：
- 改动简述
- 测试方式
- 已知限制或副作用

合并前：
- 至少要有 1 位队友 review。
- CI 必须通过。

## 5. 代码风格

请遵守这些基本规则：

- 类名使用 PascalCase。
- 方法名和变量名使用 camelCase。
- 一个类尽量只负责一件事。
- 不要把游戏规则写进 UI 层。
- `core` 包不要依赖 JavaFX。
- `network` 包不要直接更新界面组件。

## 6. 测试要求

修改核心逻辑时必须补测试。

- 修改 `core` 时更新单元测试。
- 重要流程要写集成测试。
- 修 bug 时要补回归测试。
- 提交 PR 前确认测试全部通过。

重点测试内容：
- 回合切换
- 卡牌工厂创建
- 租金计算
- 行动卡结算
- 网络消息处理

## 7. 沟通

如果你的改动会影响多个模块，请先在 Issue 里讨论再实现。

尤其是这些内容：
- 网络协议修改
- 游戏状态修改
- 重要规则修改

## 8. 不确定怎么办

如果你不知道怎么贡献代码，可以开 Issue 或在群里问队友。

感谢你帮助完善这个项目！