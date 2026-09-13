package mezz.jei.common.chat;

import io.netty.buffer.Unpooled;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.Optional;

public record SharedChatIngredient(boolean fluid, ResourceLocation id, int amount, DataComponentPatch components) {
	private static final int MAX_BYTES = 16 * 1024;
	public static final int MAX_LENGTH = (MAX_BYTES + 2) / 3 * 4;
	private static final String COMMAND = "jei_internal_ingredient ";
	private static final StreamCodec<RegistryFriendlyByteBuf, SharedChatIngredient> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, SharedChatIngredient::fluid,
		ResourceLocation.STREAM_CODEC, SharedChatIngredient::id,
		ByteBufCodecs.VAR_INT, SharedChatIngredient::amount,
		DataComponentPatch.STREAM_CODEC, SharedChatIngredient::components,
		SharedChatIngredient::new
	);

	public static Optional<SharedChatIngredient> from(ITypedIngredient<?> ingredient) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> new SharedChatIngredient(false, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), stack.getComponentsPatch()))
			.or(() -> fromFluid(ingredient, Services.PLATFORM.getFluidHelper()));
	}

	private static <T> Optional<SharedChatIngredient> fromFluid(ITypedIngredient<?> ingredient, IPlatformFluidHelperInternal<T> helper) {
		return ingredient.getIngredient(helper.getFluidIngredientType())
			.filter(stack -> !helper.isEmpty(stack))
			.map(stack -> new SharedChatIngredient(true, helper.getFluidId(stack), Math.toIntExact(helper.getAmount(stack)), helper.getComponentsPatch(stack)));
	}

	public Optional<String> encode(RegistryAccess registries) {
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_BYTES), registries);
		try {
			CODEC.encode(buffer, this);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			return Optional.of(Base64.getEncoder().encodeToString(bytes));
		} catch (RuntimeException e) {
			// Components may exceed the share limit or contain values that cannot be sent over the network.
			return Optional.empty();
		} finally {
			buffer.release();
		}
	}

	public static Optional<SharedChatIngredient> decode(String snapshot, RegistryAccess registries) {
		if (snapshot.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		// Chat links and client requests are untrusted, including their component codecs.
		try {
			byte[] bytes = Base64.getDecoder().decode(snapshot);
			if (bytes.length > MAX_BYTES) {
				return Optional.empty();
			}
			var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registries);
			try {
				SharedChatIngredient ingredient = CODEC.decode(buffer);
				if (buffer.isReadable() || !ingredient.isValid()) {
					return Optional.empty();
				}
				return Optional.of(ingredient);
			} finally {
				buffer.release();
			}
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private boolean isValid() {
		if (amount <= 0) {
			return false;
		}
		if (fluid) {
			return BuiltInRegistries.FLUID.getOptional(id).filter(value -> value != Fluids.EMPTY).isPresent();
		}
		return BuiltInRegistries.ITEM.getOptional(id).filter(value -> value != net.minecraft.world.item.Items.AIR).isPresent();
	}

	public static Optional<String> getSnapshot(@Nullable Style style) {
		ClickEvent click = style == null ? null : style.getClickEvent();
		if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND || !click.getValue().startsWith(COMMAND)) {
			return Optional.empty();
		}
		String snapshot = click.getValue().substring(COMMAND.length());
		return snapshot.length() <= MAX_LENGTH ? Optional.of(snapshot) : Optional.empty();
	}

	public Component createLink(String snapshot) {
		Component link;
		if (fluid) {
			link = createFluidLink(Services.PLATFORM.getFluidHelper());
		} else {
			ItemStack stack = createItemStack();
			String name = StringUtil.filterText(stack.getHoverName().getString());
			// Vanilla hover serialization only accepts counts up to 99; the snapshot keeps the real count.
			stack.setCount(Math.min(amount, 99));
			link = Component.literal("[" + name + (amount > 1 ? " x" + amount : "") + "]")
				.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
		}
		return link.copy().withStyle(style -> style.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + snapshot)));
	}

	private <T> Component createFluidLink(IPlatformFluidHelperInternal<T> helper) {
		String name = StringUtil.filterText(helper.getDisplayName(createFluidStack(helper)).getString());
		return Component.literal("[" + name + " " + amount + " mB]").withStyle(style -> style.withHoverEvent(new HoverEvent(
			HoverEvent.Action.SHOW_TEXT, Component.literal(name + "\n" + amount + " mB")
		)));
	}

	public Optional<ITypedIngredient<?>> resolve(IIngredientManager manager) {
		if (fluid) {
			return resolveFluid(manager, Services.PLATFORM.getFluidHelper());
		}
		return manager.createTypedIngredient(VanillaTypes.ITEM_STACK, createItemStack(), false).map(ingredient -> ingredient);
	}

	private ItemStack createItemStack() {
		return new ItemStack(BuiltInRegistries.ITEM.getHolder(id).orElseThrow(), amount, components);
	}

	private <T> T createFluidStack(IPlatformFluidHelperInternal<T> helper) {
		return helper.create(BuiltInRegistries.FLUID.getHolder(id).orElseThrow(), amount, components);
	}

	private <T> Optional<ITypedIngredient<?>> resolveFluid(IIngredientManager manager, IPlatformFluidHelperInternal<T> helper) {
		return manager.createTypedIngredient(helper.getFluidIngredientType(), createFluidStack(helper), false).map(ingredient -> ingredient);
	}
}
