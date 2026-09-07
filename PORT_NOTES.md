# JEI 1.21.1 → 1.20.1 Fork Port — Notes

**Status**: 9/9 commits ported. Final jar built and deployed.

## Deployed jar
- `E:/JEI Unofficial/deployed/JEIunofficial-1.20.1-forge-15.48.0.181.jar` (2.7 MB)
- Source jar: `E:/JEI Unofficial/JEI-1.20.1/Forge/build/libs/JEIunofficial-1.20.1-forge-15.48.0.181.jar`

## Port sequence (9 commits)
1. `090f757b8` → `b501a9e93` — Improve bookmark and recipe preview interactions
2. `f726b0d11` → `5f80c0351` — Skip patterns already available on AE2 networks
3. `c5966c943` → `48f1f7f7f` — Preserve selected candidates across recipe interactions
4. `b07fd793e` → `5f523dbf8` — Add recipe search, preference filters, candidate previews
5. `00db9f3ec` → `4b0613f35` — Add recipe query session navigation
6. `aff241b51` → `8ecaa8d44` — Filter recipe candidates and open exact bookmarked recipes
7. `d95012587` → `da26abafe` — Simplify fork test infrastructure (test files skipped)
8. `b78bfa808` → `a67624948` — Add JECh support to recipe search
9. `4abe49002` → `b42a832c7` — Add bookmark group sharing, missing chains, terminal search

## Known limitations
- `RecipeGuiLogic`, `RecipesGui`, `RecipeGuiLayouts`, `JeiGuiStarter`, `RecipeLayoutProjection`-using bookmark/chain code: NOT FULLY PORTED. The 1.20.1 fork has its own structure for these; the surface is preserved (new methods on interfaces have defaults, new types have empty/no-op base impls), but the actual wiring of the new features into the 1.20.1 RecipesGui / RecipeGuiLogic is incomplete.
- `PacketShareBookmarkGroup`: NOT PORTED. 1.21.1 uses `PlayToServerPacket` base + `CustomPacketPayload` + `StreamCodec`; 1.20.1 uses `PacketJei` + `PacketIdServer` + `writePacketData`. Bookmark-group sharing client→server flow is disabled in 1.20.1; the parse/format helpers in `JeiChatItemLinks` are now available.
- `getRegisteredName()` (1.20.5+ Holder API) → `BuiltInRegistries.ITEM.getKey(item).toString()` in `JeiChatItemLinks`.
- Pattern switch (`switch (x) { case Foo f -> ... }`) → `if/else instanceof` for Java 17.
- `List.getFirst()` → `List.get(0)` for Java 17.
- `RecipeGuiLogic.previousRecipeCategory()` stays `void` (1.20.1 fork convention; 1.21.1 changed to `boolean`).
- 1.20.1's `IClientConfig` has `is*Enabled()` (no `.getValue()`) for booleans and `get*Enabled()` for collection getters — preserved.

## What works in 1.20.1
- All 252+ Gui unit tests pass.
- Forge jar builds successfully.
- New types available: `CandidateTooltipComponent`, `BookmarkCandidateTooltipHelper`, `RecipeFilterMode`, `RecipeSearchQuery` (with input terms + JECh matcher), `RecipeSearchIngredient`, `RecipeSearchIngredientFactory`, `FilteredRecipeSlotView`, `IRecipeSlotCandidateView`, `RecipeLayoutProjection` (with filtered candidates), `InputSlotSelectionState` (with applyCandidateFilter), `IRecipeSearchTextMatcher`, `RecipeLookupSnapshot` (with createSearchTextMatcher), `LookupStatePositionUtil`, `RecipeNavigationHistory/Direction/Entry/ButtonController`, `BookmarkFavoriteRecipePreviewState`, `ExternalIngredientSearchHandlerRegistry`.
- Lang en_us/zh_cn updated with all new keys from commits 4, 5, 9.
- 1.20.1 fork's own custom port mods (e.g. `9c4a7dc95 fix: bound BookmarkDisplayGenerator.nextSlotIndex`) preserved.


## Round: 4 new upstream commits on top of 4abe49002 (a65515356 + ba03979a9 + cc665f4d3 + 3eec0b8bc)

The 4 new commits on top of our previous port base (4abe49002) are NOT additive patches — they are a coordinated refactor of the bookmark core + a config-reload consolidation + a test-fixture simplification. They assume an int-typed groupId, a new codec-based serialization, a new WorldInputHandler, and a 1.21.1 InputEvent surface that 1.20.1 does not have. The fork's own BookmarkJsonSerializer (with WCWT freeze, Bug6 matching, alt+滚轮 gate) is built on the String-id world and would need to be rewritten alongside the upstream refactor.

### Committed in this round (3 partial commits, all additive only)

- d9b38055e: Port 1.21.1 3eec0b8bc (partial) — tooltip state consolidation + world target interface
  - Added 3 new keymapping getters (getBookmarkWorldTarget / getShowWorldTargetRecipe / getShowWorldTargetUses) to IInternalKeyMappings
  - Generalised BookmarkPermutationTooltipState.updateStart to take Object sourceKey (new overload, old int overload kept)
  - Added 4 en_us + 4 zh_cn translations for the world-target category and keys

- 13d2cd311: Port 1.21.1 3eec0b8bc (partial) — world target keymapping stubs
  - Registered the 3 new keymappings in InternalKeyMappings as unbound (visible in the Controls GUI but no behavior until the upstream WorldInputHandler is ported)

- 866bb61c1: Port 1.21.1 cc665f4d3 (partial) — tooltip input provider consolidation
  - BookmarkInputHandler / BookmarkOverlayRenderer: route tooltip rendering through the new PlayerInventoryRecipeChainTooltipInventoryProvider.getTooltipInventoryInputs(groupId) method (which itself returns [] when the open screen is RecipesGui), instead of inlining the screen-type check at every call site
  - PlayerInventoryRecipeChainTooltipInventoryProvider: added getTooltipInventoryInputs(groupId)
  - BookmarkList: missing-item creation now uses IngredientBookmark.createPreservingAmount so the per-stack quantity is propagated when the player inventory is the source

### Skipped (each requires the upstream int groupId cascade that we did not port)

- 3eec0b8bc bookmark refactor (BookmarkGroup / BookmarkItemMetadata String->int, BookmarkList 252 lines, BookmarkConfig → BookmarkConfigEntryCodec, BookmarkJsonConfig, BookmarkGroupConfigSerializer, BookmarkMoveSelection, BookmarkGroupMovePlan) — these are 1.21.1-intrinsic; the fork's BookmarkJsonSerializer is built on the String-id world and would need a full rewrite to host them, breaking saved-bookmark file format and re-introducing risk for the alt+滚轮 / WCWT freeze / Bug6 fixes.
- 3eec0b8bc world target actions (WorldInputHandler + ray-trace + new packet flow) — the keymapping stubs are in place; the actual handler pipeline depends on 1.21.1 InputEvent / Networking APIs.
- 3eec0b8bc NeoForge-only files (the fork is Forge-only).
- cc665f4d3 RecipeChainTooltipModel refactor (collectOrdinaryTargets / allocateOrdinaryTargets, 78 lines) — depends on BookmarkItemMetadata.amount() being a record component, on BookmarkIngredientKey having typedIngredient() + getCraftingAvailabilityKey(), and on BookmarkConfigEntryCodec (all 1.21.1-intrinsic). Without those, the 1.20.1 Item record (key, metadata, amount, sourceIndex) doesn't match the 1.21.1 shape.
- ba03979a9 (entire commit) — 20 files, all Gui + NeoForge test files. None are source-relevant.
- a65515356 source-relevant (CollapsibleRulesReloadController → generic ConfigRulesReloadController<T>, BookmarkList -341) — depends on BookmarkConfigEntry / BookmarkConfigEntryCodec which require the int groupId cascade.

### Verification

- gradle :Gui:test → 252 tests, 0 failures (unchanged)
- gradle :Forge:build → green
- spotlessCheck → green (run automatically)
