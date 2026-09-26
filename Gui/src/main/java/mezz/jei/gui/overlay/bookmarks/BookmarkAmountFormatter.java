package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.FluidAmountFormatter;

import java.util.Set;

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
		return formatFluidAmount(amount, Services.PLATFORM.getFluidHelper().bucketVolume());
	}

	private static String formatFluidAmount(long amount, long bucketVolume) {
		if (amount < bucketVolume * 100) {
			return FluidAmountFormatter.format(amount, bucketVolume);
		}
		if (amount < bucketVolume * 100_000) {
			return formatCompact(amount, bucketVolume * 1_000, "kB");
		}
		return formatCompact(amount, bucketVolume * 1_000_000, "MB");
	}

	public static String formatTypedAmount(long amount, Set<BookmarkIngredientKey> permutations) {
		return permutations.stream().map(BookmarkIngredientKey::ingredientTypeUid)
			.filter(BookmarkAmountFormatter::usesFluidAmountUnits)
			.findFirst()
			.map(type -> formatTypedAmount(amount, type))
			.orElseGet(() -> formatItemAmount(amount));
	}

	public static String formatTypedAmount(long amount, String ingredientTypeUid) {
		if (usesFluidAmountUnits(ingredientTypeUid)) {
			return ingredientTypeUid.equals("fluid_stack") ? formatFluidAmount(amount) : formatFluidAmount(amount, 1_000);
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
