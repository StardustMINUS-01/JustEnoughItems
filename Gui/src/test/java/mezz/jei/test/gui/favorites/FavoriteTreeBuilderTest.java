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
		store.setFavorite(key("ingot"), INGOT);
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
		store.setFavorite(key("ingot"), INGOT);
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
		store.setFavorite(key("ingot"), PLATE);
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
		store.setFavorite(key("plate"), PLATE);
		store.setFavorite(key("ingot"), INGOT);
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
		store.setFavorite(key("blackstone"), INGOT);
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
	public void recordsActivePermutationIndexForSelectedFavoriteInput() {
		FavoriteRecipeStore store = store();
		store.setFavorite(key("blackstone"), INGOT);
		FavoriteTreeBuilder builder = builder(
			store,
			graph(
				resolved(ROOT, input("cobblestone", "cobblestone", "deepslate_cobblestone", "blackstone")),
				resolved(INGOT)
			)
		);

		FavoriteTreeBuilder.FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(2, result.recipes().get(0).inputs().get(0).activePermutationIndex());
	}

	@Test
	public void cycleDoesNotExpandForever() {
		FavoriteRecipeStore store = store();
		store.setFavorite(key("plate"), PLATE);
		store.setFavorite(key("root"), ROOT);
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
		store.setFavorite(key("plate"), PLATE);
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
		store.setFavorite(key("gear"), GEAR);
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
		store.setFavorite(key("glass"), GLASS_RECIPE);
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
		store.setFavorite(key("glass"), GLASS_RECIPE);
		store.setFavorite(key("stained"), STAINED_RECIPE);
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
		// The single-variant slot expands via its generated favorite...
		Assertions.assertEquals(
			Optional.of(key("ingot")),
			result.recipes().get(0).inputs().get(0).selectedFavoriteKey()
		);
		// ...while the multi-variant slot falls back to the displayed variant.
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
		store.setFavorite(key("glass"), GLASS_RECIPE);
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
		// The middle recipe's multi-variant slot expands via the unique manual favorite,
		// even though the displayed variant is a different one.
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

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(
			Optional.empty(),
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
		List<BookmarkIngredientKey> permutationKeys = permutations.length == 0 ?
			List.of(key(displayed)) :
			List.of(permutations).stream()
				.map(FavoriteTreeBuilderTest::key)
				.toList();
		return new FavoriteTreeBuilder.ResolvedInput(key(displayed), permutationKeys);
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

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of("test:ingredient", uid);
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(ResourceLocation.fromNamespaceAndPath("test", "recipe_type"), ResourceLocation.fromNamespaceAndPath("test", uid));
	}
}
