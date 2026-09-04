package mezz.jei.gui.bookmarks.chain;

import java.util.List;

@FunctionalInterface
public interface RecipeChainTooltipInventoryProvider {
	List<RecipeChainInput> getInventoryInputs(int groupId, int firstSyntheticIndex);

	static RecipeChainTooltipInventoryProvider empty() {
		return (groupId, firstSyntheticIndex) -> List.of();
	}
}
