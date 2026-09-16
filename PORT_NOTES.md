# JEI 1.21.1 → 1.20.1 Fork Port — Notes

Status updated on 2026-09-16 for the current local working tree on `stardust/1.21.1-port-to-1.20.1`. The identified gaps in this port review are implemented. Spotless, 471 tests (Common: 75, Gui: 263, Forge: 133), and the Forge build passed. The maintainer reports that acceptance testing passed for the reviewed scope, including the outstanding in-game and multiplayer checks. This does not establish complete feature parity with 1.21.1 or compatibility with every mod combination.

## Historical port sequence (9 commits)

These mappings describe earlier port attempts, not completion of every feature in their commit messages.
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
- Bookmark-group shares are limited to 30,000 Base64 characters to fit the 1.20.1 serverbound custom-payload limit. This is narrower than the 1.21.1 snapshot limit; larger groups are not sent.

## Bookmark and terminal integration (2026-09-16, local changes)

- Alt+V over a blue bracket saves the inventory-adjusted missing materials as a new blue-bracket group. It follows the existing pull-items binding and uses the same inventory provider as the chain tooltip.
- Ctrl+L over a group bracket shares the group; clicking the chat link imports it under a fresh group ID. Import validates and resolves all entries before changing bookmarks, retaining quantities, selected ingredients, recipe metadata, collapsed recipes, and group display state.
- Sharing uses an optional Forge channel, detected independently of the base JEI channel. Clients without this feature receive plain group labels without import commands; the channel does not require JEIU on every client.
- Ingredient bookmarks now have group-local equality, including group moves, candidate replacement, and reload. Explicit ingredient amounts are persisted alongside the existing 1.20.1 JSON metadata.
- Ctrl+G searches the hovered ingredient in AE2 storage and pattern-access terminals. The existing AE2 presence guard and ghost-search target are reused; simulation does not edit the search field.
- Extended the existing `BookmarkConfigTest` for group import/reload, quantities and NBT, rejection without partial mutation, and missing-group isolation. No new test file was added.
- Maintainer acceptance passed for missing-material saving, group sharing/import and mixed-client multiplayer, terminal search, and operation without AE2.

## Recipe-page integration (2026-09-16, local changes)

- Connected recipe search, preference filters, search-driven input candidates, and empty-result rendering to the recipe page. Search uses the registered search-storage factory, including search aliases supplied by plugins.
- Connected query back/forward buttons, Shift endpoint navigation, and Alt+Left/Right navigation. Sessions retain filters, search text, category/page position, and captured input selections; closing the page clears the session.
- Kept the existing 1.20.1 `IRecipeGuiLogic` field, `Stack<ILookupState>` back history, `IRecipeTransferManager`, layout factory, and container-aware layout signatures. Forward history and session metadata extend those fields instead of replacing them with 1.21.1 implementations. Sessions are bounded to 128 queries.
- The current 1.20.1 API does not expose `getRecipeIngredients`. Search snapshots read unfiltered slots through its existing `createRecipeLayoutDrawable` API, only when filtering is requested. Only the active query's search snapshot is retained; it is cleared on query changes and screen removal.
- Extended `RecipesGuiTest` for preference/search projection, registered search aliases shared with candidate filtering, filtered paging, and empty results. Automated verification passed, and the maintainer reports acceptance of the recipe-page integration.

## Inventory giving and grouping

- Restored `ServerCommandUtil.giveToInventory` to the official 1.20.1 implementation. Inventory-mode cheating now calls the separate `fillInventory` method, preserving the existing main-inventory filling and slot synchronization behavior. Maintainer acceptance passed.
- Cross-page grouping, zero default recipe multipliers when quantities are not preserved, and bracket dragging without waiting after leaving the starting row are already present in 1.20.1; they are not outstanding port gaps.

## Platform adaptations

- `getRegisteredName()` (1.20.5+ Holder API) → `BuiltInRegistries.ITEM.getKey(item).toString()` in `JeiChatItemLinks`.
- Pattern switch (`switch (x) { case Foo f -> ... }`) → `if/else instanceof` for Java 17.
- `List.getFirst()` → `List.get(0)` for Java 17.
- `RecipeGuiLogic.previousRecipeCategory()` stays `void` (1.20.1 fork convention; 1.21.1 changed to `boolean`).
- 1.20.1's `IClientConfig` has `is*Enabled()` (no `.getValue()`) for booleans and `get*Enabled()` for collection getters — preserved.

## Scope distinctions

- Candidate previews and selection have callers in bookmark and recipe-tree tooltips, favorite input restoration, layout projections, and the recipe-page search integration above.
- Bookmark group IDs are already integers. Storage still uses the 1.20.1 implementation rather than the complete 1.21.1 `BookmarkJsonConfig` / `BookmarkConfigEntryCodec` structure. Save/reload behavior passed acceptance; this does not imply cross-version file-format equivalence.
- AE2 pattern encoding has a network-pattern lookup and filtering path. Maintainer acceptance passed for the tested environment, not every AE2 and extension version combination.
- Recipe-chain iteration optimization remains deferred on the 1.21.1 `perf/recipe-chain-iterator` experimental branch. It is not merged into either mainline and is not an outstanding gap in this port.
