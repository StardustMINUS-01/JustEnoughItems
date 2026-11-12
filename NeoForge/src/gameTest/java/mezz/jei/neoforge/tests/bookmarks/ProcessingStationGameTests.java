package mezz.jei.neoforge.tests.bookmarks;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.common.bookmarks.ServerProcessingStationCraft;
import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.packets.PacketProcessingStationCraft;
import mezz.jei.common.network.packets.PlayToClientPacket;
import mezz.jei.neoforge.tests.lib.JeiGameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Optional;

@ForEachTest(groups = "processing_stations")
public final class ProcessingStationGameTests {
	private static final IConnectionToClient CONNECTION = new IConnectionToClient() {
		@Override
		public <T extends PlayToClientPacket<T>> void sendPacketToClient(T packet, ServerPlayer player) {}
	};

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Stonecutting executes one recipe per tick and cancellation preserves remaining materials.")
	public static void stonecuttingAndCancellation(JeiGameTestHelper helper) {
		ServerPlayer player = helper.getPlayer();
		player.getInventory().clearContent();
		StonecutterMenu menu = new StonecutterMenu(7, player.getInventory());
		player.containerMenu = menu;
		player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
		var recipe = player.level().getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).stream()
			.filter(r -> r.value().getIngredients().getFirst().test(new ItemStack(Items.STONE)))
			.filter(r -> r.value().getResultItem(player.registryAccess()).is(Items.STONE_SLAB)).findFirst().orElseThrow();
		var request = new PacketProcessingStationCraft(7, 1, 1, RecipeTypes.STONECUTTING.getUid(), Optional.of(recipe.id()), 3,
			List.of(new ItemStack(Items.STONE)), new ItemStack(Items.STONE_SLAB, 2));
		ServerProcessingStationCraft.start(player, request, CONNECTION);
		ServerProcessingStationCraft.tick(player);
		helper.assertEquals(2, player.getInventory().countItem(Items.STONE_SLAB), "One stone produces two slabs");
		ServerProcessingStationCraft.cancel(player);
		ServerProcessingStationCraft.tick(player);
		helper.assertEquals(2, player.getInventory().countItem(Items.STONE), "Cancellation preserves the remaining stone");
		helper.succeed();
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Anvil repair consumes experience and material through vanilla result pickup.")
	public static void anvilRepair(JeiGameTestHelper helper) {
		ServerPlayer player = helper.getPlayer();
		player.setGameMode(GameType.SURVIVAL);
		player.experienceLevel = 30;
		player.getInventory().clearContent();
		AnvilMenu menu = new AnvilMenu(7, player.getInventory());
		player.containerMenu = menu;
		ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
		damaged.setDamageValue(100);
		player.getInventory().setItem(0, damaged.copy());
		player.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
		var request = new PacketProcessingStationCraft(7, 1, 1, RecipeTypes.ANVIL.getUid(), Optional.empty(), 1,
			List.of(damaged, new ItemStack(Items.DIAMOND)), new ItemStack(Items.DIAMOND_PICKAXE));
		ServerProcessingStationCraft.start(player, request, CONNECTION);
		ServerProcessingStationCraft.tick(player);
		helper.assertEquals(0, player.getInventory().countItem(Items.DIAMOND), "Repair material consumed");
		helper.assertTrue(player.experienceLevel < 30, "Vanilla anvil XP charged");
		helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.is(Items.DIAMOND_PICKAXE) && s.getDamageValue() == 0), "Repaired tool delivered");
		helper.succeed();
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Wrong expected outputs cannot create items and closing a menu cancels pending work.")
	public static void rejectsWrongOutputAndChangedMenu(JeiGameTestHelper helper) {
		ServerPlayer player = helper.getPlayer();
		player.getInventory().clearContent();
		SmithingMenu menu = new SmithingMenu(7, player.getInventory());
		player.containerMenu = menu;
		List<ItemStack> inputs = List.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_INGOT));
		for (int i = 0; i < inputs.size(); i++)
			player.getInventory().setItem(i, inputs.get(i).copy());
		var request = new PacketProcessingStationCraft(7, 1, 1, RecipeTypes.SMITHING.getUid(), Optional.empty(), 1, inputs, new ItemStack(Items.DIAMOND_BLOCK));
		ServerProcessingStationCraft.start(player, request, CONNECTION);
		ServerProcessingStationCraft.tick(player);
		helper.assertEquals(0, player.getInventory().countItem(Items.DIAMOND_BLOCK), "Client output is not trusted");
		helper.assertSameStack(inputs.getFirst(), menu.getSlot(0).getItem(), "No template consumed for wrong output");
		ServerProcessingStationCraft.start(player, request, CONNECTION);
		player.containerMenu = player.inventoryMenu;
		ServerProcessingStationCraft.tick(player);
		helper.assertSameStack(inputs.getFirst(), menu.getSlot(0).getItem(), "No processing after menu change");
		helper.succeed();
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Smithing consumes template and addition and delivers the real result.")
	public static void smithing(JeiGameTestHelper helper) {
		ServerPlayer player = helper.getPlayer();
		player.getInventory().clearContent();
		SmithingMenu menu = new SmithingMenu(7, player.getInventory());
		player.containerMenu = menu;
		List<ItemStack> inputs = List.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_INGOT));
		for (int i = 0; i < inputs.size(); i++)
			player.getInventory().setItem(i, inputs.get(i).copy());
		ServerProcessingStationCraft.start(player, new PacketProcessingStationCraft(7, 1, 1, RecipeTypes.SMITHING.getUid(), Optional.empty(), 1, inputs, new ItemStack(Items.NETHERITE_SWORD)), CONNECTION);
		ServerProcessingStationCraft.tick(player);
		helper.assertEquals(1, player.getInventory().countItem(Items.NETHERITE_SWORD), "Output delivered");
		helper.assertEquals(0, player.getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), "Template consumed");
		helper.assertEquals(0, player.getInventory().countItem(Items.NETHERITE_INGOT), "Addition consumed");
		helper.succeed();
	}
}
