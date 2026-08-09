package mezz.jei.neoforge.compat.ae2.patternencoding;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.packets.PlayToServerPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class PacketEncodeRecipeChainPatterns extends PlayToServerPacket<PacketEncodeRecipeChainPatterns> {
	public static final CustomPacketPayload.Type<PacketEncodeRecipeChainPatterns> TYPE = new CustomPacketPayload.Type<>(
		ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "encode_recipe_chain_patterns")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketEncodeRecipeChainPatterns> STREAM_CODEC = StreamCodec.composite(
		JeiPatternEncodeRequestWire.STREAM_CODEC.apply(ByteBufCodecs.list(JeiPatternEncodeRequestWire.MAX_REQUESTS)),
		packet -> packet.requests,
		PacketEncodeRecipeChainPatterns::new
	);

	private final List<JeiPatternEncodeRequestWire> requests;

	public PacketEncodeRecipeChainPatterns(List<JeiPatternEncodeRequestWire> requests) {
		this.requests = requests.stream()
			.limit(JeiPatternEncodeRequestWire.MAX_REQUESTS)
			.toList();
	}

	@Override
	public Type<PacketEncodeRecipeChainPatterns> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketEncodeRecipeChainPatterns> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		ServerPlayer player = context.player();
		JeiBatchPatternEncodeResult result = JeiRecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, player.containerMenu, requests);
		player.sendSystemMessage(formatResult(result));
	}

	private static Component formatResult(JeiBatchPatternEncodeResult result) {
		if (result.stopReason() == JeiPatternEncodeStopReason.NONE) {
			return Component.translatable(
				"jei.message.ae2.pattern_encoding.result",
				result.encodedCount(),
				result.skippedExistingCount(),
				result.skippedInvalidCount()
			);
		}
		return Component.translatable(
			"jei.message.ae2.pattern_encoding.result_stopped",
			result.encodedCount(),
			result.skippedExistingCount(),
			result.skippedInvalidCount(),
			result.stopReason().name()
		);
	}
}
