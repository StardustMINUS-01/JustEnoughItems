package mezz.jei.gui.input.handlers;

import mezz.jei.gui.util.GiveAmount;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FocusInputHandlerGiveAmountTest {
	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void defaultOneAndMaxKeepBehavior() {
		assertEquals(1, FocusInputHandler.resolveGiveAmount(GiveAmount.ONE, stack(), Optional.empty()));
		assertEquals(64, FocusInputHandler.resolveGiveAmount(GiveAmount.MAX, stack(), Optional.empty()));
	}

	@Test
	public void customAmountWinsForOneAndMax() {
		assertEquals(65, FocusInputHandler.resolveGiveAmount(GiveAmount.ONE, stack(), Optional.of(65L)));
		assertEquals(65, FocusInputHandler.resolveGiveAmount(GiveAmount.MAX, stack(), Optional.of(65L)));
	}

	@Test
	public void customAmountClampsToIntMax() {
		assertEquals(Integer.MAX_VALUE, FocusInputHandler.resolveGiveAmount(GiveAmount.ONE, stack(), Optional.of(3_000_000_000L)));
	}

	@Test
	public void nonPositiveCustomAmountFallsBackToOne() {
		assertEquals(1, FocusInputHandler.resolveGiveAmount(GiveAmount.MAX, stack(), Optional.of(0L)));
	}

	private static ItemStack stack() {
		return new ItemStack(Items.DIAMOND);
	}
}
