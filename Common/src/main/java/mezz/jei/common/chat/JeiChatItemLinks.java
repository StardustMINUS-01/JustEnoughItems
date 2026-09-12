package mezz.jei.common.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public final class JeiChatItemLinks {
	public static final String SHOW_RECIPE_COMMAND = "jei_internal_show";
	public static final String IMPORT_BOOKMARK_GROUP_COMMAND = "jei_internal_import_group";
	public static final String LINK_ARGUMENT = "link";
	public static final int MAX_BOOKMARK_GROUP_ENTRIES = 512;
	public static final int BOOKMARK_GROUP_SNAPSHOT_VERSION = 2;
	public static final int MAX_BOOKMARK_GROUP_LINK_LENGTH = 64 * 1024;
	public static final int MAX_BOOKMARK_GROUP_TITLE_LENGTH = 128;

	private static final String LINK_VERSION = "v1";
	private static final String LINK_VERSION_PREFIX = LINK_VERSION + ":";
	private static final String LINK_MARKER_PREFIX = "[JEI:";
	private static final String LINK_MARKER_SUFFIX = "]";
	private static final String BOOKMARK_GROUP_MARKER_PREFIX = "[JEI-GROUP:";
	private static final char LINK_VALUE_SEPARATOR = ';';

	private JeiChatItemLinks() {
	}

	public record IngredientLink(String ingredientTypeUid, String ingredientUid) {
	}

	public static String createLinkMarker(ITypedIngredient<?> typedIngredient, IIngredientManager ingredientManager) {
		return createLinkMarkerInternal(typedIngredient, ingredientManager);
	}

	public static Component parse(String rawText) {
		return parse(rawText, JeiChatItemLinks::getIngredientName);
	}

	public static Component parse(String rawText, Function<IngredientLink, Optional<String>> ingredientNameLookup) {
		return parseLinkedMessage(rawText, ingredientNameLookup)
			.orElseGet(() -> Component.literal(rawText));
	}

	private static Optional<Component> parseLinkedMessage(String rawText, Function<IngredientLink, Optional<String>> ingredientNameLookup) {
		Optional<ParsedLinkMarker> optionalMarker = findNextLinkMarker(rawText, 0, ingredientNameLookup);
		if (optionalMarker.isEmpty()) {
			return Optional.empty();
		}
		MutableComponent result = Component.empty();
		int lastEnd = 0;

		do {
			ParsedLinkMarker marker = optionalMarker.get();
			if (marker.start() > lastEnd) {
				result.append(Component.literal(rawText.substring(lastEnd, marker.start())));
			}

			result.append(marker.component());

			lastEnd = marker.end();
			optionalMarker = findNextLinkMarker(rawText, lastEnd, ingredientNameLookup);
		} while (optionalMarker.isPresent());

		if (lastEnd < rawText.length()) {
			result.append(Component.literal(rawText.substring(lastEnd)));
		}

		return Optional.of(result);
	}

	public static Optional<Component> parseChatMessage(Component message) {
		return parseChatMessage(message, JeiChatItemLinks::getIngredientName);
	}

	public static Optional<Component> parseChatMessage(Component message, Function<IngredientLink, Optional<String>> ingredientNameLookup) {
		return parseLinkedMessage(message.getString(), ingredientNameLookup);
	}

	public static boolean hasLinkMarkers(String rawText) {
		return findNextLinkMarker(rawText, 0, link -> Optional.empty()).isPresent();
	}

	public static Optional<ITypedIngredient<?>> resolveTypedIngredient(IngredientLink link, IIngredientManager ingredientManager) {
		return ingredientManager.getIngredientTypeForUid(link.ingredientTypeUid())
			.flatMap(ingredientType -> resolveTypedIngredient(ingredientType, link.ingredientUid(), ingredientManager));
	}

	public static String createCommandArgument(IngredientLink link) {
		return LINK_VERSION_PREFIX +
			link.ingredientTypeUid() +
			LINK_VALUE_SEPARATOR +
			link.ingredientUid();
	}

	public static Optional<IngredientLink> parseCommandArgument(String linkText) {
		if (!linkText.startsWith(LINK_VERSION_PREFIX)) {
			return Optional.empty();
		}

		int ingredientTypeUidStart = LINK_VERSION_PREFIX.length();
		int separator = linkText.indexOf(LINK_VALUE_SEPARATOR, ingredientTypeUidStart);
		if (separator <= ingredientTypeUidStart) {
			return Optional.empty();
		}

		int ingredientUidStart = separator + 1;
		if (ingredientUidStart >= linkText.length()) {
			return Optional.empty();
		}

		String ingredientTypeUid = linkText.substring(ingredientTypeUidStart, separator);
		if (!isValidIngredientTypeUid(ingredientTypeUid)) {
			return Optional.empty();
		}

		String ingredientUid = linkText.substring(ingredientUidStart);
		IngredientLink link = new IngredientLink(ingredientTypeUid, ingredientUid);
		return Optional.of(link);
	}

	public static String createShowRecipeCommand(IngredientLink link) {
		return SHOW_RECIPE_COMMAND + " " + createCommandArgument(link);
	}

	public static Optional<IngredientLink> parseShowRecipeCommand(String command) {
		String prefix = SHOW_RECIPE_COMMAND + " ";
		if (!command.startsWith(prefix)) {
			return Optional.empty();
		}

		String linkText = command.substring(prefix.length());
		return parseCommandArgument(linkText);
	}

	public static String createBookmarkGroupLinkMarker(String snapshot) {
		return BOOKMARK_GROUP_MARKER_PREFIX + snapshot + LINK_MARKER_SUFFIX + " ";
	}

	public static boolean isBookmarkGroupLinkLengthValid(String snapshot) {
		int markerOverhead = BOOKMARK_GROUP_MARKER_PREFIX.length() + LINK_MARKER_SUFFIX.length() + 1;
		return snapshot.length() <= MAX_BOOKMARK_GROUP_LINK_LENGTH - markerOverhead;
	}

	public static String createImportBookmarkGroupCommand(String snapshot) {
		return IMPORT_BOOKMARK_GROUP_COMMAND + " " + snapshot;
	}

	public static Optional<String> parseImportBookmarkGroupCommand(String command) {
		String prefix = IMPORT_BOOKMARK_GROUP_COMMAND + " ";
		if (!command.startsWith(prefix)) {
			return Optional.empty();
		}
		String snapshot = command.substring(prefix.length());
		return snapshot.isEmpty() ? Optional.empty() : Optional.of(snapshot);
	}

	public static boolean isValidBookmarkGroupSnapshot(String snapshot) {
		return parseBookmarkGroupSnapshot(snapshot).isPresent();
	}

	private static String createLinkMarker(IngredientLink link) {
		String linkText = createCommandArgument(link);
		return LINK_MARKER_PREFIX + linkText + LINK_MARKER_SUFFIX + " ";
	}

	private static <T> String createLinkMarkerInternal(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientType<T> ingredientType = typedIngredient.getType();
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredientType);
		Object uid = ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
		String ingredientUid = getIngredientUidString(uid);

		IngredientLink link = new IngredientLink(ingredientType.getUid(), ingredientUid);
		return createLinkMarker(link);
	}

	private static <T> Optional<ITypedIngredient<T>> resolveTypedIngredient(
		IIngredientType<T> ingredientType,
		String ingredientUid,
		IIngredientManager ingredientManager
	) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredientType);
		return ingredientManager.getAllTypedIngredients(ingredientType)
			.stream()
			.filter(typedIngredient -> {
				Object uid = ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
				String candidateUid = getIngredientUidString(uid);
				return ingredientUid.equals(candidateUid);
			})
			.findFirst();
	}

	private static Optional<ParsedLinkMarker> findNextLinkMarker(
		String rawText,
		int searchStart,
		Function<IngredientLink, Optional<String>> ingredientNameLookup
	) {
		while (searchStart < rawText.length()) {
			int markerStart = rawText.indexOf("[JEI", searchStart);
			if (markerStart < 0) {
				return Optional.empty();
			}
			searchStart = markerStart + 1;
			boolean groupMarker = rawText.startsWith(BOOKMARK_GROUP_MARKER_PREFIX, markerStart);
			if (!groupMarker && !rawText.startsWith(LINK_MARKER_PREFIX, markerStart)) {
				continue;
			}
			Optional<ParsedLinkMarker> marker = groupMarker ? parseBookmarkGroupMarker(rawText, markerStart) : parseLinkMarker(rawText, markerStart)
				.map(linkMarker -> new ParsedLinkMarker(
					linkMarker.start(),
					linkMarker.end(),
					createLinkComponent(linkMarker.link(), ingredientNameLookup)
				));
			if (marker.isPresent()) {
				return marker;
			}
		}
		return Optional.empty();
	}

	private static Optional<LinkMarker> parseLinkMarker(String rawText, int start) {
		int argumentStart = start + LINK_MARKER_PREFIX.length();
		int markerEnd = rawText.indexOf(LINK_MARKER_SUFFIX, argumentStart);
		if (markerEnd < 0) {
			return Optional.empty();
		}

		String linkText = rawText.substring(argumentStart, markerEnd);
		Optional<IngredientLink> optionalLink = parseCommandArgument(linkText);
		if (optionalLink.isEmpty()) {
			return Optional.empty();
		}

		IngredientLink link = optionalLink.get();
		LinkMarker marker = new LinkMarker(start, markerEnd + LINK_MARKER_SUFFIX.length(), link);
		return Optional.of(marker);
	}

	private static Optional<ParsedLinkMarker> parseBookmarkGroupMarker(String rawText, int start) {
		int snapshotStart = start + BOOKMARK_GROUP_MARKER_PREFIX.length();
		int markerEnd = rawText.indexOf(LINK_MARKER_SUFFIX, snapshotStart);
		if (markerEnd < 0) {
			return Optional.empty();
		}
		String snapshot = rawText.substring(snapshotStart, markerEnd);
		return parseBookmarkGroupSnapshot(snapshot)
			.map(json -> new ParsedLinkMarker(
				start,
				markerEnd + LINK_MARKER_SUFFIX.length(),
				createBookmarkGroupLinkComponent(snapshot, json)
			));
	}

	private static Optional<JsonObject> parseBookmarkGroupSnapshot(String snapshot) {
		// Bookmark-group snapshots can arrive from an untrusted remote client through the server.
		try {
			if (!isBookmarkGroupLinkLengthValid(snapshot)) {
				return Optional.empty();
			}
			String jsonText = new String(Base64.getUrlDecoder().decode(snapshot), StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
			JsonObject group = json.getAsJsonObject("group");
			int entries = json.getAsJsonArray("bookmarks").size();
			if (json.get("version").getAsInt() != BOOKMARK_GROUP_SNAPSHOT_VERSION ||
				group.get("title").getAsString().length() > MAX_BOOKMARK_GROUP_TITLE_LENGTH ||
				entries <= 0 ||
				entries > MAX_BOOKMARK_GROUP_ENTRIES
			) {
				return Optional.empty();
			}
			return Optional.of(json);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static MutableComponent createBookmarkGroupLinkComponent(String snapshot, JsonObject json) {
		JsonObject group = json.getAsJsonObject("group");
		String title = group.get("title").getAsString();
		boolean craftingMode = group.has("crafting") && group.get("crafting").getAsBoolean();
		int entries = json.getAsJsonArray("bookmarks").size();
		MutableComponent component = Component.literal("[" + title + "]");
		return component.withStyle(style -> style
			.withColor(craftingMode ? ChatFormatting.AQUA : ChatFormatting.GRAY)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, createImportBookmarkGroupCommand(snapshot)))
			.withHoverEvent(new HoverEvent(
				HoverEvent.Action.SHOW_TEXT,
				Component.translatable("jei.chat.bookmark_group.hover", entries)
			))
		);
	}

	private static boolean isValidIngredientTypeUid(String ingredientTypeUid) {
		for (int i = 0; i < ingredientTypeUid.length(); i++) {
			char c = ingredientTypeUid.charAt(i);
			if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '=' || c == '[' || c == ']') {
				return false;
			}
		}
		return true;
	}

	private static Optional<String> getIngredientName(IngredientLink link) {
		Optional<IJeiRuntime> optionalRuntime = Internal.getOptionalJeiRuntime();
		if (optionalRuntime.isEmpty()) {
			return Optional.empty();
		}

		IJeiRuntime runtime = optionalRuntime.get();
		IIngredientManager ingredientManager = runtime.getIngredientManager();
		return resolveTypedIngredient(link, ingredientManager)
			.map(typedIngredient -> getIngredientName(typedIngredient, ingredientManager));
	}

	private static <T> String getIngredientName(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientType<T> ingredientType = typedIngredient.getType();
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredientType);
		return ingredientHelper.getDisplayName(typedIngredient.getIngredient());
	}

	private static String getIngredientUidString(Object uid) {
		if (uid instanceof ResourceLocation resourceLocation) {
			return resourceLocation.toString();
		}
		if (uid instanceof Item item) {
			return BuiltInRegistries.ITEM.wrapAsHolder(item)
				.getRegisteredName();
		}
		if (uid instanceof Fluid fluid) {
			return BuiltInRegistries.FLUID.wrapAsHolder(fluid)
				.getRegisteredName();
		}
		if (uid instanceof Iterable<?> iterable) {
			return getIterableUidString(iterable);
		}
		if (uid instanceof Object[] array) {
			return getIterableUidString(Arrays.asList(array));
		}
		return String.valueOf(uid);
	}

	private static String getIterableUidString(Iterable<?> iterable) {
		StringJoiner joiner = new StringJoiner(",", "(", ")");
		for (Object value : iterable) {
			String valueString = getIngredientUidString(value);
			joiner.add(valueString);
		}
		return joiner.toString();
	}

	private static MutableComponent createLinkComponent(IngredientLink link, Function<IngredientLink, Optional<String>> ingredientNameLookup) {
		Optional<String> optionalIngredientName = ingredientNameLookup.apply(link);
		if (optionalIngredientName.isEmpty()) {
			String ingredientUid = link.ingredientUid();
			return Component.literal("[" + ingredientUid + "]");
		}

		String ingredientName = optionalIngredientName.get();
		MutableComponent component = Component.literal("[" + ingredientName + "]");
		HoverEvent hoverEvent = createHoverEvent(link, ingredientName);
		return component.withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, createShowRecipeCommand(link)))
			.withHoverEvent(hoverEvent)
		);
	}

	private static HoverEvent createHoverEvent(IngredientLink link, String ingredientName) {
		return createItemHoverEvent(link)
			.orElseGet(() -> createTextHoverEvent(ingredientName));
	}

	private static HoverEvent createTextHoverEvent(String ingredientName) {
		MutableComponent hoverText = Component.literal(ingredientName);
		return new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText);
	}

	private static Optional<HoverEvent> createItemHoverEvent(IngredientLink link) {
		if (!link.ingredientTypeUid().equals(VanillaTypes.ITEM_STACK.getUid())) {
			return Optional.empty();
		}

		Optional<IJeiRuntime> optionalRuntime = Internal.getOptionalJeiRuntime();
		if (optionalRuntime.isEmpty()) {
			return Optional.empty();
		}

		IJeiRuntime runtime = optionalRuntime.get();
		IIngredientManager ingredientManager = runtime.getIngredientManager();
		return resolveTypedIngredient(link, ingredientManager)
			.flatMap(ITypedIngredient::getItemStack)
			.filter(stack -> !stack.isEmpty())
			.map(JeiChatItemLinks::createItemStackHoverEvent);
	}

	private static HoverEvent createItemStackHoverEvent(ItemStack stack) {
		ItemStack displayStack = stack.copy();
		return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(displayStack));
	}

	private record LinkMarker(int start, int end, IngredientLink link) {
	}

	private record ParsedLinkMarker(int start, int end, MutableComponent component) {
	}
}
