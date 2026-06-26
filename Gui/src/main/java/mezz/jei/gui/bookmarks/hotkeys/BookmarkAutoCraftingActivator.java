package mezz.jei.gui.bookmarks.hotkeys;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.Internal;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class BookmarkAutoCraftingActivator {
	private static final Set<String> CLAIMED_AUTO_CRAFTING_KEYS = new HashSet<>();

	private BookmarkAutoCraftingActivator() {
	}

	public static boolean isAutoCraftingInput(UserInput input, IJeiKeyMapping craftItemsKey) {
		return (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0 &&
			craftItemsKey.matchesIgnoringModifiers(input.getKey());
	}

	public static boolean claimAutoCraftingInput(UserInput input, IJeiKeyMapping craftItemsKey) {
		if (!isAutoCraftingInput(input, craftItemsKey)) {
			return false;
		}
		String keyId = keyId(input.getKey());
		if (CLAIMED_AUTO_CRAFTING_KEYS.contains(keyId)) {
			return false;
		}
		CLAIMED_AUTO_CRAFTING_KEYS.add(keyId);
		return true;
	}

	public static void releaseAutoCraftingInput(InputConstants.Key key) {
		CLAIMED_AUTO_CRAFTING_KEYS.remove(keyId(key));
	}

	public static void clearAutoCraftingInputs() {
		CLAIMED_AUTO_CRAFTING_KEYS.clear();
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated
	) {
		IConnectionToServer serverConnection = Internal.getServerConnection();
		return activate(
			input,
			recipeLayout,
			containerMenu,
			onActivated,
			(input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0,
			JeiClientSoundUtil::playClickSound,
			serverConnection::sendPacketToServer,
			serverConnection.isJeiOnServer(),
			getPlayerInventoryStacks()
		);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		boolean hasShift,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport
	) {
		return activate(input, recipeLayout, containerMenu, onActivated, hasShift, playClickSound, packetSender, hasServerSupport, List.of(), ClientFallbackStarter.DISABLED);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		boolean hasShift,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport,
		List<ItemStack> availableStacks
	) {
		return activate(input, recipeLayout, containerMenu, onActivated, hasShift, playClickSound, packetSender, hasServerSupport, availableStacks, ClientFallbackStarter.DISABLED);
	}

	public static boolean activate(
		UserInput input,
		IRecipeLayoutDrawable<?> recipeLayout,
		@Nullable AbstractContainerMenu containerMenu,
		Runnable onActivated,
		boolean hasShift,
		Runnable playClickSound,
		Consumer<PlayToServerPacket> packetSender,
		boolean hasServerSupport,
		List<ItemStack> availableStacks,
		ClientFallbackStarter clientFallbackStarter
	) {
		if (!hasShift || containerMenu == null) {
			return false;
		}

		int targetSlotCount = BookmarkGhostOverlayTargetSlots.fromMenu(containerMenu).size();
		if (targetSlotCount <= 0) {
			return false;
		}

		return BookmarkCraftingGridFill.create(recipeLayout, targetSlotCount, 0, availableStacks)
			.map(fill -> {
				if (!input.isSimulate()) {
					List<ItemStack> targetStacks = fill.targetStacks();
					if (hasServerSupport) {
						packetSender.accept(new PacketCraftingGridCraft(containerMenu.containerId, fill.multiplier(), targetStacks));
					} else if (!clientFallbackStarter.start(containerMenu, targetStacks)) {
						return false;
					}
					onActivated.run();
					playClickSound.run();
				}
				return true;
			})
			.orElse(false);
	}

	public static List<ItemStack> getPlayerInventoryStacks() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		return stacks;
	}

	private static String keyId(InputConstants.Key key) {
		return key.getType() + ":" + key.getValue();
	}

	@FunctionalInterface
	public interface ClientFallbackStarter {
		ClientFallbackStarter DISABLED = (menu, targetStacks) -> false;

		boolean start(AbstractContainerMenu menu, List<ItemStack> targetStacks);
	}
}
