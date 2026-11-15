package mezz.jei.gui.bookmarks;

import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.RegistryAccess;

public class BookmarkFactory {
	private final IIngredientManager ingredientManager;

	public BookmarkFactory(ICodecHelper codecHelper, RegistryAccess registryAccess, IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
	}

	public <T> IngredientBookmark<T> create(ITypedIngredient<T> typedIngredient) {
		return IngredientBookmark.create(typedIngredient, ingredientManager);
	}
}
