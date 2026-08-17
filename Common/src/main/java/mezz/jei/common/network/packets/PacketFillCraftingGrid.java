package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.CraftingGridFillExecutors;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketFillCraftingGrid extends PacketJei {
	private static final int MAX_TARGET_STACKS = 9;

	private final int containerId;
	private final int multiplier;
	private final List<ItemStack> targetStacks;

	public PacketFillCraftingGrid(int containerId, int multiplier, List<ItemStack> targetStacks) {
		this.containerId = containerId;
		this.multiplier = Math.max(0, multiplier);
		this.targetStacks = targetStacks.stream()
			.limit(MAX_TARGET_STACKS)
			.map(ItemStack::copy)
			.toList();
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.FILL_CRAFTING_GRID;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(containerId);
		buf.writeVarInt(multiplier);
		buf.writeVarInt(targetStacks.size());
		for (ItemStack stack : targetStacks) {
			buf.writeItem(stack);
		}
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		FriendlyByteBuf buf = data.buf();
		ServerPacketContext context = data.context();
		ServerPlayer player = context.player();
		int containerId = buf.readVarInt();
		int multiplier = buf.readVarInt();
		int count = buf.readVarInt();
		List<ItemStack> targetStacks = new ArrayList<>(Math.min(count, MAX_TARGET_STACKS));
		for (int i = 0; i < count && i < MAX_TARGET_STACKS; i++) {
			targetStacks.add(buf.readItem());
		}
		for (int i = targetStacks.size(); i < count; i++) {
			buf.readItem();
		}
		MinecraftServer server = player.server;
		return server.submit(() -> {
			CraftingGridFillExecutors.fill(player, containerId, targetStacks, multiplier);
		});
	}
}
