package mezz.jei.test.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteStoreTest {
	private static final BookmarkIngredientKey IRON_PICKAXE = target("minecraft:iron_pickaxe");
	private static final BookmarkIngredientKey DIAMOND_PICKAXE = target("minecraft:diamond_pickaxe");
	private static final BookmarkIngredientKey GOLDEN_PICKAXE = target("minecraft:golden_pickaxe");
	private static final FocusedRecipe IRON_RECIPE = recipe("minecraft:iron_pickaxe");
	private static final FocusedRecipe DIAMOND_RECIPE = recipe("minecraft:diamond_pickaxe");
	private static final FocusedRecipe GOLDEN_RECIPE = recipe("minecraft:golden_pickaxe");
	private static final FocusedRecipe ALTERNATE_RECIPE = recipe("test:alternate_iron_pickaxe");

	@Test
	public void indexesManualFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();

		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());

		Assertions.assertEquals(IRON_RECIPE, store.getManualFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(IRON_RECIPE).orElseThrow());
		Assertions.assertTrue(store.containsManual(IRON_PICKAXE));
	}

	@Test
	public void replacesRecipe() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());

		store.setFavorite(IRON_PICKAXE, ALTERNATE_RECIPE, Map.of());

		Assertions.assertEquals(ALTERNATE_RECIPE, store.getManualFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertTrue(store.getManualFavorite(IRON_RECIPE).isEmpty());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(ALTERNATE_RECIPE).orElseThrow());
	}

	@Test
	public void replacesTarget() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());

		store.setFavorite(DIAMOND_PICKAXE, IRON_RECIPE, Map.of());

		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertEquals(IRON_RECIPE, store.getManualFavorite(DIAMOND_PICKAXE).orElseThrow());
		Assertions.assertEquals(DIAMOND_PICKAXE, store.getManualFavorite(IRON_RECIPE).orElseThrow());
	}

	@Test
	public void removesManualFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());

		store.removeFavorite(IRON_PICKAXE);

		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertTrue(store.getManualFavorite(IRON_RECIPE).isEmpty());
		Assertions.assertFalse(store.containsManual(IRON_PICKAXE));
	}

	@Test
	public void notifiesOnChanges() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());
		Assertions.assertEquals(1, changed[0]);
		store.removeFavorite(IRON_PICKAXE);
		Assertions.assertEquals(2, changed[0]);
		store.clear();

		Assertions.assertEquals(3, changed[0]);
	}

	@Test
	public void prefersManualFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(IRON_PICKAXE, IRON_RECIPE);
		Assertions.assertEquals(IRON_RECIPE, store.getGeneratedFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());

		store.setFavorite(IRON_PICKAXE, ALTERNATE_RECIPE, Map.of());
		Assertions.assertEquals(ALTERNATE_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_PICKAXE, store.getManualFavorite(ALTERNATE_RECIPE).orElseThrow());

		store.removeFavorite(IRON_PICKAXE);
		Assertions.assertEquals(IRON_RECIPE, store.getFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
	}

	@Test
	public void cachesGeneratedFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		int[] invocations = {0};
		store.setGeneratedFavoriteResolver(key -> {
			invocations[0]++;
			return Optional.of(IRON_RECIPE);
		});

		Assertions.assertEquals(IRON_RECIPE, store.getGeneratedFavorite(IRON_PICKAXE).orElseThrow());
		Assertions.assertEquals(IRON_RECIPE, store.getGeneratedFavorite(IRON_PICKAXE).orElseThrow());

		Assertions.assertEquals(1, invocations[0]);
	}

	@Test
	public void retriesMissingFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		int[] calls = {0};
		store.setGeneratedFavoriteResolver(key -> {
			calls[0]++;
			return Optional.empty();
		});

		Assertions.assertTrue(store.getGeneratedFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertTrue(store.getFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertEquals(2, calls[0]);
	}

	@Test
	public void movesFavoriteBefore() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());
		store.setFavorite(DIAMOND_PICKAXE, DIAMOND_RECIPE, Map.of());
		store.setFavorite(GOLDEN_PICKAXE, GOLDEN_RECIPE, Map.of());

		Assertions.assertTrue(store.moveFavorite(DIAMOND_RECIPE, IRON_RECIPE, 0));

		Assertions.assertEquals(List.of(
			DIAMOND_RECIPE,
			IRON_RECIPE,
			GOLDEN_RECIPE
		), store.entries().stream().map(FavoriteRecipeStore.Entry::recipe).toList());
	}

	@Test
	public void movesFavoriteAfter() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());
		store.setFavorite(DIAMOND_PICKAXE, DIAMOND_RECIPE, Map.of());
		store.setFavorite(GOLDEN_PICKAXE, GOLDEN_RECIPE, Map.of());
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		Assertions.assertTrue(store.moveFavorite(IRON_RECIPE, GOLDEN_RECIPE, 1));

		Assertions.assertEquals(List.of(
			DIAMOND_RECIPE,
			GOLDEN_RECIPE,
			IRON_RECIPE
		), store.entries().stream().map(FavoriteRecipeStore.Entry::recipe).toList());
		Assertions.assertEquals(1, changed[0]);
	}

	@Test
	public void storesInputSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		FavoriteRecipeStore.FavoriteSlotInput slot = slot("sand", "sand", "red_sand");

		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of(0, slot));

		FavoriteRecipeStore.Entry entry = store.getManualEntry(IRON_RECIPE).orElseThrow();
		Assertions.assertEquals(IRON_PICKAXE, entry.target());
		Assertions.assertEquals(Map.of(0, slot), entry.inputs());
	}

	@Test
	public void synchronizesSelections() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		var family = List.of(target("minecraft:sand"), target("minecraft:red_sand"));
		var first = new FavoriteRecipeStore.FavoriteSlotInput(family.getFirst(), family);
		var second = new FavoriteRecipeStore.FavoriteSlotInput(family.getLast(), family);
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of(0, first, 1, second));
		Assertions.assertTrue(store.selectFavoriteInputs(IRON_RECIPE, first, family.getFirst(), true));
		Assertions.assertTrue(store.getManualEntry(IRON_RECIPE).orElseThrow().inputs().values().stream()
			.allMatch(input -> input.selected().equals(family.getFirst())));
		Assertions.assertTrue(store.selectFavoriteInputs(IRON_RECIPE, first, family.getLast(), false));
		Assertions.assertEquals(Map.of(0, second, 1, second), store.getManualEntry(IRON_RECIPE).orElseThrow().inputs());
		Assertions.assertFalse(store.selectFavoriteInputs(IRON_RECIPE, second, family.getLast(), true));
	}

	@Test
	public void cyclesSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		var slot = slot("sand", "sand", "red_sand", "gravel");
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of(0, slot));
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		Assertions.assertTrue(store.cycleFavoriteInputs(IRON_RECIPE, slot, 1));
		Assertions.assertEquals(target("minecraft:gravel"), currentSlot(store).selected());
		Assertions.assertEquals(1, changed[0]);
		Assertions.assertTrue(store.cycleFavoriteInputs(IRON_RECIPE, currentSlot(store), -1));
		Assertions.assertEquals(target("minecraft:sand"), currentSlot(store).selected());
		Assertions.assertEquals(2, changed[0]);
	}

	@Test
	public void ignoresUnavailableSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		FavoriteRecipeStore.FavoriteSlotInput single = slot("sand", "sand");
		store.setFavorite(
			IRON_PICKAXE,
			IRON_RECIPE,
			Map.of(0, single)
		);
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		Assertions.assertFalse(store.cycleFavoriteInputs(IRON_RECIPE, single, 1));
		Assertions.assertFalse(store.cycleFavoriteInputs(
			IRON_RECIPE,
			slot("red_sand", "red_sand", "gravel"),
			1
		));
		Assertions.assertFalse(store.cycleFavoriteInputs(
			DIAMOND_RECIPE,
			single,
			1
		));
		Assertions.assertEquals(0, changed[0]);
	}

	@Test
	public void replacesManualBatch() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(IRON_PICKAXE, IRON_RECIPE, Map.of());
		store.setGeneratedFavorite(GOLDEN_PICKAXE, GOLDEN_RECIPE);
		int[] changed = {0};
		store.addSourceListChangedListener(() -> changed[0]++);

		store.setFavorites(List.of(
			new FavoriteRecipeStore.Entry(DIAMOND_PICKAXE, DIAMOND_RECIPE, Map.of()),
			new FavoriteRecipeStore.Entry(GOLDEN_PICKAXE, GOLDEN_RECIPE, Map.of())
		));

		Assertions.assertTrue(store.getManualFavorite(IRON_PICKAXE).isEmpty());
		Assertions.assertEquals(DIAMOND_RECIPE, store.getManualFavorite(DIAMOND_PICKAXE).orElseThrow());
		Assertions.assertEquals(GOLDEN_RECIPE, store.getManualFavorite(GOLDEN_PICKAXE).orElseThrow());
		Assertions.assertEquals(GOLDEN_RECIPE, store.getGeneratedFavorite(GOLDEN_PICKAXE).orElseThrow());
		Assertions.assertEquals(1, changed[0]);
	}

	private static FavoriteRecipeStore.FavoriteSlotInput slot(String selected, String... candidates) {
		return new FavoriteRecipeStore.FavoriteSlotInput(target("minecraft:" + selected),
			java.util.Arrays.stream(candidates).map(uid -> target("minecraft:" + uid)).toList());
	}

	private static FavoriteRecipeStore.FavoriteSlotInput currentSlot(FavoriteRecipeStore store) {
		return store.getManualEntry(IRON_RECIPE).orElseThrow().inputs().get(0);
	}

	private static BookmarkIngredientKey target(String uid) {
		return new BookmarkIngredientKey("minecraft:item_stack", uid);
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"), ResourceLocation.parse(uid));
	}
}
