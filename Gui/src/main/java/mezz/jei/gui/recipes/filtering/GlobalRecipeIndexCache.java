package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.recipes.IRecipeMaterialCacheInternal;
import mezz.jei.gui.search.IngredientSearchSnapshot;
import org.apache.logging.log4j.LogManager;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;

final class GlobalRecipeIndexCache {
	private final IRecipeMaterialCacheInternal materials;
	final mezz.jei.gui.search.OrphanIngredientCorpus orphans;
	private final List<IRecipeCategory<?>> categories;
	private final IRecipeCategory<?> crafting;
	private final IdentityHashMap<IRecipeCategory<?>, Slot> slots = new IdentityHashMap<>();
	final long materialRevision;
	final long identityRevision;
	private volatile boolean stopped;
	private enum Warmup { NONE, IDLE, PAGE }
	private volatile Warmup warmup = Warmup.NONE;

	boolean isIdleWarmup() { return warmup == Warmup.IDLE; }
	void disableWarmup() { warmup = Warmup.NONE; }

	static final class IdleBudget {
		int rows = 200_000;
		long associations = 4_000_000;

		void countRow(long width) {
			if (width > 16_384 || (associations -= width) < 0)
				throw new CancellationException();
		}
	}
	private IdleBudget idleBudget;

	void warmIdle() {
		idleBudget = new IdleBudget();
		warmup = Warmup.IDLE;
		warmers.execute(() -> {
			for (var category : categories) {
				if (stopped || !isIdleWarmup() || idleBudget.rows <= 0 || idleBudget.associations <= 0)
					return;
				warmCategory(category, Warmup.IDLE);
			}
		});
	}
	private final ForkJoinPool warmers = new ForkJoinPool(Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 4), pool -> {
		var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
		thread.setName("JEI Recipe Warmup-" + thread.getPoolIndex());
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	}, null, true);

	GlobalRecipeIndexCache(IRecipeMaterialCacheInternal materials, IngredientSearchSnapshot identities, long revision, long identityRevision) {
		this.materials = materials;
		this.orphans = new mezz.jei.gui.search.OrphanIngredientCorpus(identities);
		this.materialRevision = revision;
		this.identityRevision = identityRevision;
		categories = List.copyOf(materials.getRegisteredCategories());
		IRecipeCategory<?> crafting = null;
		for (var category : categories) {
			slots.put(category, new Slot());
			if (RecipeTypes.CRAFTING.equals(category.getRecipeType()))
				crafting = category;
		}
		this.crafting = crafting;
	}

	private static final class Slot {
		volatile CompactRecipeIndex<?> index;
	}

	void warm(IRecipeCategory<?> selected) {
		if (warmup == Warmup.PAGE)
			return;
		warmup = Warmup.PAGE;
		if (selected != null)
			warmers.execute(() -> warmCategory(selected, Warmup.PAGE));
		if (crafting != null && crafting != selected)
			warmers.execute(() -> warmCategory(crafting, Warmup.PAGE));
		warmers.execute(() -> categories.parallelStream()
			.filter(category -> category != selected && category != crafting && !stopped)
			.forEach(category -> warmCategory(category, Warmup.PAGE)));
	}

	private void warmCategory(IRecipeCategory<?> category, Warmup mode) {
		try {
			Runnable checkpoint = () -> {
				checkActive();
				if (warmup != mode)
					throw new CancellationException();
			};
			checkpoint.run();
			if (mode == Warmup.IDLE && materials.getRegisteredRecipeCount(category) > Math.min(65_536, idleBudget.rows))
				return;
			var index = build(category, mode == Warmup.IDLE ? 1 : 512, checkpoint, mode == Warmup.IDLE);
			if (index != null && mode == Warmup.PAGE)
				index.paths(checkpoint);
		} catch (CancellationException ignored) {
			// The cache has been superseded.
		} catch (RuntimeException | LinkageError e) {
			LogManager.getLogger().warn("Failed to warm recipe index for {}", category.getRecipeType().getUid(), e);
		}
	}

	@SuppressWarnings("unchecked")
	<T> CompactRecipeIndex<T> ready(IRecipeCategory<T> category) {
		var slot = slots.get(category);
		return slot == null ? null : (CompactRecipeIndex<T>) slot.index;
	}

	<T> CompactRecipeIndex<T> build(IRecipeCategory<T> category, int minimumSize) {
		return build(category, minimumSize, this::checkActive, false);
	}

	@SuppressWarnings("unchecked")
	private <T> CompactRecipeIndex<T> build(IRecipeCategory<T> category, int minimumSize, Runnable checkpoint, boolean idle) {
		var slot = slots.get(category);
		if (slot == null)
			return null;
		synchronized (slot) {
			checkpoint.run();
			if (slot.index == null) {
				var registered = materials.getRegisteredRecipes(category, minimumSize);
				if (registered.isEmpty() && minimumSize > 0)
					return null;
				if (idle && registered.size() > Math.min(65_536, idleBudget.rows))
					return null;
				var built = new CompactRecipeIndex<>(category, registered, materials, orphans, checkpoint, idle ? idleBudget : null);
				checkpoint.run();
				if (idle && built.recipes.isEmpty())
					return null;
				slot.index = built;
				if (idle)
					idleBudget.rows -= built.recipes.size();
			}
			return (CompactRecipeIndex<T>) slot.index;
		}
	}

	void stop() {
		stopped = true;
		warmers.shutdownNow();
	}

	private void checkActive() {
		if (stopped || Thread.currentThread().isInterrupted())
			throw new CancellationException();
	}
}
