package mezz.jei.test.gui.input;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.FocusedRecipeCandidate;
import mezz.jei.gui.input.FocusedRecipeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FocusedRecipeResolverTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final FocusedRecipe GUI_RECIPE = recipe("test:gui_recipe");
	private static final FocusedRecipe BOOKMARK_RECIPE = recipe("test:bookmark_recipe");
	private static final FocusedRecipe PANEL_RECIPE = recipe("test:panel_recipe");

	@Test
	public void craftingLookupPrefersRecipeGuiFocusedRecipeBeforeBookmarkAndPanel() {
		Optional<FocusedRecipe> focusedRecipe = FocusedRecipeResolver.resolveCraftingLookup(
			Optional.of(FocusedRecipeCandidate.recipe(GUI_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(BOOKMARK_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(PANEL_RECIPE))
		);

		assertEquals(Optional.of(GUI_RECIPE), focusedRecipe);
	}

	@Test
	public void hotkeyLookupPrefersBookmarkBeforePanelAndRecipeGui() {
		Optional<FocusedRecipe> focusedRecipe = FocusedRecipeResolver.resolveHotkey(
			Optional.of(FocusedRecipeCandidate.recipe(BOOKMARK_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(PANEL_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(GUI_RECIPE))
		);

		assertEquals(Optional.of(BOOKMARK_RECIPE), focusedRecipe);
	}

	@Test
	public void hotkeyLookupIgnoresIngredientBookmarkCandidatesLikeNei() {
		Optional<FocusedRecipe> focusedRecipe = FocusedRecipeResolver.resolveHotkey(
			Optional.of(FocusedRecipeCandidate.ingredientBookmark(BOOKMARK_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(PANEL_RECIPE)),
			Optional.of(FocusedRecipeCandidate.recipe(GUI_RECIPE))
		);

		assertEquals(Optional.of(PANEL_RECIPE), focusedRecipe);
	}

	@Test
	public void bookmarkMetadataCreatesCandidatesOnlyForRecipeBackedSlots() {
		BookmarkItemMetadata resultMetadata = metadata(BookmarkItemType.RESULT, BOOKMARK_RECIPE.recipeUid());
		BookmarkItemMetadata ingredientMetadata = metadata(BookmarkItemType.INGREDIENT, BOOKMARK_RECIPE.recipeUid());
		BookmarkItemMetadata itemMetadata = metadata(BookmarkItemType.ITEM, null);

		assertEquals(
			Optional.of(FocusedRecipeCandidate.recipe(BOOKMARK_RECIPE)),
			FocusedRecipeCandidate.fromBookmarkMetadata(resultMetadata)
		);
		assertEquals(
			Optional.of(FocusedRecipeCandidate.ingredientBookmark(BOOKMARK_RECIPE)),
			FocusedRecipeCandidate.fromBookmarkMetadata(ingredientMetadata)
		);
		assertTrue(FocusedRecipeCandidate.fromBookmarkMetadata(itemMetadata).isEmpty());
	}

	private static FocusedRecipe recipe(String recipeUid) {
		return new FocusedRecipe(CRAFTING, ResourceLocation.parse(recipeUid));
	}

	private static BookmarkItemMetadata metadata(BookmarkItemType type, ResourceLocation recipeUid) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			recipeUid == null ? null : CRAFTING,
			recipeUid,
			Set.of()
		);
	}
}
