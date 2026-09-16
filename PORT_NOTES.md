# JEI 1.21.1 → 1.20.1 Fork Port — Notes

Status reviewed against `2abe1d5d` on 2026-09-16 through static code and call-site inspection. Builds, tests, and in-game behavior were not verified in this review.

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
- Recipe-page search, preference filtering, and search-driven candidate filtering: helper classes exist, but `RecipesGui` and `RecipeGuiLogic` do not connect them to the page. `IRecipeGuiLogic.applyRecipeResultFilter` remains an empty default method.
- Query-session navigation: `RecipeNavigationHistory` and its button controller are not connected to the recipe page. The existing stack-based back history is not equivalent to session navigation.
- Bookmark-group sharing and import: link formatting and parsing helpers exist, but the group-sharing packet, sending entry point, and actual import flow are not connected.
- Saving missing ingredients as a recipe-chain group: calculation exists, but the corresponding input action and group-creation flow are absent.
- External terminal search: `ExternalIngredientSearchHandlerRegistry` has no production registration or call sites outside its own definition.

## Platform adaptations

- `getRegisteredName()` (1.20.5+ Holder API) → `BuiltInRegistries.ITEM.getKey(item).toString()` in `JeiChatItemLinks`.
- Pattern switch (`switch (x) { case Foo f -> ... }`) → `if/else instanceof` for Java 17.
- `List.getFirst()` → `List.get(0)` for Java 17.
- `RecipeGuiLogic.previousRecipeCategory()` stays `void` (1.20.1 fork convention; 1.21.1 changed to `boolean`).
- 1.20.1's `IClientConfig` has `is*Enabled()` (no `.getValue()`) for booleans and `get*Enabled()` for collection getters — preserved.

## Scope distinctions

- Candidate previews and selection are partially integrated: bookmark and recipe-tree tooltips, favorite input restoration, and layout projections have real callers. This does not complete search-driven candidate filtering.
- Bookmark group IDs are already integers. Storage still uses the 1.20.1 implementation rather than the complete `BookmarkJsonConfig` / `BookmarkConfigEntryCodec` structure. A structural difference alone is not a missing feature; saved-data compatibility needs separate verification.
- AE2 pattern encoding has a network-pattern lookup and filtering path. Compatibility with specific AE2 and extension versions still requires runtime verification.
