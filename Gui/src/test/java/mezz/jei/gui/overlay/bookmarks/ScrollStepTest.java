package mezz.jei.gui.overlay.bookmarks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScrollStepTest {
	@Test
	public void hidingQuantityFieldUsesFixedStepWithoutLosingValue() {
		var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
		ScrollStep scrollStep = new ScrollStep(enabled::get);
		scrollStep.setValue(128);
		assertEquals(128, scrollStep.getEffectiveStep());
		enabled.set(false);
		assertEquals(64, scrollStep.getEffectiveStep());
		assertEquals(128, scrollStep.getValue());
		enabled.set(true);
		assertEquals(128, scrollStep.getEffectiveStep());
	}
	@Test
	public void defaultValueUsesOneStackStep() {
		ScrollStep scrollStep = new ScrollStep();

		assertEquals(0, scrollStep.getValue());
		assertEquals(64, scrollStep.getEffectiveStep());
	}

	@Test
	public void setValueClampsBelowZero() {
		ScrollStep scrollStep = new ScrollStep();

		scrollStep.setValue(-5);

		assertEquals(0, scrollStep.getValue());
	}

	@Test
	public void addClampsBelowZero() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(1);

		scrollStep.add(-64);

		assertEquals(0, scrollStep.getValue());
	}

	@Test
	public void ctrlScrollFromSmallValueGoesToZero() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(33);

		scrollStep.add(-64);

		assertEquals(0, scrollStep.getValue());
	}

	@Test
	public void addClampsToIntMax() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(Integer.MAX_VALUE);

		scrollStep.add(64);

		assertEquals(Integer.MAX_VALUE, scrollStep.getValue());
	}

	@Test
	public void customValueIsUsedAsStep() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(33);

		assertEquals(33, scrollStep.getEffectiveStep());
	}

	@Test
	public void resetReturnsToOneStackDisplay() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(5);

		scrollStep.reset();

		assertEquals(0, scrollStep.getValue());
	}

	@Test
	public void digitsParseToValue() {
		assertEquals(33, ScrollStepTextField.parse("33"));
	}
}
