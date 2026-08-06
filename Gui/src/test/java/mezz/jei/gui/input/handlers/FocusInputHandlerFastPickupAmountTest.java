package mezz.jei.gui.input.handlers;

import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FocusInputHandlerFastPickupAmountTest {
	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void ordinaryItemUsesScrollStepValue() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(33);

		assertEquals(33, FocusInputHandler.resolveFastPickupAmount(stack(Items.DIAMOND), Optional.empty(), scrollStep));
	}

	@Test
	public void ordinaryItemUsesOneStackWhenScrollStepIsZero() {
		ScrollStep scrollStep = new ScrollStep();

		assertEquals(64, FocusInputHandler.resolveFastPickupAmount(stack(Items.DIAMOND), Optional.empty(), scrollStep));
	}

	@Test
	public void nonStackableItemUsesOneStackWhenScrollStepIsZero() {
		ScrollStep scrollStep = new ScrollStep();

		assertEquals(1, FocusInputHandler.resolveFastPickupAmount(stack(Items.DIAMOND_SWORD), Optional.empty(), scrollStep));
	}

	@Test
	public void chainAmountTakesPriorityOverScrollStep() {
		ScrollStep scrollStep = new ScrollStep();
		scrollStep.setValue(33);

		assertEquals(8, FocusInputHandler.resolveFastPickupAmount(stack(Items.DIAMOND), Optional.of(8L), scrollStep));
	}

	@Test
	public void chainAmountClampsToIntMax() {
		ScrollStep scrollStep = new ScrollStep();

		assertEquals(Integer.MAX_VALUE, FocusInputHandler.resolveFastPickupAmount(
			stack(Items.DIAMOND),
			Optional.of(3_000_000_000L),
			scrollStep
		));
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}
}
