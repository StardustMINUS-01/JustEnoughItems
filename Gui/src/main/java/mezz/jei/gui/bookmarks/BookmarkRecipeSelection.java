package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.recipes.InputSlotSelectionState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal adapter so recipe-tree previews compile without the 1.21 candidate stack. */
public final class BookmarkRecipeSelection {
	private final IRecipeLayoutDrawable<?> layout;
	private final InputSlotSelectionState selections;

	public BookmarkRecipeSelection(IRecipeLayoutDrawable<?> layout, List<RecipeChainInput> saved, IIngredientManager manager) {
		this(layout, saved, manager, Map.of());
	}

	public BookmarkRecipeSelection(IRecipeLayoutDrawable<?> layout, List<RecipeChainInput> saved, IIngredientManager manager,
		Map<Integer, BookmarkIngredientKey> previousChoices) {
		this.layout = layout;
		this.selections = new InputSlotSelectionState(manager);
		if (!previousChoices.isEmpty()) {
			selections.setSelectedKeys(previousChoices);
		}
		selections.apply(layout);
	}

	public Map<Integer, BookmarkIngredientKey> selectedKeys() {
		return selections.currentSelections(layout);
	}

	public Optional<RecipeChainInput> source(IRecipeSlotView slot) {
		return Optional.empty();
	}

	public List<ITypedIngredient<?>> getCandidates(IRecipeSlotView slot) {
		return slot.getAllIngredients().toList();
	}

	public boolean select(IRecipeSlotView slot, ITypedIngredient<?> ingredient, boolean synchronize, BookmarkList bookmarks) {
		return false;
	}

	public boolean scroll(double x, double y, double delta, boolean synchronize, BookmarkList bookmarks) {
		return false;
	}
}
