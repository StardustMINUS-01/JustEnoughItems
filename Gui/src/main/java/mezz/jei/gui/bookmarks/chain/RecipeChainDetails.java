/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RecipeChainDetails(
	Map<Integer, RecipeChainItem> calculatedItems,
	Map<Integer, ResourceLocation> itemToRecipe,
	Set<ResourceLocation> outputRecipes,
	Set<ResourceLocation> middleRecipes,
	Map<ResourceLocation, Set<ResourceLocation>> recipeRelations,
	Map<BookmarkIngredientKey, Long> missedItems,
	Map<BookmarkIngredientKey, Long> containerItems,
	Set<Integer> initialItems,
	Set<Integer> missingIngredients,
	Set<Integer> remainderItems,
	Map<ResourceLocation, CollapsedBlock> collapsedBlocks
) {
	public RecipeChainDetails {
		calculatedItems = Map.copyOf(calculatedItems);
		itemToRecipe = Map.copyOf(itemToRecipe);
		outputRecipes = Set.copyOf(outputRecipes);
		middleRecipes = Set.copyOf(middleRecipes);
		recipeRelations = Map.copyOf(recipeRelations);
		missedItems = Map.copyOf(missedItems);
		containerItems = Map.copyOf(containerItems);
		initialItems = Set.copyOf(initialItems);
		missingIngredients = Set.copyOf(missingIngredients);
		remainderItems = Set.copyOf(remainderItems);
		collapsedBlocks = Map.copyOf(collapsedBlocks);
	}

	public record CollapsedBlockItem(
		int sourceIndex,
		BookmarkItemMetadata metadata,
		RecipeChainItem chainItem,
		boolean anchor
	) {
	}

	public record CollapsedBlock(
		ResourceLocation recipeUid,
		List<CollapsedBlockItem> items
	) {
	}
}
