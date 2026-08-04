package mezz.jei.test.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteTreeBuilderTest {
	private static final FocusedRecipe ROOT = recipe("root");
	private static final FocusedRecipe PLATE = recipe("plate");
	private static final FocusedRecipe INGOT = recipe("ingot");
	private static final FocusedRecipe GEAR = recipe("gear");
	private static final FocusedRecipe GLASS_RECIPE = recipe("glass_recipe");
	private static final FocusedRecipe STAINED_RECIPE = recipe("stained_recipe");
	private static final FocusedRecipe RULE_RECIPE = recipe("rule_recipe");
	private static final FocusedRecipe WHITE_RECIPE = recipe("white_recipe");
	private static final FocusedRecipe DUST_RECIPE = recipe("dust_recipe");

	@Test
	public void buildTreeIncludesRootRecipeOnlyWhenNoInputsAreFavorited() {
		FavoriteTreeBuilder builder = builder(
			store(),
			graph(
				resolved(ROOT, input("ingot"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 4);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(Optional.empty(), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void depthZeroDoesNotExpandInputs() {
		FavoriteRecipeStore store = store();
		favorite(store, key("ingot"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(INGOT, input("dust"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 0);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(Optional.of(INGOT), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void buildTreeExpandsManualFavoriteInput() {
		FavoriteRecipeStore store = store();
		favorite(store, key("ingot"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(INGOT)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, INGOT), recipes(result));
		Assertions.assertEquals(Optional.of(key("ingot")), result.recipes().get(0).inputs().get(0).selectedFavoriteKey());
	}

	@Test
	public void buildTreeExpandsGeneratedFavoriteInput() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(INGOT)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, INGOT), recipes(result));
		Assertions.assertEquals(Optional.of(key("ingot")), result.recipes().get(0).inputs().get(0).selectedFavoriteKey());
		Assertions.assertEquals(Optional.of(INGOT), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void buildTreePrefersManualFavoriteOverGeneratedFavorite() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), INGOT);
		favorite(store, key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(INGOT),
				resolved(PLATE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void depthOneExpandsOnlyOneLayer() {
		FavoriteRecipeStore store = store();
		favorite(store, key("plate"), PLATE);
		favorite(store, key("ingot"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("plate")),
				resolved(PLATE, input("ingot")),
				resolved(INGOT)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(INGOT), result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void buildTreeFindsFavoriteFromInputPermutationsWhenDisplayedInputIsNotFavorited() {
		FavoriteRecipeStore store = store();
		favorite(store, key("blackstone"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("cobblestone", "cobblestone", "blackstone")),
				resolved(INGOT)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, INGOT), recipes(result));
		Assertions.assertEquals(Optional.of(key("blackstone")), result.recipes().get(0).inputs().get(0).selectedFavoriteKey());
	}

	@Test
	public void cycleDoesNotExpandForever() {
		FavoriteRecipeStore store = store();
		favorite(store, key("plate"), PLATE);
		favorite(store, key("root"), ROOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("plate")),
				resolved(PLATE, input("root"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 8);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(ROOT), result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void duplicateFavoriteRecipeIsAddedOnce() {
		FavoriteRecipeStore store = store();
		favorite(store, key("plate"), PLATE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("plate"), input("plate")),
				resolved(PLATE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(1).selectedFavoriteRecipe());
	}

	@Test
	public void missingFocusedRecipeStopsThatBranch() {
		FavoriteRecipeStore store = store();
		favorite(store, key("gear"), GEAR);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("gear"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(Optional.of(GEAR), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void missingRootRecipeReturnsEmptyTree() {
		FavoriteTreeBuilder builder = builder(store(), graph());

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertTrue(result.recipes().isEmpty());
	}

	@Test
	public void multiVariantSlotExpandsUniqueManualFavorite() {
		FavoriteRecipeStore store = store();
		favorite(store, key("glass"), GLASS_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("stained", "glass", "stained")),
				resolved(GLASS_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, GLASS_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("glass")),
			result.recipes().get(0).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(GLASS_RECIPE),
			result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void multiVariantSlotWithMultipleManualFavoritesFallsBackToSlotRule() {
		FavoriteRecipeStore store = store();
		favorite(store, key("glass"), GLASS_RECIPE);
		favorite(store, key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("stained", "glass", "stained")),
				resolved(RULE_RECIPE)
			),
			variants -> Optional.of(RULE_RECIPE)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, RULE_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("stained")),
			result.recipes().get(0).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(RULE_RECIPE),
			result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void multiVariantSlotWithoutRulesFallsBackToDisplayedVariant() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("stained", "glass", "stained")),
				resolved(STAINED_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, STAINED_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("stained")),
			result.recipes().get(0).inputs().get(0).selectedFavoriteKey()
		);
	}

	@Test
	public void multiVariantSlotWithoutAnyFavoriteStops() {
		FavoriteTreeBuilder builder = builder(
			store(),
			graph(
				resolved(ROOT, input("stained", "glass", "stained"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(
			Optional.empty(),
			result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void multiSlotRecipeResolvesEachSlotIndependently() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), INGOT);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot"), input("stained", "glass", "stained")),
				resolved(INGOT),
				resolved(STAINED_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, INGOT, STAINED_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("ingot")),
			result.recipes().get(0).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(key("stained")),
			result.recipes().get(0).inputs().get(1).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(STAINED_RECIPE),
			result.recipes().get(0).inputs().get(1).selectedFavoriteRecipe()
		);
	}

	@Test
	public void middleRecipeMultiVariantSlotExpandsByUniqueManualFavorite() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		favorite(store, key("glass"), GLASS_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("stained", "glass", "stained")),
				resolved(GLASS_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT, PLATE, GLASS_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("glass")),
			result.recipes().get(1).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(GLASS_RECIPE),
			result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void middleRecipeMultiVariantSlotWithoutFavoriteStopsBranch() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("stained", "glass", "stained"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void middleRecipeMultiVariantSlotWithDisplayedFavoriteDoesNotFallBack() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("stained", "glass", "stained"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void rootMultiVariantSlotFallsBackToViewSelection() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("white"), WHITE_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input(0, "ingot"), input(1, "stained", "glass", "stained")),
				resolved(WHITE_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(1, key("white")));

		Assertions.assertEquals(List.of(ROOT, WHITE_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("white")),
			result.recipes().get(0).inputs().get(1).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(WHITE_RECIPE),
			result.recipes().get(0).inputs().get(1).selectedFavoriteRecipe()
		);
	}

	@Test
	public void rootMultiVariantSlotLocksViewSelectionWithoutExpanding() {
		FavoriteTreeBuilder builder = builder(
			store(),
			graph(
				resolved(ROOT, input(0, "ingot"), input(1, "stained", "glass", "stained"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(1, key("white")));

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("white")),
			result.recipes().get(0).inputs().get(1).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.empty(),
			result.recipes().get(0).inputs().get(1).selectedFavoriteRecipe()
		);
	}

	@Test
	public void rootViewSelectionUsesInputSlotIndex() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("white"), WHITE_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input(1, "ingot"), input(3, "stained", "glass", "stained")),
				resolved(WHITE_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(3, key("white")));

		Assertions.assertEquals(List.of(ROOT, WHITE_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("white")),
			result.recipes().get(0).inputs().get(1).selectedFavoriteKey()
		);
	}

	@Test
	public void rootViewSelectionDoesNotLeakIntoMiddleRecipes() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input(0, "ingot")),
				resolved(PLATE, input(0, "stained", "glass", "stained"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2, Map.of(0, key("stained")));

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void partiallyResolvedRecipePrunesResolvedChildren() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("dust"), DUST_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("dust"), input(1, "stained", "glass", "stained")),
				resolved(DUST_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void middleRecipeWithAllResolvedInputsIsWrittenEvenWithoutExpansion() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("dust"))
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("dust")),
			result.recipes().get(1).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.empty(),
			result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void middleRecipeUsesStoredFavoriteInputSelection() {
		FavoriteRecipeStore store = store();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setFavorite(
			key("plate"),
			PLATE,
			Map.of(0, new FavoriteRecipeStore.FavoriteSlotInput(
				key("glass"),
				List.of(key("glass"), key("stained"))
			))
		);
		favorite(store, key("glass"), GLASS_RECIPE);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("ingot")),
				resolved(PLATE, input("stained", "glass", "stained")),
				resolved(GLASS_RECIPE)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT, PLATE, GLASS_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("glass")),
			result.recipes().get(1).inputs().get(0).selectedFavoriteKey()
		);
		Assertions.assertEquals(
			Optional.of(GLASS_RECIPE),
			result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	private static FavoriteTreeBuilder builder(FavoriteRecipeStore store, Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph) {
		return new FavoriteTreeBuilder(store, recipe -> Optional.ofNullable(graph.get(recipe)));
	}

	private static FavoriteTreeBuilder builder(
		FavoriteRecipeStore store,
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph,
		mezz.jei.gui.favorites.SlotRuleResolver slotRuleResolver
	) {
		return new FavoriteTreeBuilder(
			store,
			recipe -> Optional.ofNullable(graph.get(recipe)),
			slotRuleResolver
		);
	}

	private static Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph(FavoriteTreeBuilder.ResolvedRecipe... recipes) {
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		for (FavoriteTreeBuilder.ResolvedRecipe recipe : recipes) {
			graph.put(recipe.recipe(), recipe);
		}
		return graph;
	}

	private static FavoriteTreeBuilder.ResolvedRecipe resolved(FocusedRecipe recipe, FavoriteTreeBuilder.ResolvedInput... inputs) {
		return new FavoriteTreeBuilder.ResolvedRecipe(recipe, List.of(inputs));
	}

	private static FavoriteTreeBuilder.ResolvedInput input(String displayed, String... permutations) {
		return input(0, displayed, permutations);
	}

	private static FavoriteTreeBuilder.ResolvedInput input(int inputSlotIndex, String displayed, String... permutations) {
		List<BookmarkIngredientKey> permutationKeys = permutations.length == 0 ?
			List.of(key(displayed)) :
			List.of(permutations).stream()
				.map(FavoriteTreeBuilderTest::key)
				.toList();
		return new FavoriteTreeBuilder.ResolvedInput(inputSlotIndex, key(displayed), permutationKeys);
	}

	private static List<FocusedRecipe> recipes(FavoriteTreeBuilder.FavoriteTreeResult result) {
		return result.recipes()
			.stream()
			.map(FavoriteTreeBuilder.FavoriteTreeRecipe::recipe)
			.toList();
	}

	private static FavoriteRecipeStore store() {
		return new FavoriteRecipeStore();
	}

	private static void favorite(FavoriteRecipeStore store, BookmarkIngredientKey key, FocusedRecipe recipe) {
		store.setFavorite(key, recipe, Map.of());
	}

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of("test:ingredient", uid);
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(ResourceLocation.fromNamespaceAndPath("test", "recipe_type"), ResourceLocation.fromNamespaceAndPath("test", uid));
	}
}
