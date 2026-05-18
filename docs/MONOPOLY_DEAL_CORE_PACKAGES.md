# Monopoly Deal 核心包集成说明（card / property / player + 协作 API）

本文说明 `core.card`、`core.property`、`core.player` 以及 **`core.common`**（`OperationResult`）、**`core.api`**（`PlayerTableFacade`）的职责与推荐集成方式。

## 包职责与依赖方向

| 模块 | 职责 |
|------|------|
| **core.property** | `PropertyColor`、`RentTable`、`PropertySet`（单行套）、`PropertyBoard`（多行）、`PropertyRentSupport`（租金汇总只读工具）。 |
| **core.card** | 密封 `Card` 层次、`DeckDefinition`（牌组常量）、`StandardDeckFactory`、`Deck`、`CardBankSupport`（银行/支付语义）、`ActionEffect`（行动卡扩展点，引擎注册实现）。 |
| **core.player** | `Hand`、`Bank`、`Player`（聚合）、`PlayerSnapshot`（回滚）、`PlayerPropertyTransfers`（跨玩家地产搬运）、`BankPaymentSolver`（银行支付凑额）、`PlayerZoneListener`（变更通知）。 |
| **core.common** | `OperationResult` / `OperationError`：替代裸 `boolean`，便于 UI 提示。 |
| **core.api** | `PlayerTableFacade`：给引擎/UI 的稳定入口（薄封装 + JavaDoc）。 |

```text
core.api      → core.player → core.card / core.property
core.player   → core.card / core.property
core.card     → core.property（颜色元数据）
core.common   →（无业务依赖）
```

## 牌堆 `Deck`：顶 / 底语义（易错点）

内部 `Deque`：**队首 = 牌堆底，队尾 = 牌堆顶**。

- `new Deck(bottomToTop)`：`bottomToTop.get(0)` 在最底，`get(size-1)` 在顶。
- `draw()` / `tryDraw()`：从**队尾**抽（顶牌）。
- `discardToBottom(card)`：插入**队首**（整副牌最底下）。

## 创建 106 张牌并洗牌

张数常量见 [`DeckDefinition`](c:\Users\myl34\Desktop\monopoly-deal-group6\src\main\java\ie\ucd\bdic\group6\core\card\DeckDefinition.java)。

```java
import ie.ucd.bdic.group6.core.card.*;
import java.util.List;
import java.util.Random;

List<Card> cards = StandardDeckFactory.createUnshuffledDeck();
Deck deck = Deck.shuffled(cards, new Random());
deck.tryDraw().ifPresent(card -> { /* ... */ });
```

## 推荐：通过 `PlayerTableFacade` 操作玩家

```java
import ie.ucd.bdic.group6.core.api.PlayerTableFacade;
import ie.ucd.bdic.group6.core.player.Player;
import ie.ucd.bdic.group6.core.property.PropertyColor;
import ie.ucd.bdic.group6.core.property.PropertySet;

Player p = new Player("p1", "Alice");
PlayerTableFacade table = new PlayerTableFacade(p);
p.hand().add(someCard);

PropertySet row = p.propertyBoard().startNewRow(PropertyColor.RED);
table.playPropertyFromHand(row, redProperty, java.util.Optional.empty());
table.bankFromHand(someMoneyOrBankableAction);
```

底层等价调用：`Player.tryPlayPropertyFromHand`、`Player.tryBankFromHand` 等，均返回 **`OperationResult`**（含 `OperationError`）。

## 手牌上限（弃至 7 张）

```java
table.discardHandDownToSeven(List.of(cardToDiscard1, cardToDiscard2, ...), drawPile);
```

## 银行与「行动卡进银行后不能再当行动打出」

- **进银行**：`Bank.depositAsMoney` / `Player.tryBankFromHand` 会拒绝不能当钱的牌（如普通地产、无面值彩虹万能）。
- **语义**：`CardBankSupport.canUseBankedCardAsAction` 对任意在银行的牌恒为 **false**（引擎不得再从银行打出行动效果）。
- **支付**：`Player.tryPayBankCardsTo` 仅允许 `card.bankValueM()` 有值的牌；向收款方入账时走 `Bank.depositAsMoney` 并带完整回滚。

## 银行凑额（不找零：最小超额子集）

```java
var plan = BankPaymentSolver.suggestMinimalOverpaySubset(p.bank().view(), dueM);
plan.ifSuccess(list -> p.tryPayBankCardsTo(receiver, list));
```

## 地产跨玩家搬运（引擎组合事务）

不要在 `Player` 内写复杂多步事务；使用：

```java
PlayerPropertyTransfers.transferPropertyCardToRow(sender, receiver, card, receiverRow, wildChoice);
```

失败时返回明确 `OperationError`（如 `RECEIVER_ROW_REJECTS_CARD`、`ROLLBACK_FAILED`）。

## House / Hotel（必须先打出对应行动卡）

- **不要**直接调用 `PropertySet.tryPlaceHouseStructuralOnly()` 作为正常对局路径（仅结构占位，测试或快照恢复可用）。
- 对局路径： **`Player.trySpendHouseCardOnRow` / `trySpendHotelCardOnRow`**（从手牌消耗对应 `ActionCard`，成功后再结构建房）。
- 返回的 `ActionCard` 由引擎放入中央出牌区 / 弃牌堆。

## 胜利宣告辅助

`Player.canDeclareVictory(isMyTurn)`：仅当 **自己回合** 且已有 **3 种不同锚色** 的完整套时为 `true`（仍需引擎处理「何时允许宣告」的 UX）。

## 快照与 Just Say No 回滚

```java
PlayerSnapshot before = payer.snapshot();
// ... attempt action ...
if (cancelled) {
    payer.restore(before);
}
```

## UI / 网络：监听变更

```java
p.addZoneListener(new PlayerZoneListener() {
    @Override
    public void onHandChanged(Player player) { /* refresh */ }
});
```

## 测试辅助

[`TestGameBuilder`](c:\Users\myl34\Desktop\monopoly-deal-group6\src\test\java\ie\ucd\bdic\group6\testsupport\TestGameBuilder.java)（`src/test`）用于快速摆玩家状态。

## 万能卡支付说明

- **彩虹万能**：`bankValueM` 为空 → 不能当钱支付、不能进银行作钱（`CardBankSupport` / `Bank.depositAsMoney`）。
- **双色等带印刷面值万能**：可当钱（与部分文字说明冲突时，以代码与 `DeckDefinition` 为准，可按课程要求调整工厂）。

## 行动卡效果扩展

- 实现 `ActionEffect` 并在引擎侧按 `ActionType` 注册；`ActionContext` 刻意保持精简，由引擎注入会话对象。
- 使用 [`ActionEffectRegistry`](c:\Users\myl34\Desktop\monopoly-deal-group6\src\main\java\ie\ucd\bdic\group6\core\card\ActionEffectRegistry.java) 集中管理 `ActionType → ActionEffect` 映射，避免在多个 `switch` 中散落逻辑。

---

集成问题可在仓库内联系对应模块维护者。
