package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the display amount of a typed ingredient, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver}).
 *
 * <p>The amount is resolved through {@link IIngredientHelper#getAmount} first,
 * and falls back to a reflective {@code getAmount()} call on the ingredient
 * itself. The 1.21.1 version uses {@code ReflectionCache}; 1.20.1 has no such
 * class, so a small per-class {@link Method} cache is inlined here instead.
 */
public final class BookmarkIngredientAmountResolver {
	private static final Map<Class<?>, Optional<Method>> GET_AMOUNT_METHODS = new ConcurrentHashMap<>();

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
			Optional<Method> getAmount = findGetAmountMethod(ingredient.getClass());
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

	private static Optional<Method> findGetAmountMethod(Class<?> ingredientClass) {
		return GET_AMOUNT_METHODS.computeIfAbsent(ingredientClass, clazz -> {
			try {
				return Optional.of(clazz.getMethod("getAmount"));
			} catch (NoSuchMethodException ignored) {
				return Optional.empty();
			}
		});
	}
}
