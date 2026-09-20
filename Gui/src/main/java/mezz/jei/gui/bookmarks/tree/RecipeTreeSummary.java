package mezz.jei.gui.bookmarks.tree;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RecipeTreeSummary {
	private RecipeTreeSummary() {}

	public static RecipeChainTooltipModel create(List<RecipeChainInput> inputs, Optional<RecipeChainDetails> details,
		Set<ResourceLocation> collapsed, List<RecipeChainInput> inventory, IIngredientManager ingredients) {
		// Account for stock without expanding the group's saved output target or reading keyboard modifiers.
		var totals = RecipeChainTooltipModel.create(inputs, details, collapsed, List.of(), false, false, ingredients);
		var model = RecipeChainTooltipModel.create(inputs, details, collapsed, inventory, true, true, ingredients);
		List<RecipeChainTooltipModel.Section> sections = new ArrayList<>(totals.sections());
		for (var type : List.of(RecipeChainTooltipSectionType.MISSING,
			RecipeChainTooltipSectionType.NEEDED, RecipeChainTooltipSectionType.AVAILABLE, RecipeChainTooltipSectionType.REMAINDER)) {
			var section = model.sections().stream().filter(value -> value.type() == type).findFirst();
			if (section.isPresent()) {
				sections.add(section.get());
			} else if (type == RecipeChainTooltipSectionType.MISSING || type == RecipeChainTooltipSectionType.AVAILABLE || type == RecipeChainTooltipSectionType.REMAINDER) {
				sections.add(new RecipeChainTooltipModel.Section(type, List.of()));
			}
		}
		return new RecipeChainTooltipModel(sections);
	}
}
