package mezz.jei.neoforge.compat.ae2.patternencoding;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.network.ClientPacketContext;
import mezz.jei.common.network.codecs.EnumStreamCodec;
import mezz.jei.common.network.packets.PlayToClientPacket;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.Entry;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.Outcome;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.RecipeKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class PacketPatternEncodingResult extends PlayToClientPacket<PacketPatternEncodingResult> {
	public static final Type<PacketPatternEncodingResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "pattern_encoding_result"));
	private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC, entry -> entry.recipe().type(),
		ResourceLocation.STREAM_CODEC, entry -> entry.recipe().id(),
		new EnumStreamCodec<>(Outcome.class), Entry::outcome,
		(type, id, outcome) -> new Entry(new RecipeKey(type, id), outcome)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketPatternEncodingResult> STREAM_CODEC = StreamCodec.composite(
		ComponentSerialization.STREAM_CODEC, packet -> packet.message,
		ENTRY_CODEC.apply(ByteBufCodecs.list(JeiPatternEncodeRequestWire.MAX_REQUESTS)), packet -> packet.entries,
		PacketPatternEncodingResult::new
	);

	final Component message;
	final List<Entry> entries;

	private PacketPatternEncodingResult(Component message, List<Entry> entries) {
		this.message = message;
		this.entries = List.copyOf(entries);
	}

	public static PacketPatternEncodingResult invalidRecipes(List<JeiPatternEncodeEntryResult> failures) {
		return from(JeiBatchPatternEncodeResult.fromEntries(JeiPatternEncodeStopReason.NONE, failures));
	}

	static PacketPatternEncodingResult from(JeiBatchPatternEncodeResult result) {
		List<Entry> entries = new ArrayList<>(result.entries().size());
		for (var entry : result.entries()) {
			Outcome outcome = switch (entry.status()) {
				case ENCODED -> Outcome.ENCODED;
				case INVENTORY_FULL -> Outcome.INVENTORY_FULL;
				case FAILED_NO_BLANK_PATTERN, SKIPPED_EXISTING_PRIMARY_OUTPUT, SKIPPED_INVALID_CATALYST, SKIPPED_INVALID_RECIPE, SKIPPED_INVALID_PATTERN -> Outcome.FAILED;
				case NOT_PROCESSED -> Outcome.NONE;
			};
			entries.add(new Entry(new RecipeKey(entry.recipeTypeUid(), entry.recipeUid()), outcome));
		}
		String prefix = "jei.message.ae2.pattern_encoding.";
		List<Component> parts = new ArrayList<>();
		switch (result.stopReason()) {
			case NO_BLANK_PATTERN -> parts.add(Component.translatable(prefix + "no_blank"));
			case NO_INVENTORY_SPACE -> parts.add(Component.translatable(prefix + "inventory_full"));
			case NOT_PATTERN_ENCODING_TERMINAL -> parts.add(Component.translatable(prefix + "not_terminal"));
			case NONE -> {}
		}
		if (result.encodedCount() > 0) {
			parts.add(Component.translatable(prefix + "encoded", result.encodedCount()));
		}
		if (result.skippedExistingCount() > 0) {
			parts.add(result.entries().size() == 1
				? Component.translatableWithFallback(prefix + "existing_single", "This output already has a pattern")
				: Component.translatable(prefix + "existing", result.skippedExistingCount()));
		}
		if (result.skippedInvalidCount() > 0) {
			parts.add(Component.translatable(prefix + "invalid", result.skippedInvalidCount()));
		}
		var message = Component.empty();
		for (Component part : parts) {
			if (!message.getSiblings().isEmpty()) {
				message.append(Component.translatableWithFallback(prefix + "separator", ", "));
			}
			message.append(part);
		}
		return new PacketPatternEncodingResult(message, entries);
	}

	@Override
	public Type<PacketPatternEncodingResult> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketPatternEncodingResult> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ClientPacketContext context) {
		show();
	}

	public void show() {
		PatternEncodingFeedback.INSTANCE.show(message, entries);
	}
}
