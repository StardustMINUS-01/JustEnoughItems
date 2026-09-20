# JEI 1.21.1 → 1.20.1 Fork Port — Notes

## Bookmark candidate restoration and deletion (2026-09-20, uncommitted)

- Sidebar candidate scrolling now uses the same saved-input selection transaction as the candidate popup. It retains quantities and does not invent candidates for ordinary item bookmarks by looking up unrelated recipe context.
- Candidate previews and selection restore saved item/fluid snapshots through the existing ingredient-key resolver. Fluid snapshots use SNBT fields `FluidName`, `Amount` and optional `Tag`, inside the existing JSON `ingredient` field. Item snapshots and snapshot-free legacy entries keep their existing format; there is no high-version Codec dependency. Older saves that never recorded a fluid variant cannot recover its missing NBT automatically.
- The bookmark key (A by default) on an entry in a collapsed recipe closure removes the whole collapsed block, without removing unrelated entries or other groups. Expanded tree editing still removes individual entries. The same interaction is implemented in 1.21.1.
- Existing tests exercise scroll/popup equivalence after JSON round-trip, quantity preservation, fluid snapshot restoration and malformed-data rejection, and the single/whole/expanded deletion boundaries. In-game acceptance remains pending.

## Selection, transfer and layout parity (2026-09-20, uncommitted)

- Recipe-tree insertion keeps its button enabled independently of container transfer support and passes selected inputs plus filtered candidates into the tree.
- Recipe transfer checks, actual transfers and error overlays use the selected/filtered slot projection. Existing upstream service methods and the original controller constructor remain unchanged; the additional projection entry points share the upstream transfer lifecycle.
- Selected AE2 crafting-pattern transfers use the 15.4.10 `appeng.integration.modules.jeirei.EncodingHelper` signature, verified from the local dependency. The generic transfer path remains available for other recipe/container types.
- Single-column bookmark layouts place consecutive recipe entries without the old capped scanning loop. Favorite output-slot actions use the displayed ingredient. Bookmark/tree previews retain saved candidate boundaries, and filtered slot tag information is recomputed from the remaining candidates.
- Show Bookmark Details has its own configurable binding (left Shift by default, left Super on macOS), separate from recipe cycling/pinning. Modifier-key display names use translation keys.
- Existing regression tests now assert exact single-column positions and candidate/output/transfer projection behavior rather than only termination. In-game recipe-tree insertion, AE2 selected-pattern transfer and customized keybindings still require acceptance testing.

## Crafting and ingredient identity parity (2026-09-20, uncommitted)

- Ingredient-key equality, hashing and ordering now use JEI ingredient type and UID, not the saved stack snapshot. The known `item_stack` spelling is normalized to `minecraft:item_stack`. Snapshots remain available for restoring saved ingredients. Inventory matching no longer infers an item type from legacy/unknown keys or drops subtypes outside the existing relaxed namespaces; old entries with genuinely missing type information may need to be recreated.
- Crafting requests carry the selected recipe ID to the existing executor interface. A new optional `jei:bookmark_crafting` channel and packet ID distinguish the new format on Forge and Fabric. Servers without that capability are not sent the new requests. The old packet ordinal remains reserved but has no decoder; old-client crafting requests are not reinterpreted as the new format.
- Chain crafting follows the 1.21.1 ACK/inventory state machine: each waiting stage allows 40 ticks, and a failed ACK or expired wait stops the task rather than skipping a confirmed-but-unsynchronized recipe and continuing. Matching client/server builds are required for this crafting extension.
- AE2 15.4.10 was inspected locally and provides `IClientRepo.getByIngredient(Ingredient)`. Active tasks query their relevant input/output item kinds and bypass the ordinary 200 ms snapshot cache. This narrows returned data without claiming a complexity guarantee for AE2's internal implementation. Scope construction is proportional to the task's recipe slots, with O(U) retained item kinds for that task only; completion, failure, cancellation and runtime shutdown clear it. No persistent full-network index was added.
- Existing tests cover key identity, subtype separation, multi-output chains, ACK/sync timeout, cancellation, scope cleanup and recipe IDs in outgoing packets. In-game AE2 and mixed-version acceptance remain pending.

## Favorite-tree parity (2026-09-20, uncommitted)

- Favorite-tree inputs now exclude existing GTM virtual non-consumable projections through the shared compatibility adapter.
- Root choices, saved choices and single-candidate inputs use the existing manual/generated favorite resolver, retaining manual priority and build-local layout reuse. Unused relaxed manual-only lookups were removed.
- Favorite grid and recipe-row targets prefer saved typed ingredients. The existing 1.20.1 item SNBT snapshots are restored when needed, including after reloading favorites; generated preference resolution shares this restoration path. Bookmark equality, inventory matching and the save format are unchanged.
- In-game verification of GTM recipe trees, automatic preference expansion and saved NBT targets remains pending.

## Chat sharing and NBT rules (2026-09-20, uncommitted)

- Forge's optional bookmark-sharing channel now advertises revision 2. Item/fluid and recipe shares require revision 2 on the server; recipients without it receive plain readable labels. Revision 1 group sharing remains supported. These new sharing capabilities are Forge integrations; the Fabric build is checked but does not advertise this channel.
- Item shares contain the saved stack NBT, including Forge capability data, with the count encoded separately to avoid the vanilla byte-count limit. Fluid shares preserve quantity and tag data. Binary snapshots are limited to 16 KiB before Base64 encoding, below the 1.20.1 serverbound payload limit. Unsupported ingredients and oversized/invalid data are not replaced with UID-only shares.
- Right-clicking the recipe-page bookmark button shares its recipe type and ID. The recipient resolves that exact local recipe. Chat recipe previews omit the tooltip background; holding Shift fixes recipe or ingredient previews for R/U lookups, bookmarking, and copy shortcuts. Runtime shutdown clears preview references.
- Rules support `nbt:{...}` on item/fluid tag data. Objects match without regard to key order; extra fields or list elements fail unless covered by the pattern. List order and numeric NBT types remain significant. `*` matches any value, and wildcards work in field names and quoted strings; `\*` matches a literal star. No component compatibility alias is introduced.
- Ctrl+S copies `item = <id> & nbt:{...}` for the hovered item, escaping literal stars. It copies the item's tag only, not the saved-stack envelope or Forge capability envelope. There is no full/default-component switch on 1.20.1.
- NBT-specific grouping is evaluated on demand. The existing classification cache stores only kind, ID and tag IDs, never stack NBT variants; it is discarded when rules change. Chat caches retain only the currently hovered ingredient and recipe preview and clear on screen/runtime changes.
- Dedicated-server and automated verification do not replace mixed-client multiplayer and in-game preview acceptance; those checks remain pending.

## Sidebar and interaction parity (2026-09-19, uncommitted)

- Collapsible rules now coexist in `config/jei/collapsible-items/`. Bracket-prefixed filenames take priority, with filename ordering within each priority. Existing root-level rule files are moved into the directory without overwriting collisions. Default rules are generated once during GUI configuration initialization only when no `.txt` files exist; runtime reloads never recreate them.
- Colors are separate client configuration values, both defaulting to `0x339999FF`. Collapsed group counts are configurable and default off. A navigation button immediately left of the next-page button toggles all groups; right-click always collapses them. The upstream 1.20.1 navigation button fields are retained.
- Forge opens the configuration screen as a native GUI layer. Background transparency defaults to 25 percent. Apply persists valid edits without closing, resets the draft baseline, and leaves later edits cancellable. JEI input and background layout updates are blocked while this layer is active.
- Tag copying uses an interactive tooltip instead of a separate screen. Ctrl shortcut hints are collapsed until Ctrl is held. Recipe-tree sidebar clicks open recipes or uses on release, without treating drags or scrolls as clicks. Favorite input candidates use the existing interactive ingredient grid, including synchronized selection.
- The preceding calculation/layout batch fixes ordinary-target inventory allocation, recipe-page button width, collapsed 3D item layering, and bookmark-related bucket formatting.
- The subsequent chat/NBT batch is described above. In-game acceptance of the new UI is pending.

## Upstream integration in progress (2026-09-19)

The current uncommitted integration updates the official 1.20.1 baseline to `f80535691` (15.59.0), without MezzConfig. Fork candidate selection now extends upstream ingredient state, recipe transfers use the upstream transfer service, cheat quantity handling extends the dedicated cheat input handler, and query sessions extend upstream navigation history. Runtime rule listeners are removed on shutdown.

Issue #25 is fixed by restricting non-consumable toggles to recipe inputs. Repeated toggles on outputs are rejected without changing metadata. Previously corrupted bookmark data is not automatically repaired.

Windows validation passed 600 unit tests, 68 Forge server GameTests, Fabric client tests with and without AMECS, Forge/Fabric builds, and API compatibility against the fixed official `15.59.0.212` baseline. Whole-repository Spotless and the final cleanup recheck passed. Maintainer in-game acceptance is pending. Multiplayer acceptance should use matching JEIU client/server test builds; mixing old JEIU protocol versions is not verified. This does not claim complete parity with all 1.21.1 fork features.

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
