package mezz.jei.test.bench;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestAmountIngredient;
import mezz.jei.test.lib.TestAmountIngredientHelper;
import mezz.jei.test.lib.TestAmountIngredientRenderer;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredient;
import mezz.jei.test.lib.TestIngredientHelper;
import mezz.jei.test.lib.TestIngredientRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Benchmark tests for the 1.20.1 port of the JEI 1.21.1
 * {@code BookmarkIngredientAmountResolver}: the ingredient-helper amount path,
 * the reflective {@code getAmount()} fallback path, and the default of 1 when
 * neither reports an amount.
 */
public class BookmarkIngredientAmountResolverTest {

	@Test
	public void resolvesAmountThroughIngredientHelper() {
		IIngredientManager ingredientManager = createManager(false);
		TestAmountIngredient ingredient = new TestAmountIngredient(1, 64);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		assertEquals(64, BookmarkIngredientAmountResolver.getAmount(typed, ingredientManager));
	}

	@Test
	public void resolvesAmountThroughReflectionWhenHelperHasNoAmount() {
		// reflectAmount=true makes the helper report -1, forcing the reflective path.
		IIngredientManager ingredientManager = createManager(true);
		TestAmountIngredient ingredient = new TestAmountIngredient(1, 7);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		assertEquals(7, BookmarkIngredientAmountResolver.getAmount(typed, ingredientManager));
	}

	@Test
	public void defaultsToOneWhenNoAmountIsResolvable() {
		IIngredientManager ingredientManager = createManager(true);
		// TestAmountIngredient with amount 0: helper reports -1 (reflect path),
		// reflection returns 0 -> no explicit amount -> default 1.
		TestAmountIngredient ingredient = new TestAmountIngredient(1, 0);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		assertEquals(1, BookmarkIngredientAmountResolver.getAmount(typed, ingredientManager));
	}

	@Test
	public void defaultsToOneForIngredientsWithoutAmountSupport() {
		// TestIngredient has neither a helper-reported amount nor a getAmount() method.
		IIngredientManager ingredientManager = createManager(false);
		TestIngredient ingredient = new TestIngredient(3);
		ITypedIngredient<TestIngredient> typed = ingredientManager
			.createTypedIngredient(TestIngredient.TYPE, ingredient)
			.orElseThrow();
		assertEquals(1, BookmarkIngredientAmountResolver.getAmount(typed, ingredientManager));
	}

	private static IIngredientManager createManager(boolean reflectAmount) {
		SubtypeManager subtypeManager = new SubtypeManager(new SubtypeInterpreters());
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, new TestColorHelper());
		builder.register(
			TestAmountIngredient.TYPE,
			List.of(),
			new TestAmountIngredientHelper(reflectAmount),
			new TestAmountIngredientRenderer()
		);
		builder.register(TestIngredient.TYPE, List.of(), new TestIngredientHelper(), new TestIngredientRenderer());
		return builder.build();
	}
}
