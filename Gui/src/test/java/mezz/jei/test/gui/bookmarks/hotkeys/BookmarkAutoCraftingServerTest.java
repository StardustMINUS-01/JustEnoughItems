package mezz.jei.test.gui.bookmarks.hotkeys;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkAutoCraftingServerTest {
	private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "craft");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@EnumSource(value = InputType.class, names = {"SIMULATE", "EXECUTE"})
	void singleRecipeSendsOnlyOnExecution(InputType inputType) {
		var layout = layout();
		CraftingMenu menu = new CraftingMenu(7, new Inventory(null));
		var key = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C);
		UserInput userInput = new UserInput(key, 0, 0, GLFW.GLFW_MOD_SHIFT, inputType);
		List<PlayToServerPacket> packets = new ArrayList<>();
		AtomicInteger closed = new AtomicInteger();
		AtomicInteger sounds = new AtomicInteger();

		assertTrue(BookmarkAutoCraftingActivator.activate(userInput, layout, menu,
			closed::incrementAndGet, true, sounds::incrementAndGet, packets::add, true,
			List.of(new ItemStack(Items.OAK_PLANKS))));
		int executions = inputType == InputType.EXECUTE ? 1 : 0;
		assertEquals(executions, packets.size());
		assertEquals(executions, closed.get());
		assertEquals(executions, sounds.get());
		if (executions > 0) {
			assertInstanceOf(PacketCraftingGridCraft.class, packets.getFirst());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void chainWaitsForMatchingAckAndInventorySync(boolean craftAll) {
		List<RecipeChainInput> chain = List.of(input(0, result(RECIPE, key("result"), 1, 1)),
			input(1, ingredient(RECIPE, key("material"), 1)));
		AtomicReference<List<RecipeChainInput>> inventory = new AtomicReference<>(List.of(
			input(0, mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item(key("material"), 1))));
		List<PlayToServerPacket> packets = new ArrayList<>();
		AtomicBoolean valid = new AtomicBoolean(true);
		var task = BookmarkAutoCraftingBridge.createTask(chain, Set.of(), 9, 7, inventory::get,
			() -> List.of(new ItemStack(Items.OAK_PLANKS)), uid -> Optional.of(layout()),
			packets::add, valid::get, craftAll).orElseThrow();

		assertTrue(task.start());
		assertEquals(1, packets.size());
		task.handleAck(999, 1);
		assertTrue(task.tick());
		task.handleAck(1, 1);
		assertTrue(task.tick());
		assertEquals(1, packets.size());
		inventory.set(List.of(input(0, mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item(key("result"), 1))));
		assertFalse(task.tick());
		assertEquals(1, packets.size());

		var cancelled = BookmarkAutoCraftingBridge.createTask(chain, Set.of(), 9, 7, List::of,
			List::of, uid -> Optional.of(layout()), packets::add, valid::get, craftAll).orElseThrow();
		valid.set(false);
		assertFalse(cancelled.start());
		assertEquals(1, packets.size());
	}

	private static RecipeLayoutTestFixtures.TestRecipeLayout layout() {
		return RecipeLayoutTestFixtures.singleIngredientLayout(RecipeTypes.CRAFTING, new Object(), RECIPE,
			List.of(item(Items.OAK_PLANKS)), List.of(item(Items.STICK)));
	}
}
