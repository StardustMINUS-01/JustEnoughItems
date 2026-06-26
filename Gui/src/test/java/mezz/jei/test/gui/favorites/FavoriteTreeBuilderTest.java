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

	private static FavoriteTreeBuilder builder(FavoriteRecipeStore store, Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph) {
		return new FavoriteTreeBuilder(store, recipe -> Optional.ofNullable(graph.get(recipe)));
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
