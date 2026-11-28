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
		if (selected != null)
			warmers.execute(() -> warmCategory(selected));
		if (crafting != null && crafting != selected)
			warmers.execute(() -> warmCategory(crafting));
		warmers.execute(() -> categories.parallelStream()
			.filter(category -> category != selected && category != crafting && !stopped)
			.forEach(this::warmCategory));
	}

	private void warmCategory(IRecipeCategory<?> category) {
		try {
			var index = build(category, 512);
			if (index != null)
				index.paths(this::checkActive);
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

	@SuppressWarnings("unchecked")
	<T> CompactRecipeIndex<T> build(IRecipeCategory<T> category, int minimumSize) {
		var slot = slots.get(category);
		if (slot == null)
			return null;
		synchronized (slot) {
			checkActive();
			if (slot.index == null) {
				var registered = materials.getRegisteredRecipes(category, minimumSize);
				if (registered.isEmpty() && minimumSize > 0)
					return null;
				slot.index = new CompactRecipeIndex<>(category, registered, materials, orphans, this::checkActive);
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
