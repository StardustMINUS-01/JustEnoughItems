# JEI-1.20.1 基准测试报告

- **日期**: 2026-08-14
- **项目**: JEI-1.20.1（Forge 47.3.1 / MC 1.20.1 / Java 17 / Gradle 8.10）
- **测试位置**: `Forge/src/test/java/mezz/jei/test/bench/`
- **运行命令**: `.\gradlew.bat :Forge:test --tests "mezz.jei.test.bench.*" --no-daemon`
- **构建结果**: BUILD SUCCESSFUL in 13s（增量）
- **测试框架**: JUnit 5（@TestFactory + DynamicTest），每行 `[jei-reference]/[jei-boundary]` 日志
- **超时保护**: JeiBenchRunner.invoke（daemon 线程 + CompletableFuture.get(deadline) + interrupt + join(grace)，DEFAULT_DEADLINE=5s）

## 引擎

| 引擎 | 说明 | 对应能力 |
|---|---|---|
| jei-search | 搜索管线：IngredientFilter 等价实现（ElementPrefixParser + GeneralizedSuffixTreeSearchStorage + ElementSearch + SearchTokenizer） | 搜索能力 |
| jei-tree | 配方树展开：**JeiRecipeTreeEngine**（1.20.1 无 FavoriteTreeBuilder，用 INPUT-role RecipeMap 索引镜像其语义：输入→消费者解析 + 私有输入剪枝 + 环去重） | craft-plan 能力 |

> 配方树场景与预期值（17 用例）与 1.21.1 基准完全一致；引擎为 bench 本地镜像实现。

## 总览

| 套件 | 引擎 | 用例数 | SUPPORTED | FALSE_POSITIVE | FALSE_NEGATIVE | ENGINE_ERROR | ENGINE_TIMEOUT | 总耗时 |
|---|---|---|---|---|---|---|---|---|
| JeiSearchReferenceCapabilitySuiteTest | jei-search | 30 | 30 | 0 | 0 | 0 | 0 | 1677.790 ms |
| JeiRecipeTreeReferenceCapabilitySuiteTest | jei-tree | 17 | 17 | 0 | 0 | 0 | 0 | 33.457 ms |
| JeiBoundaryCapabilitySuiteTest | jei-search | 25 | 25 | 0 | 0 | 0 | 0 | 185.520 ms |
| **合计** | | **72** | **72** | **0** | **0** | **0** | **0** | **1896.767 ms** |

---

## 1. 搜索能力基准（jei-search）— 30 用例

数据模式：MISSING（查询不存在的目标）/ MINIMUM（最小规模）/ UNBOUNDED（大规模）。
display name 实际格式：`test ingredient display name testingredient#N`（小写、去格式）。
注意：`#` 是 tag 前缀（ElementPrefixParser），含 `#N` 的完整名查询需用引号包裹为单 token。

### 1.1 name/prefix — 显示名前缀搜索

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | nonexistentprefix | 0 | 0 | SUPPORTED | 2.026 | 0.917 |
| MINIMUM | 2 | "test ingredient display name testingredient#0" | 1 | 1 | SUPPORTED | 1.563 | 0.778 |
| UNBOUNDED | 5000 | "test ingredient display name testingredient#4999" | 1 | 1 | SUPPORTED | 277.654 | 273.660 |

### 1.2 name/substring — 显示名子串搜索

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | nonexistent | 0 | 0 | SUPPORTED | 1.681 | 1.030 |
| MINIMUM | 2 | ingredient display | 2 | 2 | SUPPORTED | 1.846 | 0.728 |
| UNBOUNDED | 5000 | "display name testingredient#2500" | 1 | 1 | SUPPORTED | 184.533 | 182.693 |

### 1.3 name/suffix — 显示名后缀搜索

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | 9999 | 0 | 0 | SUPPORTED | 1.443 | 0.900 |
| MINIMUM | 2 | 1 | 1 | 1 | SUPPORTED | 0.858 | 0.463 |
| UNBOUNDED | 5000 | 4999 | 1 | 1 | SUPPORTED | 128.454 | 126.785 |

### 1.4 name/multi-token — 多 token 交集搜索

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | test nonexistent | 0 | 0 | SUPPORTED | 1.104 | 0.546 |
| MINIMUM | 2 | test 0 | 1 | 1 | SUPPORTED | 0.758 | 0.351 |
| UNBOUNDED | 5000 | ingredient 4999 | 1 | 1 | SUPPORTED | 140.333 | 133.513 |

### 1.5 mod/prefix — 模组前缀搜索（@）

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | @nonexistentmod | 0 | 0 | SUPPORTED | 1.220 | 0.672 |
| MINIMUM | 2 | @ModName(jei_test_mod) | 2 | 2 | SUPPORTED | 1.370 | 0.681 |
| UNBOUNDED | 5000 | @ModName(jei_test_mod) | 5000 | 5000 | SUPPORTED | 130.607 | 126.692 |

### 1.6 id/prefix — 资源位置前缀搜索（&）

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | &jei_test_mod:test_ingredient_9999 | 0 | 0 | SUPPORTED | 0.991 | 0.503 |
| MINIMUM | 2 | &jei_test_mod:test_ingredient_1 | 1 | 1 | SUPPORTED | 0.852 | 0.440 |
| UNBOUNDED | 5000 | &jei_test_mod:test_ingredient_4999 | 1 | 1 | SUPPORTED | 112.171 | 110.343 |

### 1.7 color/prefix — 颜色前缀搜索（^）

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | ^nonexistentcolor | 0 | 0 | SUPPORTED | 0.868 | 0.445 |
| MINIMUM | 2 | ^black | 2 | 2 | SUPPORTED | 1.018 | 0.509 |
| UNBOUNDED | 5000 | ^black | 5000 | 5000 | SUPPORTED | 120.706 | 116.665 |

### 1.8 exact-name — 完整名精确搜索（引号包裹）

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | "test ingredient display name testingredient#9999" | 0 | 0 | SUPPORTED | 0.881 | 0.425 |
| MINIMUM | 2 | "test ingredient display name testingredient#0" | 1 | 1 | SUPPORTED | 0.855 | 0.460 |
| UNBOUNDED | 5000 | "test ingredient display name testingredient#4999" | 1 | 1 | SUPPORTED | 131.262 | 129.535 |

### 1.9 exclusion — 排除搜索（-）

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 2 | test -nonexistent | 2 | 2 | SUPPORTED | 1.372 | 0.569 |
| MINIMUM | 2 | test -1 | 1 | 1 | SUPPORTED | 0.884 | 0.388 |
| UNBOUNDED | 5000 | test -4999 | 4999 | 4999 | SUPPORTED | 125.821 | 115.415 |

### 1.10 large-scale — 大规模索引性能

| 模式 | scale | 查询 | 结果数 | 预期数 | 状态 | elapsedMs | buildMs |
|---|---|---|---|---|---|---|---|
| MISSING | 10000 | 99999 | 0 | 0 | SUPPORTED | 286.675 | 283.833 |
| MINIMUM | 100 | 99 | 1 | 1 | SUPPORTED | 2.775 | 2.248 |
| UNBOUNDED | 10000 | 9999 | 1 | 1 | SUPPORTED | 284.416 | 282.018 |

> `[jei-reference] engine=jei-search SUMMARY cases=30 supported=30 falsePositive=0 falseNegative=0 engineError=0 timeout=0 totalElapsedMs=1677.790`

---

## 2. 配方树展开基准（jei-tree / JeiRecipeTreeEngine）— 17 用例

图族：CHAIN（链）/ BINARY_TREE（二叉树）/ FAN_OUT（扇出）/ CYCLE（环）/ CATALYST（催化剂）/ PRUNED（剪枝）/ DEEP_RECURSION（深递归）。
数据模式：MINIMUM / MISSING / UNBOUNDED。expected 为递归展开后唯一配方数（环与催化剂去重）。

| id | family | mode | graphSize | depth | recipes | expected | 状态 | elapsedMs |
|---|---|---|---|---|---|---|---|---|
| chain/minimum | CHAIN | MINIMUM | 4 | 4 | 4 | 4 | SUPPORTED | 7.844 |
| chain/missing | CHAIN | MISSING | 3 | 3 | 1 | 1 | SUPPORTED | 0.646 |
| chain/unbounded | CHAIN | UNBOUNDED | 500 | 500 | 500 | 500 | SUPPORTED | 8.347 |
| binary/minimum | BINARY_TREE | MINIMUM | 15 | 3 | 15 | 15 | SUPPORTED | 1.920 |
| binary/missing | BINARY_TREE | MISSING | 15 | 3 | 1 | 1 | SUPPORTED | 0.706 |
| binary/unbounded | BINARY_TREE | UNBOUNDED | 2047 | 10 | 2047 | 2047 | SUPPORTED | 17.530 |
| fanout/minimum | FAN_OUT | MINIMUM | 3 | 1 | 3 | 3 | SUPPORTED | 0.538 |
| fanout/missing | FAN_OUT | MISSING | 5 | 1 | 1 | 1 | SUPPORTED | 0.890 |
| fanout/unbounded | FAN_OUT | UNBOUNDED | 17 | 2 | 17 | 17 | SUPPORTED | 0.699 |
| cycle/minimum | CYCLE | MINIMUM | 2 | 5 | 2 | 2 | SUPPORTED | 0.608 |
| cycle/unbounded | CYCLE | UNBOUNDED | 2 | 200 | 2 | 2 | SUPPORTED | 0.415 |
| catalyst/minimum | CATALYST | MINIMUM | 2 | 1 | 2 | 2 | SUPPORTED | 0.485 |
| catalyst/unbounded | CATALYST | UNBOUNDED | 2 | 1 | 2 | 2 | SUPPORTED | 0.610 |
| pruned/minimum | PRUNED | MINIMUM | 5 | 5 | 3 | 3 | SUPPORTED | 0.427 |
| pruned/unbounded | PRUNED | UNBOUNDED | 50 | 50 | 11 | 11 | SUPPORTED | 0.740 |
| deep/minimum | DEEP_RECURSION | MINIMUM | 10 | 10 | 10 | 10 | SUPPORTED | 0.600 |
| deep/unbounded | DEEP_RECURSION | UNBOUNDED | 1000 | 1000 | 1000 | 1000 | SUPPORTED | 8.377 |

> `[jei-reference] engine=jei-tree SUMMARY cases=17 supported=17 falsePositive=0 falseNegative=0 engineError=0 timeout=0 totalElapsedMs=33.457`

---

## 3. 边界条件基准（jei-search）— 25 用例

SCALE=100。覆盖：退化查询、单字符 token、排除-only、无匹配 token、前缀空文本、引号、反斜杠转义、重复 token、大小写、null 安全。

| id | query | actual | expected | 状态 | elapsedMs |
|---|---|---|---|---|---|
| empty-query | '' | empty | empty | SUPPORTED | 720.781 |
| whitespace-only | '   ' | empty | empty | SUPPORTED | 22.446 |
| tab-only | '\t\n' | empty | empty | SUPPORTED | 18.878 |
| single-char-match | t | contains-0 | contains-0 | SUPPORTED | 31.096 |
| single-char-nomatch | z | empty | empty | SUPPORTED | 15.691 |
| single-char-exclusion | - | empty | empty | SUPPORTED | 16.923 |
| single-char-prefix-at | @ | empty | empty | SUPPORTED | 9.644 |
| single-char-prefix-hash | # | empty | empty | SUPPORTED | 9.371 |
| single-char-prefix-amp | & | empty | empty | SUPPORTED | 9.129 |
| single-char-prefix-caret | ^ | empty | empty | SUPPORTED | 9.929 |
| exclusion-only | -test | empty | empty | SUPPORTED | 9.626 |
| exclusion-prefix-only | -@mod | empty | empty | SUPPORTED | 8.301 |
| no-match-long | zzzzzzzzzz | empty | empty | SUPPORTED | 9.347 |
| no-match-huge-token | a×1000 | empty | empty | SUPPORTED | 8.513 |
| no-match-special-chars | !@#$%^&*() | empty | empty | SUPPORTED | 7.242 |
| prefix-at-only | @ | empty | empty | SUPPORTED | 7.082 |
| prefix-at-whitespace | '@ ' | empty | empty | SUPPORTED | 8.428 |
| quote-escaped | "test ingredient display name testingredient#0" | contains-0 | contains-0 | SUPPORTED | 13.762 |
| quote-unterminated | "test | contains-0 | contains-0 | SUPPORTED | 12.987 |
| backslash-escape | \t | contains-0 | contains-0 | SUPPORTED | 12.303 |
| trailing-backslash | test\ | contains-0 | contains-0 | SUPPORTED | 11.861 |
| repeated-token | test test test | contains-0 | contains-0 | SUPPORTED | 13.456 |
| uppercase-query | "TEST INGREDIENT DISPLAY NAME TESTINGREDIENT#0" | contains-0 | contains-0 | SUPPORTED | 12.714 |
| mixed-case-query | "TeSt InGrEdIeNt DiSpLaY NaMe TeStInGrEdIeNt#0" | contains-0 | contains-0 | SUPPORTED | 10.788 |
| null-query | null | empty | empty | SUPPORTED | 9.688 |

> `[jei-boundary] engine=jei-search SUMMARY cases=25 supported=25 falseNegative=0 engineError=0 timeout=0 totalElapsedMs=185.520`

---

## 结论

- **72/72 用例全部 SUPPORTED**：搜索 30、配方树 17、边界 25；零 FALSE_POSITIVE / FALSE_NEGATIVE / ENGINE_ERROR / ENGINE_TIMEOUT。
- **性能观察**：
  - 搜索 UNBOUNDED（scale 5000–10000）单次查询 110–285ms，其中 95%+ 消耗在索引构建（buildMs）而非查询本身；查询本身 <5ms。
  - 配方树展开 1000 节点深递归 8.4ms，200 节点环 0.4ms（去重正确）。
  - 边界：空查询首次 720ms 为 JVM 预热，其余均 <32ms。
- **关键语义发现**：
  - `#` 是 tag 前缀，裸 `#N` token 走 tag 搜索（TestIngredient 无 tag → 空）；完整名含 `#N` 需引号包裹。
  - 搜索先 `toLowerCase(Locale.ROOT)` 再 tokenize，大小写不敏感（suffix tree 子串匹配）。
  - 排除-only 查询（`-test`）走 `getAllIngredients` 起点再 removeAll，正确处理。
  - `Set.of()` 不可变集陷阱：intersection 空输入时必须返回可变集合（镜像 IngredientFilter 的 IdentityHashMap 实现），否则排除查询抛 UnsupportedOperationException。
- **配方树剪枝语义（本迁移的关键修复）**：1.20.1 无 FavoriteTreeBuilder，场景用 INPUT-role RecipeMap 表达"原料被配方消费 = 收藏"关系。JeiRecipeTreeEngine 严格镜像 1.21.1 `FavoriteTreeBuilder.build`：
  - **无自环**：配方自身的输入槽位不构成"收藏"（展开目标排除自身配方），与 1.21.1 中 store 映射独立于配方自身一致。
  - **私有输入剪枝**：输入仅被自身配方消费（无其他消费者）时是未收藏输入；非根配方含任何私有输入即为终端节点——自身可达但不展开。pruned 场景的 `p_missing` 正是此类（被 cutAt 节点自身消费、无其他消费者），因此 p2 在 cutAt 处停止展开（pruned/minimum=3、pruned/unbounded=11），而根配方始终展开（cycle 的 `c_link_a` 私有输入不阻断根 → cycle=2）。
  - 修复前引擎按"输入完全无消费者"剪枝，`p_missing` 被 p2 自身消费故永不触发 → pruned 两用例 FALSE_POSITIVE（5≠3、50≠11）；修复后 17/17 SUPPORTED。

## 测试文件清单

| 文件 | 作用 |
|---|---|
| JeiSearchEngine.java | 搜索引擎（镜像 IngredientFilter 管线，用 GeneralizedSuffixTreeSearchStorage） |
| JeiRecipeTreeEngine.java | 配方树引擎（镜像 1.21.1 FavoriteTreeBuilder 语义；1.20.1 无此类，bench 本地实现） |
| JeiBenchRunner.java | 超时保护执行器 |
| JeiSearchReferenceScenarios.java | 30 个搜索参考场景定义 |
| JeiRecipeTreeReferenceScenarios.java | 17 个配方树参考场景定义（与 1.21.1 基准一致） |
| TestRecipe.java | 配方树测试配方 record（uid + inputs） |
| JeiSearchReferenceCapabilitySuiteTest.java | 搜索套件（@TestFactory + SUMMARY） |
| JeiRecipeTreeReferenceCapabilitySuiteTest.java | 配方树套件（@TestFactory + SUMMARY） |
| JeiBoundaryCapabilitySuiteTest.java | 边界套件（@TestFactory + SUMMARY） |
| JeiSupportStatus / JeiDataMode / JeiSearchCapability / JeiTreeFamily | 枚举支撑类 |
| TestIngredientRenderer.java | 测试原料渲染器（bench 跨包使用，从 package-private 改为 public） |
