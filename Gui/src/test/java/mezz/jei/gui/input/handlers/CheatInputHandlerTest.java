package mezz.jei.gui.input.handlers;

import mezz.jei.common.config.GiveMode;
import mezz.jei.gui.util.GiveAmount;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheatInputHandlerTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@CsvSource({
		"MOUSE_PICKUP, ONE, 8, 33, false, 1",
		"MOUSE_PICKUP, MAX, 8, 33, false, 64",
		"MOUSE_PICKUP, MAX, 8, 33, true, 1",
		"INVENTORY, ONE, 8, 33, false, 8",
		"INVENTORY, MAX, 8, 33, false, 8",
		"INVENTORY, ONE, , 33, false, 33",
		"INVENTORY, MAX, , 33, false, 33",
		"INVENTORY, ONE, , 0, false, 64",
		"INVENTORY, MAX, , 0, true, 1",
		"INVENTORY, MAX, 3000000000, 33, false, 2147483647"
	})
	void resolvesQuantityByDestination(GiveMode mode, GiveAmount clickAmount, Long bookmarkAmount,
		long configuredAmount, boolean nonStackable, int expected) {
		ItemStack stack = new ItemStack(nonStackable ? Items.DIAMOND_SWORD : Items.DIAMOND);
		assertEquals(expected, CheatInputHandler.resolveGiveAmount(mode, clickAmount, stack,
			Optional.ofNullable(bookmarkAmount), configuredAmount));
	}
}
