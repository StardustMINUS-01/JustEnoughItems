package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.resources.ResourceLocation;

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
	Set<Integer> remainderItems
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
	}
}
