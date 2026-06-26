/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.chain;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Minimal data-level equivalent of GTNH NEI AutoCraftingManager.runProcessing().
 */
public final class AutoCraftingManager {
	private static final int MAX_RECIPE_BATCH = 64;

	private AutoCraftingManager() {
	}

	public static Result run(
		RecipeChainMath math,
		List<RecipeChainInput> initialItems,
		Supplier<List<RecipeChainInput>> inventory,
		RecipeExecutor executor
	) {
		return run(math, initialItems, inventory, executor, () -> false);
	}

	public static Result run(
		RecipeChainMath math,
		List<RecipeChainInput> initialItems,
		Supplier<List<RecipeChainInput>> inventory,
		RecipeExecutor executor,
		BooleanSupplier interrupted
	) {
		math.createMasterRoot();
		boolean processed = false;
		boolean changed;
		int craftedRecipes = 0;

		do {
			changed = false;
			RecipeChainIterator iterator = new RecipeChainIterator(math, initialItems);
			iterator.updateInventory(inventory.get());

			while (iterator.hasNext() && !interrupted.getAsBoolean()) {
				Map<ResourceLocation, Long> recipes = iterator.next();
				boolean craftedBatch = false;

				for (Map.Entry<ResourceLocation, Long> entry : recipes.entrySet()) {
					if (interrupted.getAsBoolean()) {
						break;
					}
					long multiplier = entry.getValue();
					while (multiplier > 0 && !interrupted.getAsBoolean()) {
						int batch = (int) Math.min(MAX_RECIPE_BATCH, multiplier);
						if (!executor.craft(entry.getKey(), batch)) {
							break;
						}
						multiplier -= MAX_RECIPE_BATCH;
						craftedRecipes++;
						craftedBatch = true;
					}
				}

				if (craftedBatch) {
					changed = true;
					processed = true;
					iterator.updateInventory(inventory.get());
				}
			}
		} while (changed && !interrupted.getAsBoolean());

		return new Result(processed, processed && !changed && !interrupted.getAsBoolean(), craftedRecipes);
	}

	@FunctionalInterface
	public interface RecipeExecutor {
		boolean craft(ResourceLocation recipeUid, int multiplier);
	}

	public record Result(
		boolean processed,
		boolean completed,
		int craftedRecipes
	) {
	}
}
