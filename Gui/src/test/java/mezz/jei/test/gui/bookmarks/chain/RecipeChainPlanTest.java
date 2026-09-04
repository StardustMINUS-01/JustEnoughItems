package mezz.jei.test.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainPlan;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class RecipeChainPlanTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath("test", "plate");
	private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void compilesAcyclicDependenciesIntoMiddleAndOutputRecipes() {
		RecipeChainInput plateResult = input(0, recipe(PLATE, BookmarkItemType.RESULT, "plate"));
		RecipeChainInput plateInput = input(1, recipe(PLATE, BookmarkItemType.INGREDIENT, "ingot"));
		RecipeChainInput machineResult = input(2, recipe(MACHINE, BookmarkItemType.RESULT, "machine"));
		RecipeChainInput machineInput = input(3, recipe(MACHINE, BookmarkItemType.INGREDIENT, "plate"));

		RecipeChainPlan plan = RecipeChainPlan.compile(List.of(plateResult, plateInput, machineResult, machineInput), Set.of());

		Assertions.assertEquals(Set.of(MACHINE), plan.outputRecipes().keySet());
		Assertions.assertEquals(plateResult, plan.preferredResults().get(machineInput));
	}

	@Test
	public void breaksCyclesAtTheEarliestRecipeAndLeavesTheBackEdgeExternal() {
		RecipeChainInput firstResult = input(0, recipe(PLATE, BookmarkItemType.RESULT, "plate"));
		RecipeChainInput firstInput = input(1, recipe(PLATE, BookmarkItemType.INGREDIENT, "machine"));
		RecipeChainInput secondResult = input(2, recipe(MACHINE, BookmarkItemType.RESULT, "machine"));
		RecipeChainInput secondInput = input(3, recipe(MACHINE, BookmarkItemType.INGREDIENT, "plate"));

		RecipeChainPlan plan = RecipeChainPlan.compile(List.of(firstResult, firstInput, secondResult, secondInput), Set.of());

		Assertions.assertEquals(Set.of(PLATE), plan.outputRecipes().keySet());
		Assertions.assertEquals(secondResult, plan.preferredResults().get(firstInput));
		Assertions.assertFalse(plan.preferredResults().containsKey(secondInput));
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata recipe(ResourceLocation recipeUid, BookmarkItemType type, String item) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(new BookmarkIngredientKey("test:item", item))
		);
	}
}
