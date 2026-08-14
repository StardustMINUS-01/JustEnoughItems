package mezz.jei.gui.bookmarks;

import java.util.Set;

/**
 * Formats bookmark ingredient amounts for display, ported from JEI 1.21.1
 * ({@code mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter}). The
 * 1.21.1 overload taking a {@code Set<BookmarkIngredientKey>} is intentionally
 * omitted; 1.20.1 callers use the {@code ingredientTypeUid} overload.
 */
public final class BookmarkAmountFormatter {
	private static final Set<String> FLUID_AMOUNT_TYPE_UIDS = Set.of(
		"fluid_stack",
		"mekanism.api.chemical.gas.GasStack",
		"mekanism.api.chemical.infuse.InfusionStack",
		"mekanism.api.chemical.pigment.PigmentStack",
		"mekanism.api.chemical.slurry.SlurryStack"
	);

	private BookmarkAmountFormatter() {
	}

	public static String formatItemAmount(long amount) {
		if (amount < 1_000) {
			return Long.toString(amount);
		}
		if (amount < 100_000) {
			return formatCompact(amount, 1_000, "K");
		}
		if (amount < 100_000_000) {
			return formatCompact(amount, 1_000_000, "M");
		}
		return formatCompact(amount, 1_000_000_000, "B");
	}

	public static String formatFluidAmount(long amount) {
		if (amount < 1_000) {
			return Long.toString(amount);
		}
		if (amount < 100_000) {
			return formatCompact(amount, 1_000, "B");
		}
		if (amount < 100_000_000) {
			return formatCompact(amount, 1_000_000, "kB");
		}
		return formatCompact(amount, 1_000_000_000, "MB");
	}

	public static String formatTypedAmount(long amount, String ingredientTypeUid) {
		if (usesFluidAmountUnits(ingredientTypeUid)) {
			return formatFluidAmount(amount);
		}
		return formatItemAmount(amount);
	}

	public static boolean usesFluidAmountUnits(String ingredientTypeUid) {
		return FLUID_AMOUNT_TYPE_UIDS.contains(ingredientTypeUid);
	}

	private static String formatCompact(long amount, long divisor, String suffix) {
		long tenths = Math.round((amount * 10.0) / divisor);
		if (tenths % 10 == 0) {
			return (tenths / 10) + suffix;
		}
		return (tenths / 10) + "." + Math.abs(tenths % 10) + suffix;
	}
}
