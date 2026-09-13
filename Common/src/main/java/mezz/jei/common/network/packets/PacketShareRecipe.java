package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.common.network.IChatConnection;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class PacketShareRecipe extends PlayToServerPacket<PacketShareRecipe> {
	public static final Type<PacketShareRecipe> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "share_recipe"));
	private static final StreamCodec<io.netty.buffer.ByteBuf, ResourceLocation> ID_CODEC = ByteBufCodecs.stringUtf8(JeiChatRecipeLinks.MAX_ID_LENGTH)
		.map(ResourceLocation::parse, ResourceLocation::toString);
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketShareRecipe> STREAM_CODEC = StreamCodec.composite(
		ID_CODEC, packet -> packet.recipe.recipeType(),
		ID_CODEC, packet -> packet.recipe.recipeId(),
		ByteBufCodecs.stringUtf8(JeiChatRecipeLinks.MAX_TITLE_LENGTH), packet -> packet.title,
		(type, id, title) -> new PacketShareRecipe(new RecipeLink(type, id), title)
	);

	private final RecipeLink recipe;
	private final String title;

	public PacketShareRecipe(RecipeLink recipe, String title) {
		this.recipe = recipe;
		this.title = title;
	}

	@Override
	public Type<PacketShareRecipe> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketShareRecipe> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ServerPacketContext context) {
		if (!title.isBlank() && context.connection() instanceof IChatConnection connection) {
			connection.share(context.player(), JeiChatRecipeLinks.create(recipe, title), TYPE);
		}
	}
}
