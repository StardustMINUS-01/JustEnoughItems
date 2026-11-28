package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.common.network.packets.PacketProcessingStationCraft;
import mezz.jei.gui.bookmarks.chain.BookmarkCraftingScope;
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingStationDispatchTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void waitsForMatchingAckAndInventoryChange(boolean inventoryFirst) {
		var recipe = ResourceLocation.parse("test:repair");
		var chain = List.of(input(0, result(recipe, key("result"), 1, 1)), input(1, ingredient(recipe, key("material"), 1)));
		var inventory = List.of(input(0, item(key("material"), 2)));
		var material = new ItemStack(Items.IRON_INGOT, 2);
		var available = List.of(material);
		var menu = new AnvilMenu(7, new Inventory(null));
		var layout = RecipeLayoutTestFixtures.layout(RecipeTypes.ANVIL, new Object(), recipe,
			List.of(List.of(ItemStackIngredientTestFixtures.item(Items.IRON_INGOT)), List.of()),
			List.of(List.of(ItemStackIngredientTestFixtures.item(Items.IRON_NUGGET))));
		var packets = new ArrayList<PacketProcessingStationCraft>();
		var task = new BookmarkAutoCraftingBridge.Task(chain, Set.of(), 2, menu.containerId, () -> inventory,
			() -> available, uid -> Optional.of(layout), packet -> packets.add(assertInstanceOf(PacketProcessingStationCraft.class, packet)),
			() -> true, false, (view, containerId, amount, taskId, requestId, stacks) -> BookmarkProcessingStations.createRequest(view, menu, amount, taskId, requestId, stacks));
		try {
			assertTrue(task.start());
			assertEquals(1, packets.size());
			var first = packets.getFirst();
			assertEquals(task.taskId(), first.taskId());
			if (inventoryFirst) {
				material.shrink(1);
				task.handleAck(first.requestId() + 1, 1);
			} else {
				task.handleAck(first.requestId(), 1);
			}
			assertTrue(task.tick());
			assertEquals(1, packets.size());

			if (inventoryFirst) {
				task.handleAck(first.requestId(), 1);
			} else {
				material.shrink(1);
			}
			assertTrue(task.tick());
			assertEquals(2, packets.size());
			assertEquals(first.requestId() + 1, packets.getLast().requestId());
			assertTrue(task.tick());
			assertEquals(2, packets.size());
		} finally {
			if (!packets.isEmpty())
				BookmarkProcessingStations.acknowledge(task.taskId(), packets.getLast().requestId());
			BookmarkCraftingScope.clear();
		}
	}
}
