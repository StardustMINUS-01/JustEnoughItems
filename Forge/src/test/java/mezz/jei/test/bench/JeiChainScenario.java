package mezz.jei.test.bench;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single expansion-chain scenario: the bookmark inputs (the recipe chain the
 * player favorited), the inventory snapshot at shift+C time, the collapsed
 * recipes, the expected crafted amounts per recipe uid, and the recipe
 * definitions the simulated executor uses to craft.
 *
 * <p>The {@code expectedCrafted} map is the ground truth for the "craft the
 * set quantity regardless of inventory" semantics: shift+C must produce
 * exactly the requested batch of the final result even when the inventory
 * already holds a full batch (the Bug6 regression this suite guards).</p>
 *
 * @param id               stable identifier, e.g. {@code inventory/full-batch}
 * @param family           the chain family under test
 * @param dataMode         MISSING / MINIMUM / UNBOUNDED inventory material mode
 * @param graphSize        number of recipes in the chain graph
 * @param depth            chain depth (recipes from raw material to final result)
 * @param inputs           the favorited recipe-chain inputs (RecipeChainMath.of)
 * @param inventory        the inventory snapshot passed to expandRootDemandForCraftAll
 * @param collapsedRecipes collapsed recipe uids (empty for these scenarios)
 * @param expectedCrafted  expected crafted amounts per recipe uid
 * @param recipes          recipe definitions driving the simulated executor
 */
public record JeiChainScenario(
	String id,
	JeiChainFamily family,
	JeiDataMode dataMode,
	int graphSize,
	int depth,
	List<RecipeChainInput> inputs,
	List<RecipeChainInput> inventory,
	Set<ResourceLocation> collapsedRecipes,
	Map<ResourceLocation, Long> expectedCrafted,
	Map<ResourceLocation, ChainRecipeDef> recipes
) {
	public JeiChainScenario {
		inputs = List.copyOf(inputs);
		inventory = List.copyOf(inventory);
		collapsedRecipes = Set.copyOf(collapsedRecipes);
		expectedCrafted = Map.copyOf(expectedCrafted);
		recipes = Map.copyOf(recipes);
	}

	/**
	 * A recipe definition for the simulated executor: consumed ingredients,
	 * non-consumed (catalyst) ingredients, and produced outputs.
	 */
	public record ChainRecipeDef(List<ChainIngredientDef> ingredients, List<ChainOutputDef> outputs) {
		public ChainRecipeDef {
			ingredients = List.copyOf(ingredients);
			outputs = List.copyOf(outputs);
		}
	}

	/**
	 * One ingredient of a recipe. {@code consumed} is false for catalysts:
	 * the executor only checks presence (amount &gt;= {@code amount}) without
	 * removing the item.
	 */
	public record ChainIngredientDef(BookmarkIngredientKey key, long amount, boolean consumed) {
	}

	/**
	 * One output of a recipe, produced {@code amount} times per craft.
	 */
	public record ChainOutputDef(BookmarkIngredientKey key, long amount) {
	}
}