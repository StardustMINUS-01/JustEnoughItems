package mezz.jei.forge.compat.ae2.patternencoding;

import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.ServerPacketData;
import mezz.jei.common.network.packets.PacketJei;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketEncodeRecipeChainPatterns extends PacketJei {
	private final List<JeiPatternEncodeRequestWire> requests;

	public PacketEncodeRecipeChainPatterns(List<JeiPatternEncodeRequestWire> requests) {
		if (requests.size() > JeiPatternEncodeRequestWire.MAX_REQUESTS) {
			throw new IllegalArgumentException("Too many requests: " + requests.size());
		}
		this.requests = List.copyOf(requests);
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.ENCODE_RECIPE_CHAIN_PATTERNS;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(requests.size());
		for (JeiPatternEncodeRequestWire request : requests) {
			JeiPatternEncodeRequestWire.write(buf, request);
		}
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		FriendlyByteBuf buf = data.buf();
		int requestCount = buf.readVarInt();
		if (requestCount < 0 || requestCount > JeiPatternEncodeRequestWire.MAX_REQUESTS) {
			throw new IllegalArgumentException("Invalid request count: " + requestCount);
		}
		List<JeiPatternEncodeRequestWire> requests = new ArrayList<>(requestCount);
		for (int i = 0; i < requestCount; i++) {
			requests.add(JeiPatternEncodeRequestWire.read(buf));
		}
		ServerPacketContext context = data.context();
		ServerPlayer player = context.player();
		MinecraftServer server = player.server;
		return server.submit(() -> {
			JeiBatchPatternEncodeResult result = JeiRecipeChainPatternEncodingService.encodeRecipeChainPatterns(
				player,
				player.containerMenu,
				requests
			);
			player.sendSystemMessage(formatResult(result));
		});
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
