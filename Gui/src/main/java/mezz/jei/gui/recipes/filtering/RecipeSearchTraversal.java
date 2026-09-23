package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

final class RecipeSearchTraversal {
	private static final int RECIPES_PER_BATCH = 64;
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final RecipeFilterMode mode;
	private final RecipeSearchQuery query;
	private final Supplier<IRecipeSearchTextMatcher> matcherFactory;
	private final IRecipeSearchTextMatcher displayMatcher;
	private final List<Batch<?>> batches = new ArrayList<>();
	private final AtomicReferenceArray<List<?>> results;
	private volatile boolean cancelled;
	private int publishedBatches;
	private int matchCount;
	private int assembledBatches;
	private final List<IFocusedRecipes<?>> completedResults = new ArrayList<>();
	private final List<Object> categoryResults = new ArrayList<>();

	RecipeSearchTraversal(IRecipeManager recipeManager, IIngredientManager ingredientManager,
		ILookupState state, RecipeFilterMode mode, RecipeSearchQuery query, Supplier<IRecipeSearchTextMatcher> matcherFactory) {
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.mode = mode;
		this.query = query;
		this.matcherFactory = matcherFactory;
		this.displayMatcher = matcherFactory.get();
		// Capture the lookup state on the client thread; workers only traverse these fixed recipe lists.
		for (IRecipeCategory<?> category : state.getRecipeCategories())
			addBatches(state.getFocusedRecipes(category));
		this.results = new AtomicReferenceArray<>(batches.size());
	}

	private <T> void addBatches(IFocusedRecipes<T> focused) {
		List<T> recipes = List.copyOf(focused.getRecipes());
		for (int start = 0; start < recipes.size(); start += RECIPES_PER_BATCH)
			batches.add(new Batch<>(focused.getRecipeCategory(), recipes.subList(start, Math.min(start + RECIPES_PER_BATCH, recipes.size()))));
	}

	public void scan(RecipePreferenceRules rules, int parallelism) {
		try {
			Set<FocusedRecipe> preferred;
			if (mode == RecipeFilterMode.ALL) {
				preferred = Set.of();
			} else {
				AtomicReferenceArray<List<RecipePreferenceCandidate>> candidates = new AtomicReferenceArray<>(batches.size());
				runWorkers(parallelism, () -> index -> candidates.set(index, collectPreferences(batches.get(index))));
				checkCancelled();
				List<RecipePreferenceCandidate> all = new ArrayList<>();
				for (int i = 0; i < candidates.length(); i++)
					all.addAll(candidates.get(i));
				preferred = Set.copyOf(rules.resolvePreferredRecipes(all));
			}
			checkCancelled();
			runWorkers(parallelism, () -> {
				IRecipeSearchTextMatcher matcher = matcherFactory.get();
				Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts = new HashMap<>();
				return index -> results.set(index, match(batches.get(index), preferred, matcher, catalysts));
			});
		} catch (RuntimeException | Error e) {
			cancelled = true;
			throw e;
		}
	}

	private void runWorkers(int parallelism, Supplier<IntConsumer> workerFactory) {
		AtomicInteger next = new AtomicInteger();
		IntStream.range(0, Math.min(parallelism, batches.size())).parallel().forEach(worker -> {
			IntConsumer action = workerFactory.get();
			int index;
			while ((index = next.getAndIncrement()) < batches.size()) {
				checkCancelled();
				action.accept(index);
			}
		});
	}

	private <T> List<RecipePreferenceCandidate> collectPreferences(Batch<T> batch) {
		List<RecipePreferenceCandidate> candidates = new ArrayList<>();
		for (T recipe : batch.recipes()) {
			checkCancelled();
			var id = batch.category().getRegistryName(recipe);
			if (id != null) {
				var materials = recipeManager.getRecipeIngredients(batch.category(), recipe);
				candidates.add(new RecipePreferenceCandidate(new FocusedRecipe(batch.category().getRecipeType().getUid(), id),
					createMatchInfo(materials.getIngredients(RecipeIngredientRole.INPUT)), createMatchInfo(materials.getIngredients(RecipeIngredientRole.OUTPUT))));
			}
		}
		return candidates;
	}

	private <T> List<T> match(Batch<T> batch, Set<FocusedRecipe> preferred, IRecipeSearchTextMatcher matcher,
		Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts) {
		List<T> matches = new ArrayList<>();
		for (T recipe : batch.recipes()) {
			checkCancelled();
			IRecipeCategory<T> category = batch.category();
			if (mode != RecipeFilterMode.ALL) {
				var id = category.getRegistryName(recipe);
				boolean selected = id != null && preferred.contains(new FocusedRecipe(category.getRecipeType().getUid(), id));
				if (selected != (mode == RecipeFilterMode.PREFERRED))
					continue;
			}
			if (query.matches(category, recipe, recipeManager, ingredientManager,
				() -> catalysts.computeIfAbsent(category, c -> recipeManager.createRecipeCatalystLookup(c.getRecipeType()).get().toList()), matcher))
				matches.add(recipe);
		}
		return List.copyOf(matches);
	}

	private static List<IngredientMatchInfo> createMatchInfo(List<ITypedIngredient<?>> ingredients) {
		return ingredients.stream().map(IngredientMatchInfo::fromIngredient).flatMap(java.util.Optional::stream).toList();
	}

	public void cancel() {
		cancelled = true;
	}

	private void checkCancelled() {
		if (cancelled || Thread.currentThread().isInterrupted())
			throw new CancellationException();
	}

	public int advanceCompletedBatches() {
		while (publishedBatches < results.length()) {
			List<?> batch = results.get(publishedBatches);
			if (batch == null)
				break;
			matchCount += batch.size();
			publishedBatches++;
		}
		return matchCount;
	}

	public IRecipeSearchTextMatcher getMatcher() {
		return displayMatcher;
	}

	public List<IFocusedRecipes<?>> getResults() {
		while (assembledBatches < publishedBatches) {
			IRecipeCategory<?> category = batches.get(assembledBatches).category();
			categoryResults.addAll(results.get(assembledBatches));
			results.set(assembledBatches++, null);
			if (assembledBatches == batches.size() || batches.get(assembledBatches).category() != category) {
				if (!categoryResults.isEmpty())
					completedResults.add(copyMatches(category, categoryResults));
				categoryResults.clear();
			}
		}
		List<IFocusedRecipes<?>> focused = new ArrayList<>(completedResults);
		if (!categoryResults.isEmpty())
			focused.add(copyMatches(batches.get(assembledBatches - 1).category(), categoryResults));
		return List.copyOf(focused);
	}

	@SuppressWarnings("unchecked")
	private static <T> IFocusedRecipes<T> copyMatches(IRecipeCategory<T> category, List<Object> recipes) {
		return new StaticFocusedRecipes<>(category, (List<T>) List.copyOf(recipes));
	}

	private record Batch<T>(IRecipeCategory<T> category, List<T> recipes) {}
}
