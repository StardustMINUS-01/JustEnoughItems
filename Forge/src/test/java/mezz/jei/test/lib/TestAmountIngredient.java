package mezz.jei.test.lib;

import mezz.jei.api.ingredients.IIngredientType;

/**
 * A test ingredient with an explicit amount, used by the benchmark tests for
 * the 1.20.1 bookmark amount resolution (mirroring the JEI 1.21.1
 * {@code BookmarkIngredientAmountResolver} paths). Unlike
 * {@link TestIngredient}, this is a plain class so that it can expose a public
 * {@link #getAmount()} method for the reflective amount path without changing
 * the record contract used by the many {@code new TestIngredient(i)} call
 * sites.
 */
public class TestAmountIngredient {
	public static final IIngredientType<TestAmountIngredient> TYPE = new IIngredientType<>() {
		@Override
		public String getUid() {
			return "test_amount";
		}

		@Override
		public Class<? extends TestAmountIngredient> getIngredientClass() {
			return TestAmountIngredient.class;
		}
	};

	private final int number;
	private final int amount;

	public TestAmountIngredient(int number, int amount) {
		this.number = number;
		this.amount = amount;
	}

	public int getNumber() {
		return number;
	}

	/**
	 * The explicit amount; resolved reflectively by
	 * {@code BookmarkIngredientAmountResolver} when the ingredient helper does
	 * not report one.
	 */
	public long getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "TestAmountIngredient#" + number;
	}
}
