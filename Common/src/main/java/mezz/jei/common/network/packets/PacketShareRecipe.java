package mezz.jei.common.network.packets;

import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public final class PacketShareRecipe extends PacketJei {
	private final RecipeLink recipe;
	private final String title;

	public PacketShareRecipe(RecipeLink recipe, String title) {
		this.recipe = recipe;
		this.title = title;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.SHARE_RECIPE;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeUtf(recipe.recipeType().toString(), JeiChatRecipeLinks.MAX_ID_LENGTH);
		buf.writeUtf(recipe.recipeId().toString(), JeiChatRecipeLinks.MAX_ID_LENGTH);
		buf.writeUtf(title, JeiChatRecipeLinks.MAX_TITLE_LENGTH);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		var buf = data.buf();
		RecipeLink recipe = new RecipeLink(new ResourceLocation(buf.readUtf(JeiChatRecipeLinks.MAX_ID_LENGTH)), new ResourceLocation(buf.readUtf(JeiChatRecipeLinks.MAX_ID_LENGTH)));
		String title = buf.readUtf(JeiChatRecipeLinks.MAX_TITLE_LENGTH);
		var player = data.context().player();
		return player.server.submit(() -> {
			if (!title.isBlank()) {
				data.context().connection().shareChatLink(player, JeiChatRecipeLinks.create(recipe, title));
			}
		});
	}
}
