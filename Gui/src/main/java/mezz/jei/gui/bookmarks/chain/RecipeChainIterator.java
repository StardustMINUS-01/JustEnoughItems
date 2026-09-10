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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derived from GTNH NotEnoughItems RecipeChainIterator.
 */
public final class RecipeChainIterator implements Iterator<Map<ResourceLocation, Long>> {
	private final List<RecipeChainInput> inventory = new ArrayList<>();
	private final Set<ResourceLocation> processedRecipes = new HashSet<>();
	private final Set<ResourceLocation> precessedRecipes = new HashSet<>();
	private final RecipeChainMath math;
	private final List<RecipeChainInput> initialItems;
	private Set<ResourceLocation> topRecipes;

	public RecipeChainIterator(RecipeChainMath math, List<RecipeChainInput> initialItems) {
		this.math = math;
		this.topRecipes = math.outputRecipesSnapshot();
		this.initialItems = List.copyOf(initialItems);
		this.processedRecipes.addAll(this.topRecipes);
	}

	public void updateInventory(Collection<RecipeChainInput> inventory) {
		this.inventory.clear();
		this.inventory.addAll(inventory);
	}

	@Override
	public Map<ResourceLocation, Long> next() {
		refreshInitialItems();
		RecipeChainDetails details = math.refreshDetails();

		Set<ResourceLocation> skipRecipes = new HashSet<>();
		Map<ResourceLocation, Long> rootRecipes = new LinkedHashMap<>();
		Map<RecipeChainInput, RecipeChainInput> preferredItems = new LinkedHashMap<>(math.preferredItemsSnapshot());
		preferredItems.values().removeIf(item -> {
			ResourceLocation recipeUid = item.metadata().recipeUid();
			return recipeUid == null ||
				!hasCalculatedMultiplier(item, details) ||
				this.precessedRecipes.contains(recipeUid);
		});
		preferredItems.keySet().removeIf(item -> !hasCalculatedAmount(item, details));

		Set<RecipeChainInput> referencedByPendingRecipes = new HashSet<>();
		for (Map.Entry<RecipeChainInput, RecipeChainInput> entry : preferredItems.entrySet()) {
			if (!this.processedRecipes.contains(entry.getKey().metadata().recipeUid())) {
				referencedByPendingRecipes.add(entry.getValue());
			}
		}

		for (Map.Entry<RecipeChainInput, RecipeChainInput> entry : preferredItems.entrySet()) {
			RecipeChainInput keyItem = entry.getKey();
			ResourceLocation keyRecipe = keyItem.metadata().recipeUid();
			if (keyRecipe != null && this.topRecipes.contains(keyRecipe) && !skipRecipes.contains(keyRecipe)) {
				RecipeChainInput item = entry.getValue();
				ResourceLocation itemRecipe = item.metadata().recipeUid();
				if (itemRecipe == null) {
					continue;
				}

				if (referencedByPendingRecipes.contains(item)) {
					skipRecipes.add(itemRecipe);
				} else {
					long multiplier = details.calculatedItems()
						.get(item.index())
						.calculatedMultiplier();
					rootRecipes.merge(itemRecipe, multiplier, Math::max);
				}
			}
		}

		for (ResourceLocation recipeUid : skipRecipes) {
			rootRecipes.remove(recipeUid);
		}

		this.processedRecipes.addAll(rootRecipes.keySet());
		rootRecipes.values().removeIf(amount -> amount == 0);
		this.topRecipes = Set.copyOf(rootRecipes.keySet());
		this.precessedRecipes.addAll(this.topRecipes);

		return rootRecipes;
	}

	@Override
	public boolean hasNext() {
		return !this.topRecipes.isEmpty();
	}

	private void refreshInitialItems() {
		List<RecipeChainInput> inputs = new ArrayList<>(this.initialItems);
		inputs.addAll(this.inventory);
		this.math.replaceInitialItems(inputs);
	}

	private static boolean hasCalculatedMultiplier(RecipeChainInput input, RecipeChainDetails details) {
		RecipeChainItem item = details.calculatedItems().get(input.index());
		return item != null && item.calculatedMultiplier() > 0;
	}

	private static boolean hasCalculatedAmount(RecipeChainInput input, RecipeChainDetails details) {
		RecipeChainItem item = details.calculatedItems().get(input.index());
		return item != null && item.calculatedAmount() > 0;
	}
}
