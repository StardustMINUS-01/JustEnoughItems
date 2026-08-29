package mezz.jei.test;

import mezz.jei.common.util.TickTimer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

public class TickTimerTest {
	@Test
	public void testBasicTickTimerMath() {
		int maxValue = 1000;
		int msPerCycle = 20;
		assertTickValues(maxValue, msPerCycle, tick -> (tick % msPerCycle) * 50);
	}

	@Test
	public void testMoreTicksThanValuesMath() {
		int maxValue = 4;
		int msPerCycle = 20;
		int[] expectedValues = new int[]{0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4};
		assertTickValues(maxValue, msPerCycle, tick -> expectedValues[tick % msPerCycle]);
	}

	@Test
	public void testIndivisibleTicking() {
		int maxValue = 3;
		int msPerCycle = 10;
		int[] expectedValues = new int[]{0, 0, 0, 1, 1, 2, 2, 2, 3, 3};
		assertTickValues(maxValue, msPerCycle, tick -> expectedValues[tick % msPerCycle]);
	}

	private static void assertTickValues(int maxValue, int msPerCycle, IntUnaryOperator expectedValueForTick) {
		for (int tick = 0; tick < 1000; tick++) {
			int expectedValue = expectedValueForTick.applyAsInt(tick);
			int value = TickTimer.getValue(0, tick, maxValue, msPerCycle, false);
			Assertions.assertEquals(expectedValue, value);

			int expectedDownValue = maxValue - expectedValue;
			int downValue = TickTimer.getValue(0, tick, maxValue, msPerCycle, true);
			Assertions.assertEquals(expectedDownValue, downValue);
		}
	}
}
