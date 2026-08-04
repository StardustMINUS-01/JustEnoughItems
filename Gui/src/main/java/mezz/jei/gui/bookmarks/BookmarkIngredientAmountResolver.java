package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.ReflectionCache;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;

public final class BookmarkIngredientAmountResolver {
	private BookmarkIngredientAmountResolver() {
	}

	public static <T> long getAmount(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager) {
		return getExplicitAmount(ingredient, ingredientManager).orElse(1);
	}

	public static <T> OptionalLong getExplicitAmount(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager) {
		try {
			IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient.getType());
			long amount = ingredientHelper.getAmount(ingredient.getIngredient());
			if (amount > 0) {
				return OptionalLong.of(amount);
			}
		} catch (RuntimeException ignored) {
		}
		long reflectedAmount = getReflectedAmount(ingredient.getIngredient());
		if (reflectedAmount > 0) {
			return OptionalLong.of(reflectedAmount);
		}
		return OptionalLong.empty();
	}

	private static long getReflectedAmount(Object ingredient) {
		try {
			Optional<Method> getAmount = ReflectionCache.findMethod(ingredient.getClass(), "getAmount");
			if (getAmount.isEmpty()) {
				return -1;
			}
			Object amount = getAmount.get().invoke(ingredient);
			if (amount instanceof Number number) {
				return number.longValue();
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return -1;
	}
}
