package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PacketCraftingGridCraft extends PlayToServerPacket<PacketCraftingGridCraft> {
	private static final int MAX_TARGET_STACKS = 9;
	public static final CustomPacketPayload.Type<PacketCraftingGridCraft> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "crafting_grid_craft"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketCraftingGridCraft> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		p -> p.containerId,
		ByteBufCodecs.VAR_INT,
		p -> p.taskId,
		ByteBufCodecs.VAR_INT,
		p -> p.requestId,
		ByteBufCodecs.VAR_INT,
		p -> p.multiplier,
		ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TARGET_STACKS)),
		p -> p.targetStacks,
		PacketCraftingGridCraft::new
	);

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
	public Type<PacketCraftingGridCraft> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketCraftingGridCraft> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		IConnectionToClient connection = context.connection();
		int crafted = CraftingGridCraftExecutors.craft(context.player(), containerId, targetStacks, multiplier);
		if (requestId > 0) {
			connection.sendPacketToClient(new PacketCraftingGridCraftAck(taskId, requestId, crafted), context.player());
		}
	}
}
