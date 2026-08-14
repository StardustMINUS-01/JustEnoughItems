package mezz.jei.test.bench;

import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.Map;

/**
 * A single recipe-tree expansion scenario, mirroring the Thunderbolt
 * {@code ReferenceScenario}. Uses the real JEI 1.21.1 recipe-tree engine
 * ({@link FavoriteTreeBuilder}) ported to 1.20.1.
 *
 * @param id              stable identifier, e.g. {@code chain/unbounded}
 * @param family          the graph family under test
 * @param dataMode        MISSING / MINIMUM / UNBOUNDED
 * @param graphSize       number of recipes in the graph
 * @param depth           expansion depth passed to the engine
 * @param root            root recipe to expand
 * @param graph           the resolved recipe graph (recipe -&gt; inputs)
 * @param store           favorite store backing the expansion
 * @param expectedRecipes expected number of recipes in the expanded tree
 */
public record JeiRecipeTreeScenario(
	String id,
	JeiTreeFamily family,
	JeiDataMode dataMode,
	int graphSize,
	int depth,
	FocusedRecipe root,
	Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph,
	FavoriteRecipeStore store,
	int expectedRecipes
) {
}
