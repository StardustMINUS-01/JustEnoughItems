package mezz.jei.common.util;

public final class SaturatedMath {
	private SaturatedMath() {
	}

	public static long add(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	public static long multiply(long first, long second) {
		try {
			return Math.multiplyExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	public static long divideRoundUp(long numerator, long denominator) {
		if (denominator <= 0 || numerator <= 0) {
			return 0;
		}
		return 1 + (numerator - 1) / denominator;
	}
}
