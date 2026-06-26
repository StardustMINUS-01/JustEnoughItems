package mezz.jei.gui.overlay.elements;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.util.FocusUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class LayoutPlaceholderElement implements IElement<Object> {
	public static final LayoutPlaceholderElement INSTANCE = new LayoutPlaceholderElement();

	private LayoutPlaceholderElement() {
	}

	@Override
	public ITypedIngredient<Object> getTypedIngredient() {
		throw new UnsupportedOperationException("Layout placeholders do not have ingredients");
	}

	@Override
	public Optional<mezz.jei.gui.bookmarks.IBookmark> getBookmark() {
		return Optional.empty();
	}

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return null;
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
	}

	@Override
	public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<Object> ingredientRenderer, IIngredientHelper<Object> ingredientHelper) {
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public boolean isLayoutPlaceholder() {
		return true;
	}
}
