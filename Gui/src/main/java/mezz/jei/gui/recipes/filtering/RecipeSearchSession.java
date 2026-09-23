package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public final class RecipeSearchSession {
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final ISearchStorageBuilderFactory searchStorageBuilderFactory;
	private @Nullable RecipeSearchTraversal traversal;
	private @Nullable RecipePreferenceRules preferenceRules;
	private RecipeFilterMode mode = RecipeFilterMode.ALL;
	private RecipeSearchQuery query = RecipeSearchQuery.parse("");
	private long revision;
	private long started;
	private int ticksSincePublication;
	private int publishedMatchCount = -1;
	private @Nullable ForkJoinPool executor;
	private @Nullable Future<?> pending;
	private long pendingRevision;

	public RecipeSearchSession(IRecipeManager recipeManager, IIngredientManager ingredientManager, ISearchStorageBuilderFactory searchStorageBuilderFactory) {
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.searchStorageBuilderFactory = searchStorageBuilderFactory;
	}

	public void request(ILookupState state, RecipeFilterMode mode, RecipeSearchQuery query) {
		cancel();
		preferenceRules = null;
		this.mode = mode;
		this.query = query;
		if (mode == RecipeFilterMode.ALL && query.isEmpty())
			return;
		traversal = new RecipeSearchTraversal(recipeManager, ingredientManager, state, mode, query,
			RecipeSearchTextMatcher.createFactory(searchStorageBuilderFactory, query));
		started = System.nanoTime();
	}

	public boolean isSearching() {
		return traversal != null;
	}

	public boolean shouldShowProgress() {
		return isSearching() && System.nanoTime() - started >= 200_000_000L;
	}

	public boolean needsPreferenceRefresh(RecipePreferenceRules rules) {
		return mode != RecipeFilterMode.ALL && preferenceRules != null && preferenceRules != rules;
	}

	public Optional<Result> tick(RecipePreferenceRules rules) {
		if (traversal == null) {
			if (pending != null && pending.isDone())
				pending = null;
			return Optional.empty();
		}
		if (pending != null && pendingRevision != revision) {
			if (!pending.isDone())
				return Optional.empty();
			pending = null;
		}
		preferenceRules = rules;
		if (pending == null) {
			if (executor == null) {
				int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() * 2 / 3);
				executor = new ForkJoinPool(parallelism, pool -> {
					var worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
					worker.setName("JEI Recipe Search-" + worker.getPoolIndex());
					return worker;
				}, null, false);
			}
			RecipeSearchTraversal work = traversal;
			int parallelism = executor.getParallelism();
			pending = executor.submit(() -> work.scan(rules, parallelism));
			pendingRevision = revision;
		}
		ticksSincePublication++;
		boolean complete = pending.isDone();
		if (complete) {
			try {
				pending.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				cancel();
				return Optional.empty();
			} catch (ExecutionException e) {
				cancel();
				throw new IllegalStateException("Recipe search failed", e.getCause());
			} finally {
				pending = null;
			}
		}
		int count = traversal.advanceCompletedBatches();
		if (complete || ticksSincePublication >= 10 && count != publishedMatchCount) {
			publishedMatchCount = count;
			ticksSincePublication = 0;
			Result result = new Result(revision, query, traversal.getMatcher(), traversal.getResults());
			if (complete)
				traversal = null;
			return Optional.of(result);
		}
		return Optional.empty();
	}

	public void cancel() {
		revision++;
		if (traversal != null)
			traversal.cancel();
		traversal = null;
		ticksSincePublication = 0;
		publishedMatchCount = -1;
	}

	public void clear() {
		cancel();
		preferenceRules = null;
		if (executor != null)
			executor.shutdownNow();
		executor = null;
		pending = null;
	}

	public record Result(long revision, RecipeSearchQuery query, IRecipeSearchTextMatcher matcher, List<IFocusedRecipes<?>> recipes) {}
}
