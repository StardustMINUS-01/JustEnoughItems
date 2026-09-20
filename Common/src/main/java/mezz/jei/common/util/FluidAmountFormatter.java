package mezz.jei.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FluidAmountFormatter {
	private FluidAmountFormatter() {
	}

	public static BigDecimal buckets(long amount, long bucketVolume) {
		// Six decimal places also preserve sub-millibucket amounts on Fabric.
		return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(bucketVolume), 6, RoundingMode.HALF_UP).stripTrailingZeros();
	}

	public static String format(long amount, long bucketVolume) {
		String text = buckets(amount, bucketVolume).toPlainString();
		return text.startsWith("0.") ? text.substring(1) : text;
	}
}
