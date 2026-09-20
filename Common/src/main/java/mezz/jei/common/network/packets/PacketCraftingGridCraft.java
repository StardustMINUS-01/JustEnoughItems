package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketCraftingGridCraft extends PacketJei {
	public static final ResourceLocation CHANNEL = new ResourceLocation("jei", "bookmark_crafting");
	private static final int MAX_TARGET_STACKS = 9;
	private final int containerId;
	private final int taskId;
	private final int requestId;
	private final @Nullable ResourceLocation recipeId;
	private final int multiplier;
	private final List<ItemStack> targetStacks;

	public PacketCraftingGridCraft(int containerId, @Nullable ResourceLocation recipeId, int multiplier, List<ItemStack> targetStacks) {
		this(containerId, 0, 0, recipeId, multiplier, targetStacks);
	}

	public PacketCraftingGridCraft(int containerId, int taskId, int requestId, @Nullable ResourceLocation recipeId, int multiplier, List<ItemStack> targetStacks) {
		this.containerId = containerId;
		this.taskId = Math.max(0, taskId);
		this.requestId = Math.max(0, requestId);
		this.recipeId = recipeId;
		this.multiplier = Math.max(0, multiplier);
		this.targetStacks = targetStacks.stream().limit(MAX_TARGET_STACKS).map(ItemStack::copy).toList();
	}

	@Override
	public ResourceLocation getChannelId() {
		return CHANNEL;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.CRAFTING_GRID_CRAFT_WITH_RECIPE;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(containerId);
		buf.writeVarInt(taskId);
		buf.writeVarInt(requestId);
		buf.writeNullable(recipeId, FriendlyByteBuf::writeResourceLocation);
		buf.writeVarInt(multiplier);
		buf.writeVarInt(targetStacks.size());
		for (ItemStack stack : targetStacks) {
			buf.writeItem(stack);
		}
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		var buf = data.buf();
		int containerId = buf.readVarInt();
		int taskId = buf.readVarInt();
		int requestId = buf.readVarInt();
		ResourceLocation recipeId = buf.readNullable(FriendlyByteBuf::readResourceLocation);
		int multiplier = buf.readVarInt();
		int count = buf.readVarInt();
		// A crafting grid packet from a remote client may contain at most nine slots.
		if (count < 0 || count > MAX_TARGET_STACKS) {
			throw new IllegalArgumentException("Invalid crafting slot count: " + count);
		}
		List<ItemStack> targetStacks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			targetStacks.add(buf.readItem());
		}
		var player = data.context().player();
		return player.server.submit(() -> {
			int crafted = CraftingGridCraftExecutors.craft(player, containerId, recipeId, targetStacks, multiplier);
			if (requestId > 0) {
				data.context().connection().sendPacketToClient(new PacketCraftingGridCraftAck(taskId, requestId, crafted), player);
			}
		});
	}
}
