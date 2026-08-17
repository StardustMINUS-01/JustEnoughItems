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

	/**
	 * Value semantics like real ingredients (e.g. ItemStack): two instances
	 * with the same number and amount represent the same ingredient. This is
	 * required by the 1.20.1 port of the JEI 1.21.1
	 * {@code RecipeBookmark.equals} ingredient comparison.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof TestAmountIngredient other)) {
			return false;
		}
		return number == other.number && amount == other.amount;
	}

	@Override
	public int hashCode() {
		return 31 * number + amount;
	}
}
