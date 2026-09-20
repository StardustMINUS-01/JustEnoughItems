package mezz.jei.common.chat;

import io.netty.buffer.Unpooled;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformFluidHelperInternal;
import mezz.jei.common.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.Optional;

public record SharedChatIngredient(boolean fluid, ResourceLocation id, long amount, CompoundTag data) {
	private static final int MAX_BYTES = 16 * 1024;
	public static final int MAX_LENGTH = (MAX_BYTES + 2) / 3 * 4;
	private static final String COMMAND = "jei_internal_ingredient ";

	public static Optional<SharedChatIngredient> from(ITypedIngredient<?> ingredient) {
		try {
			return ingredient.getItemStack().filter(stack -> !stack.isEmpty())
				.map(stack -> new SharedChatIngredient(false, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), stack.save(new CompoundTag())))
				.or(() -> fromFluid(ingredient, Services.PLATFORM.getFluidHelper()));
		} catch (RuntimeException e) {
			// Forge capabilities supplied by other mods can fail during stack serialization.
			return Optional.empty();
		}
	}

	private static <T> Optional<SharedChatIngredient> fromFluid(ITypedIngredient<?> ingredient, IPlatformFluidHelperInternal<T> helper) {
		return ingredient.getIngredient(helper.getFluidIngredientType()).filter(stack -> !helper.isEmpty(stack))
			.map(stack -> new SharedChatIngredient(true, helper.getFluidId(stack), helper.getAmount(stack), helper.getTag(stack).map(CompoundTag::copy).orElseGet(CompoundTag::new)));
	}

	public Optional<String> encode() {
		var buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_BYTES));
		try {
			buffer.writeBoolean(fluid);
			buffer.writeUtf(id.toString(), 256);
			buffer.writeVarLong(amount);
			buffer.writeNbt(data);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			return Optional.of(Base64.getEncoder().encodeToString(bytes));
		} catch (RuntimeException e) {
			// Modded stack data can exceed the bounded share payload.
			return Optional.empty();
		} finally {
			buffer.release();
		}
	}

	public static Optional<SharedChatIngredient> decode(String snapshot) {
		if (snapshot.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		// Both client requests and chat commands contain untrusted NBT.
		try {
			byte[] bytes = Base64.getDecoder().decode(snapshot);
			if (bytes.length > MAX_BYTES) {
				return Optional.empty();
			}
			var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
			try {
				boolean fluid = buffer.readBoolean();
				ResourceLocation id = new ResourceLocation(buffer.readUtf(256));
				long amount = buffer.readVarLong();
				CompoundTag data = buffer.readNbt();
				if (data == null || buffer.isReadable() || amount <= 0 ||
					(fluid ? BuiltInRegistries.FLUID.getOptional(id).filter(value -> value != Fluids.EMPTY).isEmpty() : amount > Integer.MAX_VALUE || BuiltInRegistries.ITEM.getOptional(id).filter(value -> value != Items.AIR).isEmpty())
				) {
					return Optional.empty();
				}
				return Optional.of(new SharedChatIngredient(fluid, id, amount, data));
			} finally {
				buffer.release();
			}
		} catch (RuntimeException e) {
			return Optional.empty();
		}
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
		String name = fluid ? getFluidName(Services.PLATFORM.getFluidHelper()) : createItemStack().getHoverName().getString();
		String title = "[" + SharedConstants.filterText(name) + (fluid ? " " + amount + " mB" : amount > 1 ? " x" + amount : "") + "]";
		return Component.literal(title).withStyle(style -> style.withColor(ChatFormatting.AQUA)
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(title)))
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + snapshot)));
	}

	private <T> String getFluidName(IPlatformFluidHelperInternal<T> helper) {
		return helper.getDisplayName(createFluidStack(helper)).getString();
	}

	public Optional<ITypedIngredient<?>> resolve(IIngredientManager manager) {
		try {
			return fluid ? resolveFluid(manager, Services.PLATFORM.getFluidHelper()) : manager.createTypedIngredient(VanillaTypes.ITEM_STACK, createItemStack(), false).map(value -> value);
		} catch (RuntimeException e) {
			// Third-party stack/capability deserializers can reject received NBT.
			return Optional.empty();
		}
	}

	private ItemStack createItemStack() {
		CompoundTag saved = data.copy();
		saved.putString("id", id.toString());
		// Vanilla saves the count as a byte; the share keeps it separately without truncation.
		saved.putByte("Count", (byte) 1);
		ItemStack stack = ItemStack.of(saved);
		stack.setCount((int) amount);
		return stack;
	}

	private <T> T createFluidStack(IPlatformFluidHelperInternal<T> helper) {
		return helper.create(BuiltInRegistries.FLUID.get(id), amount, data.copy());
	}

	private <T> Optional<ITypedIngredient<?>> resolveFluid(IIngredientManager manager, IPlatformFluidHelperInternal<T> helper) {
		return manager.createTypedIngredient(helper.getFluidIngredientType(), createFluidStack(helper), false).map(value -> value);
	}
}
