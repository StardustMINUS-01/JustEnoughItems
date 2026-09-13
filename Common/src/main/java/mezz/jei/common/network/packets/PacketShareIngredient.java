package mezz.jei.common.network.packets;

import com.mojang.serialization.JsonOps;
import mezz.jei.api.constants.ModIds;
import mezz.jei.common.chat.SharedChatIngredient;
import mezz.jei.common.network.IChatConnection;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class PacketShareIngredient extends PlayToServerPacket<PacketShareIngredient> {
	public static final Type<PacketShareIngredient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "share_ingredient"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketShareIngredient> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(SharedChatIngredient.MAX_LENGTH), packet -> packet.snapshot,
		PacketShareIngredient::new
	);
	private final String snapshot;

	public PacketShareIngredient(String snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public Type<PacketShareIngredient> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketShareIngredient> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		if (context.connection() instanceof IChatConnection connection) {
			SharedChatIngredient.decode(snapshot, context.player().registryAccess())
				.ifPresent(ingredient -> {
					// Network component codecs can accept values that vanilla chat cannot serialize.
					try {
						Component link = ingredient.createLink(snapshot);
						var ops = context.player().registryAccess().createSerializationContext(JsonOps.INSTANCE);
						if (ComponentSerialization.CODEC.encodeStart(ops, link).result().isPresent()) {
							connection.share(context.player(), link, TYPE);
						}
					} catch (RuntimeException e) {
						// Reject invalid component data supplied by a client before broadcasting it.
					}
				});
		}
	}
}
