package mezz.jei.test.bench;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.library.recipes.collect.RecipeMap;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredient;
import mezz.jei.test.lib.TestIngredientHelper;
import mezz.jei.test.lib.TestIngredientRenderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A recipe-tree expansion engine for the 1.20.1 Forge benchmark, backed by
 * {@link RecipeMap} with the {@link RecipeIngredientRole#INPUT} role. It mirrors
 * the semantics of the 1.21.1 {@code FavoriteTreeBuilder.build}: starting from a
 * root recipe, each input ingredient of a recipe is resolved through the recipe
 * index to the recipes that consume it, expanding the tree level by level while
 * the visited set prevents cycles and duplicated work.
 */
public final class JeiRecipeTreeEngine {
	private static final RecipeType<TestRecipe> RECIPE_TYPE = RecipeType.create("test", "recipe_tree", TestRecipe.class);
	private static final TestColorHelper COLOR_HELPER = new TestColorHelper();

	private final IIngredientManager ingredientManager;
	private final RecipeMap inputRecipeMap;
	private final Map<String, TestRecipe> recipesByUid;

	private JeiRecipeTreeEngine(
		IIngredientManager ingredientManager,
		RecipeMap inputRecipeMap,
		Map<String, TestRecipe> recipesByUid
	) {
		this.ingredientManager = ingredientManager;
		this.inputRecipeMap = inputRecipeMap;
		this.recipesByUid = recipesByUid;
	}

	/**
	 * Builds an engine indexing {@code recipes} into an INPUT-role {@link RecipeMap}.
	 *
	 * @return the engine (index build time is measured by the caller)
	 */
	public static EngineResult create(List<TestRecipe> recipes) {
		SubtypeManager subtypeManager = new SubtypeManager(new SubtypeInterpreters());
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, COLOR_HELPER);
		builder.register(TestIngredient.TYPE, List.of(), new TestIngredientHelper(), new TestIngredientRenderer());
		IIngredientManager ingredientManager = builder.build();

		Comparator<RecipeType<?>> recipeTypeComparator = Comparator.comparing(recipeType -> recipeType.getUid().toString());
		RecipeMap inputRecipeMap = new RecipeMap(recipeTypeComparator, ingredientManager, RecipeIngredientRole.INPUT);
		for (TestRecipe recipe : recipes) {
			inputRecipeMap.addRecipe(RECIPE_TYPE, recipe, role -> {
				if (role != RecipeIngredientRole.INPUT) {
					return List.of();
				}
				List<ITypedIngredient<?>> typed = new ArrayList<>(recipe.inputs().size());
				for (TestIngredient input : recipe.inputs()) {
					ingredientManager.createTypedIngredient(TestIngredient.TYPE, input).ifPresent(typed::add);
				}
				return typed;
			});
		}

		Map<String, TestRecipe> recipesByUid = recipes.stream()
			.collect(Collectors.toMap(TestRecipe::uid, Function.identity()));
		return new EngineResult(new JeiRecipeTreeEngine(ingredientManager, inputRecipeMap, recipesByUid));
	}

	/**
	 * Expands the recipe tree rooted at {@code rootUid} up to {@code depth} edges,
	 * mirroring the 1.21.1 {@code FavoriteTreeBuilder.build} semantics.
	 *
	 * <p>An input ingredient of a recipe is resolved through the INPUT RecipeMap
	 * to the recipes that consume it. Following {@code FavoriteTreeBuilder},
	 * "favorited" (expandable) targets exclude the recipe itself — the recipe's
	 * own input slot is not a favorite of itself — and an input whose only
	 * consumer is the recipe itself is an un-favorited (private) input: it does
	 * not produce any child. A non-root recipe that carries any private input is
	 * a terminal node: it is reachable itself but does not expand further
	 * (mirroring {@code resolveTreeRecipe} where an input without a favorite
	 * recipe yields no children). The root recipe always expands.
	 *
	 * @return the reachable recipe uids
	 */
	public Set<String> expand(String rootUid, int depth) {
		Set<String> visited = new LinkedHashSet<>();
		if (!recipesByUid.containsKey(rootUid)) {
			return visited;
		}

		Deque<Entry> queue = new ArrayDeque<>();
		queue.add(new Entry(rootUid, 0));
		visited.add(rootUid);

		while (!queue.isEmpty()) {
			Entry entry = queue.poll();
			if (entry.depth >= depth) {
				continue;
			}
			TestRecipe recipe = recipesByUid.get(entry.uid);
			if (recipe == null) {
				continue;
			}
			boolean isRoot = entry.uid.equals(rootUid);
			List<TestRecipe> nextRecipes = new ArrayList<>();
			boolean hasPrivateInput = false;
			for (TestIngredient input : recipe.inputs()) {
				boolean anyOtherConsumer = false;
				for (TestRecipe consumer : getConsumers(input)) {
					if (consumer.uid().equals(entry.uid)) {
						continue; // the recipe's own input slot is not a favorite
					}
					anyOtherConsumer = true;
					if (!visited.contains(consumer.uid())) {
						nextRecipes.add(consumer);
					}
				}
				if (!anyOtherConsumer) {
					hasPrivateInput = true;
				}
			}
			// Pruning: a non-root recipe with a private input (an ingredient
			// consumed by no recipe other than itself) is a terminal node.
			if (!isRoot && hasPrivateInput) {
				continue;
			}
			for (TestRecipe consumer : nextRecipes) {
				if (visited.add(consumer.uid())) {
					queue.add(new Entry(consumer.uid(), entry.depth + 1));
				}
			}
		}
		return visited;
	}

	public int getIndexedCount() {
		return recipesByUid.size();
	}

	private List<TestRecipe> getConsumers(TestIngredient ingredient) {
		Optional<ITypedIngredient<TestIngredient>> typed = ingredientManager.createTypedIngredient(TestIngredient.TYPE, ingredient);
		if (typed.isEmpty()) {
			return List.of();
		}
		return inputRecipeMap.getRecipes(RECIPE_TYPE, typed.get());
	}

	/** Result of {@link #create(List)}: the engine. */
	public record EngineResult(JeiRecipeTreeEngine engine) {
	}

	private record Entry(String uid, int depth) {
	}
}
