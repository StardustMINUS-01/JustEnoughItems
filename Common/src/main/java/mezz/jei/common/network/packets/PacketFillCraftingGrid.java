package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.bookmarks.CraftingGridFillExecutors;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PacketFillCraftingGrid extends PlayToServerPacket<PacketFillCraftingGrid> {
	private static final int MAX_TARGET_STACKS = 9;
	public static final CustomPacketPayload.Type<PacketFillCraftingGrid> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "fill_crafting_grid"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFillCraftingGrid> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		p -> p.containerId,
		ByteBufCodecs.VAR_INT,
		p -> p.multiplier,
		ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TARGET_STACKS)),
		p -> p.targetStacks,
		PacketFillCraftingGrid::new
	);

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
	public Type<PacketFillCraftingGrid> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketFillCraftingGrid> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		CraftingGridFillExecutors.fill(context.player(), containerId, targetStacks, multiplier);
	}
}
