package mezz.jei.neoforge.compat.ae2.patternencoding;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
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

	public static final Type<PacketEncodeRecipeChainPatterns> WITH_FAILURES = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "encode_recipe_chain_patterns_with_failures"));
	private static final StreamCodec<RegistryFriendlyByteBuf, JeiPatternEncodeEntryResult> FAILURE_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC, JeiPatternEncodeEntryResult::recipeTypeUid,
		ResourceLocation.STREAM_CODEC, JeiPatternEncodeEntryResult::recipeUid,
		(type, id) -> new JeiPatternEncodeEntryResult(type, id, JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketEncodeRecipeChainPatterns> WITH_FAILURES_CODEC = StreamCodec.composite(
		JeiPatternEncodeRequestWire.STREAM_CODEC.apply(ByteBufCodecs.list(JeiPatternEncodeRequestWire.MAX_REQUESTS)), packet -> packet.requests,
		FAILURE_CODEC.apply(ByteBufCodecs.list(JeiPatternEncodeRequestWire.MAX_REQUESTS)), packet -> packet.failures,
		PacketEncodeRecipeChainPatterns::new
	);

	private final List<JeiPatternEncodeRequestWire> requests;
	private final List<JeiPatternEncodeEntryResult> failures;

	public PacketEncodeRecipeChainPatterns(List<JeiPatternEncodeRequestWire> requests) {
		this(requests.stream().limit(JeiPatternEncodeRequestWire.MAX_REQUESTS).toList(), List.of());
	}

	private PacketEncodeRecipeChainPatterns(List<JeiPatternEncodeRequestWire> requests, List<JeiPatternEncodeEntryResult> failures) {
		if (requests.size() + failures.size() > JeiPatternEncodeRequestWire.MAX_REQUESTS) {
			throw new IllegalArgumentException("Too many pattern encoding requests");
		}
		this.requests = List.copyOf(requests);
		this.failures = List.copyOf(failures);
	}

	public static PacketEncodeRecipeChainPatterns create(List<JeiPatternEncodeRequestWire> requests, List<JeiPatternEncodeEntryResult> failures, boolean supportsFailures) {
		if (supportsFailures || failures.isEmpty()) {
			return new PacketEncodeRecipeChainPatterns(requests, failures);
		}
		// Older servers count processing recipes with no inputs or outputs as invalid.
		List<JeiPatternEncodeRequestWire> legacyRequests = new ArrayList<>(failures.size() + requests.size());
		for (var failure : failures) {
			legacyRequests.add(new JeiPatternEncodeRequestWire(failure.recipeTypeUid(), failure.recipeUid(), JeiPatternEncodeMode.PROCESSING,
				List.of(), List.of(), List.of(), List.of(), null, false, false));
		}
		legacyRequests.addAll(requests);
		return new PacketEncodeRecipeChainPatterns(legacyRequests);
	}

	@Override
	public Type<PacketEncodeRecipeChainPatterns> type() {
		return failures.isEmpty() ? TYPE : WITH_FAILURES;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketEncodeRecipeChainPatterns> streamCodec() {
		return failures.isEmpty() ? STREAM_CODEC : WITH_FAILURES_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		ServerPlayer player = context.player();
		JeiBatchPatternEncodeResult result = mergeFailures(JeiRecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, player.containerMenu, requests));
		if (player.connection.hasChannel(PacketPatternEncodingResult.TYPE)) {
			context.connection().sendPacketToClient(PacketPatternEncodingResult.from(result), player);
		} else {
			player.sendSystemMessage(formatResult(result));
		}
	}

	JeiBatchPatternEncodeResult mergeFailures(JeiBatchPatternEncodeResult result) {
		if (failures.isEmpty()) {
			return result;
		}
		List<JeiPatternEncodeEntryResult> entries = new ArrayList<>(failures.size() + result.entries().size());
		entries.addAll(failures);
		entries.addAll(result.entries());
		return JeiBatchPatternEncodeResult.fromEntries(result.stopReason(), entries);
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
