package mezz.jei.test.bench;

import mezz.jei.gui.overlay.bookmarks.BookmarkAmountFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark tests for the 1.20.1 port of the JEI 1.21.1
 * {@code BookmarkAmountFormatter}: item/fluid compact formatting and the
 * fluid-unit type selection.
 */
public class BookmarkAmountFormatterTest {

	@Test
	public void formatItemAmountBelowOneThousand() {
		assertEquals("1", BookmarkAmountFormatter.formatItemAmount(1));
		assertEquals("999", BookmarkAmountFormatter.formatItemAmount(999));
	}

	@Test
	public void formatItemAmountThousands() {
		assertEquals("1K", BookmarkAmountFormatter.formatItemAmount(1_000));
		assertEquals("1.2K", BookmarkAmountFormatter.formatItemAmount(1_234));
		assertEquals("1.5K", BookmarkAmountFormatter.formatItemAmount(1_500));
		assertEquals("9.9K", BookmarkAmountFormatter.formatItemAmount(9_900));
		assertEquals("99.9K", BookmarkAmountFormatter.formatItemAmount(99_900));
	}

	@Test
	public void formatItemAmountMillions() {
		assertEquals("1M", BookmarkAmountFormatter.formatItemAmount(1_000_000));
		assertEquals("2.5M", BookmarkAmountFormatter.formatItemAmount(2_500_000));
		assertEquals("99.9M", BookmarkAmountFormatter.formatItemAmount(99_900_000));
	}

	@Test
	public void formatItemAmountBillions() {
		assertEquals("1B", BookmarkAmountFormatter.formatItemAmount(1_000_000_000L));
		assertEquals("3.3B", BookmarkAmountFormatter.formatItemAmount(3_300_000_000L));
	}

	@Test
	public void formatFluidAmount() {
		assertEquals("1", BookmarkAmountFormatter.formatFluidAmount(1));
		assertEquals("999", BookmarkAmountFormatter.formatFluidAmount(999));
		assertEquals("1B", BookmarkAmountFormatter.formatFluidAmount(1_000));
		assertEquals("1.5B", BookmarkAmountFormatter.formatFluidAmount(1_500));
		assertEquals("1kB", BookmarkAmountFormatter.formatFluidAmount(1_000_000));
		assertEquals("1.2kB", BookmarkAmountFormatter.formatFluidAmount(1_200_000));
		assertEquals("1MB", BookmarkAmountFormatter.formatFluidAmount(1_000_000_000L));
		assertEquals("2.5MB", BookmarkAmountFormatter.formatFluidAmount(2_500_000_000L));
	}

	@Test
	public void formatTypedAmountUsesFluidUnits() {
		assertEquals("1B", BookmarkAmountFormatter.formatTypedAmount(1_000, "fluid_stack"));
		assertEquals("1.5B", BookmarkAmountFormatter.formatTypedAmount(1_500, "mekanism.api.chemical.gas.GasStack"));
		assertEquals("1kB", BookmarkAmountFormatter.formatTypedAmount(1_000_000, "mekanism.api.chemical.infuse.InfusionStack"));
		assertEquals("1MB", BookmarkAmountFormatter.formatTypedAmount(1_000_000_000L, "mekanism.api.chemical.slurry.SlurryStack"));
	}

	@Test
	public void formatTypedAmountUsesItemUnits() {
		assertEquals("1K", BookmarkAmountFormatter.formatTypedAmount(1_000, "test"));
		assertEquals("1M", BookmarkAmountFormatter.formatTypedAmount(1_000_000, "minecraft:item"));
	}

	@Test
	public void fluidAmountTypeSelection() {
		assertTrue(BookmarkAmountFormatter.usesFluidAmountUnits("fluid_stack"));
		assertTrue(BookmarkAmountFormatter.usesFluidAmountUnits("mekanism.api.chemical.gas.GasStack"));
		assertTrue(BookmarkAmountFormatter.usesFluidAmountUnits("mekanism.api.chemical.infuse.InfusionStack"));
		assertTrue(BookmarkAmountFormatter.usesFluidAmountUnits("mekanism.api.chemical.pigment.PigmentStack"));
		assertTrue(BookmarkAmountFormatter.usesFluidAmountUnits("mekanism.api.chemical.slurry.SlurryStack"));
		assertFalse(BookmarkAmountFormatter.usesFluidAmountUnits("minecraft:item"));
	}
}
