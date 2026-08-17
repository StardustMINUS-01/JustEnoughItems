package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketCraftingGridCraft extends PacketJei {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_TARGET_STACKS = 9;

	private final int containerId;
	private final int taskId;
	private final int requestId;
	private final int multiplier;
	private final List<ItemStack> targetStacks;

	public PacketCraftingGridCraft(int containerId, int multiplier, List<ItemStack> targetStacks) {
		this(containerId, 0, 0, multiplier, targetStacks);
	}

	public PacketCraftingGridCraft(int containerId, int requestId, int multiplier, List<ItemStack> targetStacks) {
		this(containerId, 0, requestId, multiplier, targetStacks);
	}

	public PacketCraftingGridCraft(int containerId, int taskId, int requestId, int multiplier, List<ItemStack> targetStacks) {
		this.containerId = containerId;
		this.taskId = Math.max(0, taskId);
		this.requestId = Math.max(0, requestId);
		this.multiplier = Math.max(0, multiplier);
		this.targetStacks = targetStacks.stream()
			.limit(MAX_TARGET_STACKS)
			.map(ItemStack::copy)
			.toList();
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.CRAFTING_GRID_CRAFT;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(containerId);
		buf.writeVarInt(taskId);
		buf.writeVarInt(requestId);
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
		int taskId = buf.readVarInt();
		int requestId = buf.readVarInt();
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
			IConnectionToClient connection = context.connection();
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] PACKET-CRAFT containerId={} taskId={} requestId={} multiplier={} targetStacks={}",
					containerId, taskId, requestId, multiplier, targetStacks);
			}
			int crafted = CraftingGridCraftExecutors.craft(player, containerId, null, targetStacks, multiplier);
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] PACKET-CRAFT-DONE crafted={}", crafted);
			}
			if (requestId > 0) {
				if (DebugConfig.isDebugModeEnabled()) {
					LOGGER.info("[Bug6] PACKET-CRAFT-ACK-SENT taskId={} requestId={} crafted={}", taskId, requestId, crafted);
				}
				connection.sendPacketToClient(new PacketCraftingGridCraftAck(taskId, requestId, crafted), player);
			}
		});
	}
}
