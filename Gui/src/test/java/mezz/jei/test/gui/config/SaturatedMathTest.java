package mezz.jei.test.gui.config;

import mezz.jei.common.util.SaturatedMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SaturatedMathTest {
	@Test
	public void addOverflowClampsToMax() {
		Assertions.assertEquals(Long.MAX_VALUE, SaturatedMath.add(Long.MAX_VALUE, 1));
		Assertions.assertEquals(5, SaturatedMath.add(2, 3));
	}

	@Test
	public void multiplyOverflowClampsToMax() {
		Assertions.assertEquals(Long.MAX_VALUE, SaturatedMath.multiply(Long.MAX_VALUE, 2));
		Assertions.assertEquals(6, SaturatedMath.multiply(2, 3));
	}

	@Test
	public void divideRoundUpRoundsUp() {
		Assertions.assertEquals(2, SaturatedMath.divideRoundUp(5, 3));
		Assertions.assertEquals(3, SaturatedMath.divideRoundUp(9, 3));
	}

	@Test
	public void divideRoundUpReturnsZeroForInvalidInputs() {
		Assertions.assertEquals(0, SaturatedMath.divideRoundUp(5, 0));
		Assertions.assertEquals(0, SaturatedMath.divideRoundUp(0, 3));
	}
}
