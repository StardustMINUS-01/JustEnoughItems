package mezz.jei.gui.input.handlers;

import mezz.jei.common.config.GiveMode;
import mezz.jei.gui.util.GiveAmount;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FocusInputHandlerAmountTest {
	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void mousePickupKeepsGiveAmount() {
		assertEquals(1, FocusInputHandler.resolveGiveAmount(
			GiveMode.MOUSE_PICKUP, GiveAmount.ONE, stack(Items.DIAMOND), Optional.of(8L), 33
		));
		assertEquals(64, FocusInputHandler.resolveGiveAmount(
			GiveMode.MOUSE_PICKUP, GiveAmount.MAX, stack(Items.DIAMOND), Optional.of(8L), 33
		));
	}

	@Test
	public void inventoryUsesScrollStepValue() {
		assertEquals(33, FocusInputHandler.resolveGiveAmount(
			GiveMode.INVENTORY, GiveAmount.ONE, stack(Items.DIAMOND), Optional.empty(), 33
		));
	}

	@Test
	public void inventoryUsesOneStackWhenScrollStepIsZero() {
		assertEquals(64, FocusInputHandler.resolveGiveAmount(
			GiveMode.INVENTORY, GiveAmount.ONE, stack(Items.DIAMOND), Optional.empty(), 0
		));
	}

	@Test
	public void inventoryNonStackableUsesOneStackWhenScrollStepIsZero() {
		assertEquals(1, FocusInputHandler.resolveGiveAmount(
			GiveMode.INVENTORY, GiveAmount.MAX, stack(Items.DIAMOND_SWORD), Optional.empty(), 0
		));
	}

	@Test
	public void inventoryBookmarkAmountTakesPriority() {
		assertEquals(8, FocusInputHandler.resolveGiveAmount(
			GiveMode.INVENTORY, GiveAmount.ONE, stack(Items.DIAMOND), Optional.of(8L), 33
		));
	}

	@Test
	public void inventoryBookmarkAmountClampsToIntMax() {
		assertEquals(Integer.MAX_VALUE, FocusInputHandler.resolveGiveAmount(
			GiveMode.INVENTORY, GiveAmount.ONE, stack(Items.DIAMOND), Optional.of(3_000_000_000L), 0
		));
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}
}
