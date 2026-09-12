package mezz.jei.test.gui.bookmarks.hotkeys;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingActivator;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkCraftingGridFill;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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

public class AutoCraftingInputTest {
	private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "craft");
	private static final InputConstants.Key KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C);

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@BeforeEach
	@AfterEach
	void resetClaims() {
		BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void releasesKey(boolean clearAll) {
		IJeiKeyMapping binding = new TestKeyMapping(KEY);
		UserInput press = new UserInput(KEY, 0, 0, GLFW.GLFW_MOD_SHIFT, InputType.EXECUTE);
		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, binding));
		assertFalse(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, binding));
		if (clearAll) {
			BookmarkAutoCraftingActivator.clearAutoCraftingInputs();
		} else {
			BookmarkAutoCraftingActivator.releaseAutoCraftingInput(KEY);
		}
		assertTrue(BookmarkAutoCraftingActivator.claimAutoCraftingInput(press, binding));
	}

	@ParameterizedTest
	@EnumSource(value = InputType.class, names = {"SIMULATE", "EXECUTE"})
	void sendsOnExecution(InputType inputType) {
		var layout = layout();
		CraftingMenu menu = new CraftingMenu(7, new Inventory(null));
		UserInput userInput = new UserInput(KEY, 0, 0, GLFW.GLFW_MOD_SHIFT, inputType);
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
	void waitsForAckAndInventory(boolean craftAll) {
		List<RecipeChainInput> chain = List.of(input(0, result(RECIPE, key("result"), 1, 1)),
			input(1, ingredient(RECIPE, key("material"), 1)));
		AtomicReference<List<RecipeChainInput>> inventory = new AtomicReference<>(List.of(
			input(0, mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item(key("material"), 1))));
		List<PlayToServerPacket> packets = new ArrayList<>();
		AtomicBoolean valid = new AtomicBoolean(true);
		var task = BookmarkAutoCraftingBridge.createTask(chain, Set.of(), 9, 7, inventory::get,
			() -> List.of(new ItemStack(Items.OAK_PLANKS)), uid -> Optional.of(layout()),
			packets::add, valid::get, craftAll)
			.orElseThrow();

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
			List::of, uid -> Optional.of(layout()), packets::add, valid::get, craftAll)
			.orElseThrow();
		valid.set(false);
		assertFalse(cancelled.start());
		assertEquals(1, packets.size());
	}

	private static RecipeLayoutTestFixtures.TestRecipeLayout layout() {
		return gridLayout(0);
	}

	@ParameterizedTest
	@ValueSource(ints = {4, 9})
	void mapsCraftingSlots(int targetSlotCount) {
		for (int[] occupied : List.of(new int[]{0, 1, 3, 4}, new int[]{0, 4})) {
			var layout = gridLayout(occupied);
			var stacks = BookmarkCraftingGridFill.create(layout, targetSlotCount, 1).orElseThrow().targetStacks();
			int columns = targetSlotCount == 4 ? 2 : 3;
			for (int i = 0; i < targetSlotCount; i++) {
				ItemStack expected = layout.inputs().get(i / columns * 3 + i % columns)
					.getDisplayedItemStack().orElse(ItemStack.EMPTY);
				assertTrue(ItemStack.matches(expected, stacks.get(i)), "Target slot " + i);
			}
		}
	}

	private record TestKeyMapping(InputConstants.Key key) implements IJeiKeyMapping {
		@Override
		public boolean isActiveAndMatches(InputConstants.Key input) {
			return key.equals(input);
		}

		@Override
		public boolean isUnbound() {
			return false;
		}

		@Override
		public Component getTranslatedKeyMessage() {
			return Component.literal("test");
		}
	}

	private static RecipeLayoutTestFixtures.TestRecipeLayout gridLayout(int... occupied) {
		List<List<ITypedIngredient<?>>> inputs = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			inputs.add(List.of());
		}
		for (int index : occupied) {
			inputs.set(index, List.of(item(index % 2 == 0 ? Items.OAK_PLANKS : Items.STONE)));
		}
		return RecipeLayoutTestFixtures.layout(RecipeTypes.CRAFTING, new Object(), RECIPE,
			inputs, List.of(List.of(item(Items.STICK))));
	}
}
