package mezz.jei.test.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class FavoriteRecipeStoreTest {
	private static final BookmarkIngredientKey IRON_PICKAXE = target("minecraft:iron_pickaxe");
	private static final BookmarkIngredientKey DIAMOND_PICKAXE = target("minecraft:diamond_pickaxe");
	private static final BookmarkIngredientKey GOLDEN_PICKAXE = target("minecraft:golden_pickaxe");
	private static final FocusedRecipe IRON_PICKAXE_RECIPE = recipe("minecraft:iron_pickaxe");
	private static final FocusedRecipe DIAMOND_PICKAXE_RECIPE = recipe("minecraft:diamond_pickaxe");
	private static final FocusedRecipe GOLDEN_PICKAXE_RECIPE = recipe("minecraft:golden_pickaxe");
	private static final FocusedRecipe ALTERNATE_IRON_PICKAXE_RECIPE = recipe("test:alternate_iron_pickaxe");

	@Test
	public void manualFavoriteCanBeReadFromTargetAndRecipe() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();

		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		Assertions.assertEquals(IRON_PICKAXE_RECIPE, store.getManualFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(IRON_PICKAXE_RECIPE).orElseThrow());
		Assertions.assertTrue(store.containsManual(IRON_PICKAXE));
	}

	@Test
	public void sameTargetFavoriteReplacesOldRecipeMapping() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		store.setFavorite(IRON_PICKAXE, ALTERNATE_IRON_PICKAXE_RECIPE);

		Assertions.assertEquals(ALTERNATE_IRON_PICKAXE_RECIPE, store.getManualFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE_RECIPE).isEmpty());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(ALTERNATE_IRON_PICKAXE_RECIPE).orElseThrow());
	}

	@Test
	public void sameRecipeFavoriteReplacesOldTargetMapping() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		store.setFavorite(DIAMOND_PICKAXE, IRON_PICKAXE_RECIPE);

		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertEquals(IRON_PICKAXE_RECIPE, store.getManualFavorite(DIAMOND_PICKAXE).orElseThrow());
		Assertions.assertEquals(DIAMOND_PICKAXE, store.getManualFavorite(IRON_PICKAXE_RECIPE).orElseThrow());
	}

	@Test
	public void removingTargetFavoriteClearsBothDirections() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		store.removeFavorite(IRON_PICKAXE);

		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE_RECIPE).isEmpty());
		Assertions.assertFalse(store.containsManual(IRON_PICKAXE));
	}

	@Test
	public void notifiesListenersWhenManualFavoritesChange() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);
		store.removeFavorite(IRON_PICKAXE);
		store.clear();

		Assertions.assertEquals(3, changed[0]);
	}

	@Test
	public void generatedFavoriteIsReturnedWhenManualFavoriteIsMissing() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();

		store.setGeneratedFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		Assertions.assertEquals(IRON_PICKAXE_RECIPE, store.getGeneratedFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getGeneratedFavorite(IRON_PICKAXE_RECIPE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());
	}

	@Test
	public void manualFavoriteOverridesGeneratedFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);

		store.setFavorite(IRON_PICKAXE, ALTERNATE_IRON_PICKAXE_RECIPE);

		Assertions.assertEquals(ALTERNATE_IRON_PICKAXE_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(ALTERNATE_IRON_PICKAXE_RECIPE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getGeneratedFavorite(IRON_PICKAXE_RECIPE).orElseThrow());
	}

	@Test
	public void removingManualFavoriteFallsBackToGeneratedFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);
		store.setFavorite(IRON_PICKAXE, ALTERNATE_IRON_PICKAXE_RECIPE);

		store.removeFavorite(IRON_PICKAXE);

		Assertions.assertEquals(IRON_PICKAXE_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
	}

	@Test
	public void movingManualFavoriteBeforeTargetReordersEntries() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);
		store.setFavorite(DIAMOND_PICKAXE, DIAMOND_PICKAXE_RECIPE);
		store.setFavorite(GOLDEN_PICKAXE, GOLDEN_PICKAXE_RECIPE);

		Assertions.assertTrue(store.moveFavorite(DIAMOND_PICKAXE_RECIPE, IRON_PICKAXE_RECIPE, 0));

		Assertions.assertEquals(List.of(
			DIAMOND_PICKAXE_RECIPE,
			IRON_PICKAXE_RECIPE,
			GOLDEN_PICKAXE_RECIPE
		), store.entries().stream().map(FavoriteRecipeStore.Entry::recipe).toList());
	}

	@Test
	public void movingManualFavoriteAfterTargetReordersEntriesAndNotifiesListeners() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_PICKAXE_RECIPE);
		store.setFavorite(DIAMOND_PICKAXE, DIAMOND_PICKAXE_RECIPE);
		store.setFavorite(GOLDEN_PICKAXE, GOLDEN_PICKAXE_RECIPE);
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		Assertions.assertTrue(store.moveFavorite(IRON_PICKAXE_RECIPE, GOLDEN_PICKAXE_RECIPE, 1));

		Assertions.assertEquals(List.of(
			DIAMOND_PICKAXE_RECIPE,
			GOLDEN_PICKAXE_RECIPE,
			IRON_PICKAXE_RECIPE
		), store.entries().stream().map(FavoriteRecipeStore.Entry::recipe).toList());
		Assertions.assertEquals(1, changed[0]);
	}

	private static BookmarkIngredientKey target(String uid) {
		return new BookmarkIngredientKey("minecraft:item_stack", uid, null);
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"), ResourceLocation.parse(uid));
	}
}
