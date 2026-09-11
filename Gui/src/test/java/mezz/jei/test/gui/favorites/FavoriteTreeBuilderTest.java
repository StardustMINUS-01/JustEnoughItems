package mezz.jei.test.gui.favorites;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteTreeBuilder.FavoriteTreeResult;
import mezz.jei.gui.favorites.FavoriteTreeBuilder.FavoriteTreeRecipe;
import mezz.jei.gui.favorites.FavoriteTreeBuilder.ResolvedRecipe;
import mezz.jei.gui.favorites.FavoriteTreeBuilder.ResolvedInput;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	public void keepsRootWithoutFavorites() {
		FavoriteTreeBuilder builder = builder(new FavoriteRecipeStore(),
			resolved(ROOT, input("ingot"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 4);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(Optional.empty(), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	public void limitsDepth(int depth) {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("plate"), PLATE);
		favorite(store, key("ingot"), INGOT);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("plate")),
			resolved(PLATE, input("ingot")),
			resolved(INGOT));

		FavoriteTreeResult result = builder.build(ROOT, depth);

		Assertions.assertEquals(List.of(ROOT, PLATE).subList(0, depth + 1), recipes(result));
		Assertions.assertEquals(Optional.of(depth == 0 ? PLATE : INGOT),
			result.recipes().getLast().inputs().getFirst().selectedFavoriteRecipe());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void expandsFavorite(boolean manual) {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		if (manual) {
			favorite(store, key("ingot"), INGOT);
		} else {
			store.setGeneratedFavorite(key("ingot"), INGOT);
		}
		FavoriteTreeBuilder builder = builder(store, resolved(ROOT, input("ingot")), resolved(INGOT));
		FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, INGOT), recipes(result));
		var input = result.recipes().getFirst().inputs().getFirst();
		Assertions.assertEquals(Optional.of(key("ingot")), input.selectedFavoriteKey());
		Assertions.assertEquals(Optional.of(INGOT), input.selectedFavoriteRecipe());
	}

	@Test
	public void prefersManualFavorite() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), INGOT);
		favorite(store, key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(INGOT),
			resolved(PLATE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void stopsCycles() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("plate"), PLATE);
		favorite(store, key("root"), ROOT);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("plate")),
			resolved(PLATE, input("root"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 8);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(ROOT), result.recipes().get(1).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void deduplicatesRecipes() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("plate"), PLATE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input(0, "plate"), input(1, "plate")),
			resolved(PLATE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT, PLATE), recipes(result));
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
		Assertions.assertEquals(Optional.of(PLATE), result.recipes().get(0).inputs().get(1).selectedFavoriteRecipe());
	}

	@Test
	public void stopsMissingBranch() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("gear"), GEAR);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("gear"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(Optional.of(GEAR), result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe());
	}

	@Test
	public void handlesMissingRoot() {
		FavoriteTreeBuilder builder = builder(new FavoriteRecipeStore());

		FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertTrue(result.recipes().isEmpty());
	}

	@Test
	public void selectsUniqueManualVariant() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("glass"), GLASS_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("stained", "glass", "stained")),
			resolved(GLASS_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 1);

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
	public void resolvesAmbiguousVariants() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		favorite(store, key("glass"), GLASS_RECIPE);
		favorite(store, key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			graph(resolved(ROOT, input("stained", "glass", "stained")), resolved(RULE_RECIPE)),
			variants -> Optional.of(RULE_RECIPE));
		FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, RULE_RECIPE), recipes(result));
		var input = result.recipes().getFirst().inputs().getFirst();
		Assertions.assertEquals(Optional.of(key("stained")), input.selectedFavoriteKey());
		Assertions.assertEquals(Optional.of(RULE_RECIPE), input.selectedFavoriteRecipe());
	}

	@Test
	public void usesRootDisplayedVariant() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("stained", "glass", "stained")), resolved(STAINED_RECIPE));
		FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT, STAINED_RECIPE), recipes(result));
		Assertions.assertEquals(Optional.of(key("stained")),
			result.recipes().getFirst().inputs().getFirst().selectedFavoriteKey());
	}

	@Test
	public void keepsUnfavoritedRoot() {
		FavoriteTreeBuilder builder = builder(new FavoriteRecipeStore(),
			resolved(ROOT, input("stained", "glass", "stained"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 1);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		Assertions.assertEquals(
			Optional.empty(),
			result.recipes().get(0).inputs().get(0).selectedFavoriteRecipe()
		);
	}

	@Test
	public void resolvesSlotsIndependently() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), INGOT);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input(0, "ingot"), input(1, "stained", "glass", "stained")),
			resolved(INGOT),
			resolved(STAINED_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 1);

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
	public void expandsMiddleVariant() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		favorite(store, key("glass"), GLASS_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("stained", "glass", "stained")),
			resolved(GLASS_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

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
	public void prunesUnresolvedMiddle() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("stained", "glass", "stained"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void rejectsMiddleDisplayFallback() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("stained", "glass", "stained"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void usesRootSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("white"), WHITE_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input(0, "ingot"), input(1, "stained", "glass", "stained")),
			resolved(WHITE_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(1, key("white")));

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
	public void keepsRootSelection() {
		FavoriteTreeBuilder builder = builder(new FavoriteRecipeStore(),
			resolved(ROOT, input(0, "ingot"), input(1, "stained", "glass", "stained"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(1, key("white")));

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
	public void mapsRootSlotIndices() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("white"), WHITE_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input(1, "ingot"), input(3, "stained", "glass", "stained")),
			resolved(WHITE_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 1, Map.of(3, key("white")));

		Assertions.assertEquals(List.of(ROOT, WHITE_RECIPE), recipes(result));
		Assertions.assertEquals(
			Optional.of(key("white")),
			result.recipes().get(0).inputs().get(1).selectedFavoriteKey()
		);
	}

	@Test
	public void isolatesRootSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("white"), PLATE);
		store.setGeneratedFavorite(key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input(0, "ingot", "ingot", "white")),
			resolved(PLATE, input(0, "stained", "glass", "stained")));

		FavoriteTreeResult result = builder.build(ROOT, 2, Map.of(0, key("white")));

		Assertions.assertEquals(List.of(ROOT), recipes(result));
		var input = result.recipes().getFirst().inputs().getFirst();
		Assertions.assertEquals(Optional.of(key("white")), input.selectedFavoriteKey());
		Assertions.assertEquals(Optional.of(PLATE), input.selectedFavoriteRecipe());
	}

	@Test
	public void prunesPartialBranch() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("dust"), DUST_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("dust"), input(1, "stained", "glass", "stained")),
			resolved(DUST_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 3);

		Assertions.assertEquals(List.of(ROOT), recipes(result));
	}

	@Test
	public void keepsResolvedMiddle() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("dust"))
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

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
	public void usesStoredSelection() {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
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
		favorite(store, key("stained"), STAINED_RECIPE);
		FavoriteTreeBuilder builder = builder(store,
			resolved(ROOT, input("ingot")),
			resolved(PLATE, input("stained", "glass", "stained")),
			resolved(GLASS_RECIPE)
		);

		FavoriteTreeResult result = builder.build(ROOT, 2);

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

	private static FavoriteTreeBuilder builder(FavoriteRecipeStore store, ResolvedRecipe... recipes) {
		Map<FocusedRecipe, ResolvedRecipe> graph = graph(recipes);
		return new FavoriteTreeBuilder(store, recipe -> Optional.ofNullable(graph.get(recipe)));
	}

	private static FavoriteTreeBuilder builder(
		FavoriteRecipeStore store,
		Map<FocusedRecipe, ResolvedRecipe> graph,
		mezz.jei.gui.favorites.SlotRuleResolver slotRuleResolver
	) {
		return new FavoriteTreeBuilder(
			store,
			recipe -> Optional.ofNullable(graph.get(recipe)),
			slotRuleResolver
		);
	}

	private static Map<FocusedRecipe, ResolvedRecipe> graph(ResolvedRecipe... recipes) {
		Map<FocusedRecipe, ResolvedRecipe> graph = new LinkedHashMap<>();
		for (ResolvedRecipe recipe : recipes) {
			graph.put(recipe.recipe(), recipe);
		}
		return graph;
	}

	private static ResolvedRecipe resolved(FocusedRecipe recipe, ResolvedInput... inputs) {
		return new ResolvedRecipe(recipe, List.of(inputs));
	}

	private static ResolvedInput input(String displayed, String... permutations) {
		return input(0, displayed, permutations);
	}

	private static ResolvedInput input(int inputSlotIndex, String displayed, String... permutations) {
		List<BookmarkIngredientKey> permutationKeys = permutations.length == 0 ?
			List.of(key(displayed)) :
			List.of(permutations).stream()
				.map(FavoriteTreeBuilderTest::key)
				.toList();
		return new ResolvedInput(inputSlotIndex, key(displayed), permutationKeys, List.of());
	}

	private static List<FocusedRecipe> recipes(FavoriteTreeResult result) {
		return result.recipes()
			.stream()
			.map(FavoriteTreeRecipe::recipe)
			.toList();
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
