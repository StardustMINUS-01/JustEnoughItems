package mezz.jei.test.lib;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ingredient helper for {@link TestAmountIngredient}. With
 * {@code reflectAmount} enabled, {@link #getAmount} returns {@code -1} so the
 * reflective {@code getAmount()} path of
 * {@code BookmarkIngredientAmountResolver} is exercised.
 */
public class TestAmountIngredientHelper implements IIngredientHelper<TestAmountIngredient> {
	private final boolean reflectAmount;

	public TestAmountIngredientHelper(boolean reflectAmount) {
		this.reflectAmount = reflectAmount;
	}

	public TestAmountIngredientHelper() {
		this(false);
	}

	@Override
	public IIngredientType<TestAmountIngredient> getIngredientType() {
		return TestAmountIngredient.TYPE;
	}

	@Override
	public String getDisplayName(TestAmountIngredient ingredient) {
		return "§eTest Amount Ingredient Display Name " + ingredient;
	}

	@Override
	public String getUniqueId(TestAmountIngredient ingredient, UidContext context) {
		return "Test Amount Ingredient Unique Id " + ingredient;
	}

	@Override
	public String getWildcardId(TestAmountIngredient ingredient) {
		return "Test Amount Ingredient Unique Id";
	}

	@Override
	public Iterable<Integer> getColors(TestAmountIngredient ingredient) {
		return List.of(0xFF000000);
	}

	@Override
	public ResourceLocation getResourceLocation(TestAmountIngredient ingredient) {
		return new ResourceLocation("jei_test_mod", "test_amount_ingredient_" + ingredient.getNumber());
	}

	@Override
	public long getAmount(TestAmountIngredient ingredient) {
		if (reflectAmount) {
			return -1;
		}
		return ingredient.getAmount();
	}

	@Override
	public TestAmountIngredient copyIngredient(TestAmountIngredient ingredient) {
		return new TestAmountIngredient(ingredient.getNumber(), (int) ingredient.getAmount());
	}

	@Override
	public String getErrorInfo(@Nullable TestAmountIngredient ingredient) {
		return "Test Amount Ingredient Error Info " + ingredient;
	}
}
