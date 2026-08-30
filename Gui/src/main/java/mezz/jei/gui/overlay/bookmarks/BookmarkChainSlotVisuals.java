package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.config.BookmarkRecipeMarkerMode;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Objects;

public final class BookmarkChainSlotVisuals {
	private static final int INGREDIENT_COLOR = 0x6645DA75;
	private static final int RESULT_COLOR = 0x9966CCFF;
	private static final int REMAINDER_COLOR = 0x55A033A0;
	private static final int CATALYST_COLOR = 0x66E8C135;
	private static final int RESULTS_MARKER_BACKGROUND_COLOR = 0x3366CCFF;
	private static final int REMAINDER_MARKER_BACKGROUND_COLOR = 0x33A033A0;
	private static final int CHANCE_AMOUNT_TEXT_COLOR = 0xFFFFAA00;
	private static final int RECIPE_MARKER_TEXT_COLOR = 0xFFADADAD;

	private BookmarkChainSlotVisuals() {
	}

	public static int getColor(RecipeChainItemType type) {
		return switch (type) {
			case INGREDIENT -> INGREDIENT_COLOR;
			case RESULT -> RESULT_COLOR;
			case REMAINDER -> REMAINDER_COLOR;
		};
	}

	public static Optional<BookmarkSlotVisuals> create(BookmarkDisplayEntry<?> entry) {
		return create(entry, BookmarkSlotDisplayMode.DEFAULT);
	}

	public static Optional<BookmarkSlotVisuals> create(BookmarkDisplayEntry<?> entry, BookmarkSlotDisplayMode displayMode) {
		return create(entry, displayMode, BookmarkRecipeMarkerMode.NONE);
	}

	public static Optional<BookmarkSlotVisuals> create(
		BookmarkDisplayEntry<?> entry,
		BookmarkSlotDisplayMode displayMode,
		BookmarkRecipeMarkerMode recipeMarkerMode
	) {
		return createVisuals(entry, displayMode, recipeMarkerMode, false);
	}

	public static Optional<BookmarkSlotVisuals> create(BookmarkDisplayEntry<?> entry, BookmarkSlotVisualContext context) {
		return createVisuals(entry, getEffectiveDisplayMode(entry, context), context.recipeMarkerMode(), isHoveredEntry(entry, context));
	}

	private static Optional<BookmarkSlotVisuals> createVisuals(
		BookmarkDisplayEntry<?> entry,
		BookmarkSlotDisplayMode displayMode,
		BookmarkRecipeMarkerMode recipeMarkerMode,
		boolean markerHovered
	) {
		Optional<RecipeChainItem> chainItem = entry.recipeChainItem();
		if (chainItem.isPresent()) {
			RecipeChainItem item = chainItem.get();
			long displayAmount = getDisplayAmount(item, displayMode);
			Optional<String> multiplierText = getMultiplierText(entry, item, displayMode);
			Optional<String> recipeMarkerText = getRecipeMarkerText(entry.metadata(), recipeMarkerMode);
			return Optional.of(new BookmarkSlotVisuals(
				getBackgroundColor(item, displayMode),
				getMarkerBackgroundColor(entry, item, displayMode, recipeMarkerMode, markerHovered),
				formatAmountText(entry.metadata(), displayAmount, displayMode == BookmarkSlotDisplayMode.REAL && entry.metadata().factor() > 0),
				getAmountTextColor(entry.metadata()),
				multiplierText,
				getMultiplierTextColor(entry, item, displayMode, multiplierText),
				recipeMarkerText,
				getRecipeMarkerTextColor(recipeMarkerText),
				Optional.ofNullable(entry.border())
			));
		}

		BookmarkItemMetadata metadata = entry.metadata();
		if ((metadata.type().isGraphInput() && displayMode != BookmarkSlotDisplayMode.DEFAULT || metadata.type().isCatalyst()) && metadata.recipeUid() != null) {
			Optional<String> recipeMarkerText = getRecipeMarkerText(metadata, recipeMarkerMode);
			return Optional.of(new BookmarkSlotVisuals(
				displayMode == BookmarkSlotDisplayMode.DEFAULT ?
					OptionalInt.empty() :
					OptionalInt.of(metadata.type().isCatalyst() ? CATALYST_COLOR : INGREDIENT_COLOR),
				OptionalInt.empty(),
				formatPositiveAmount(metadata, metadata.amount()),
				getAmountTextColor(metadata),
				Optional.empty(),
				OptionalInt.empty(),
				recipeMarkerText,
				getRecipeMarkerTextColor(recipeMarkerText),
				Optional.ofNullable(entry.border())
			));
		}
		if (!metadata.type().isGraphInput() && metadata.recipeUid() != null) {
			Optional<String> multiplierText = getMultiplierText(entry, null, displayMode);
			Optional<String> recipeMarkerText = getRecipeMarkerText(metadata, recipeMarkerMode);
			return Optional.of(new BookmarkSlotVisuals(
				OptionalInt.empty(),
				getMarkerBackgroundColor(entry, null, displayMode, recipeMarkerMode, markerHovered),
				formatPositiveAmount(metadata, metadata.amount()),
				getAmountTextColor(metadata),
				multiplierText,
				getMultiplierTextColor(entry, null, displayMode, multiplierText),
				recipeMarkerText,
				getRecipeMarkerTextColor(recipeMarkerText),
				Optional.ofNullable(entry.border())
			));
		}

		Optional<String> amountText = formatPlainBookmarkAmount(metadata);
		if (amountText.isPresent()) {
			return Optional.of(new BookmarkSlotVisuals(
				OptionalInt.empty(),
				OptionalInt.empty(),
				amountText,
				getAmountTextColor(metadata),
				Optional.empty(),
				OptionalInt.empty(),
				Optional.empty(),
				OptionalInt.empty(),
				Optional.ofNullable(entry.border())
			));
		}

		return Optional.empty();
	}

	private static BookmarkSlotDisplayMode getEffectiveDisplayMode(BookmarkDisplayEntry<?> entry, BookmarkSlotVisualContext context) {
		BookmarkSlotDisplayMode requestedMode = context.requestedMode();
		if (requestedMode == BookmarkSlotDisplayMode.DEFAULT) {
			return BookmarkSlotDisplayMode.DEFAULT;
		}
		Optional<BookmarkDisplayEntry<?>> hoveredEntry = context.hoveredEntry();
		if (hoveredEntry.isEmpty()) {
			return BookmarkSlotDisplayMode.DEFAULT;
		}
		BookmarkDisplayEntry<?> hovered = hoveredEntry.get();
		if (!Objects.equals(entry.metadata().groupId(), hovered.metadata().groupId())) {
			return BookmarkSlotDisplayMode.DEFAULT;
		}
		if (!entry.collapsed() && entry.viewMode() == BookmarkViewMode.TODO_LIST &&
			context.rowIndex() >= 0 &&
			context.rowIndex() == context.hoveredRowIndex()) {
			return requestedMode;
		}
		if (entry.sourceIndex() == hovered.sourceIndex() || sameRecipe(entry, hovered)) {
			return requestedMode;
		}
		if (requestedMode == BookmarkSlotDisplayMode.SHIFT && entry.recipeChainItem().isPresent() && hovered.recipeChainItem().isPresent()) {
			return BookmarkSlotDisplayMode.SHIFT;
		}
		return BookmarkSlotDisplayMode.DEFAULT;
	}

	private static boolean sameRecipe(BookmarkDisplayEntry<?> entry, BookmarkDisplayEntry<?> hovered) {
		if (entry.displayRecipeUid().isPresent() && hovered.displayRecipeUid().isPresent()) {
			return entry.displayRecipeUid().get().equals(hovered.displayRecipeUid().get());
		}
		return entry.metadata().recipeUid() != null && entry.metadata().recipeUid().equals(hovered.metadata().recipeUid());
	}

	private static boolean isHoveredEntry(BookmarkDisplayEntry<?> entry, BookmarkSlotVisualContext context) {
		return context.hoveredEntry()
			.filter(hovered -> hovered.sourceIndex() == entry.sourceIndex())
			.filter(hovered -> Objects.equals(hovered.item(), entry.item()))
			.isPresent();
	}

	private static int getDisplayColor(RecipeChainItem item, BookmarkSlotDisplayMode displayMode) {
		return getColor(item.type());
	}

	private static OptionalInt getBackgroundColor(RecipeChainItem item, BookmarkSlotDisplayMode displayMode) {
		if (displayMode != BookmarkSlotDisplayMode.REAL) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(getDisplayColor(item, displayMode));
	}

	private static OptionalInt getMarkerBackgroundColor(
		BookmarkDisplayEntry<?> entry,
		RecipeChainItem item,
		BookmarkSlotDisplayMode displayMode,
		BookmarkRecipeMarkerMode recipeMarkerMode,
		boolean markerHovered
	) {
		if (recipeMarkerMode == BookmarkRecipeMarkerMode.BACKGROUND &&
			displayMode == BookmarkSlotDisplayMode.DEFAULT &&
			!entry.metadata().type().isGraphInput() &&
			entry.metadata().recipeUid() != null &&
			!markerHovered) {
			if (item != null && item.type() == RecipeChainItemType.REMAINDER && item.shiftAmount() > 0) {
				return OptionalInt.of(REMAINDER_MARKER_BACKGROUND_COLOR);
			}
			return OptionalInt.of(RESULTS_MARKER_BACKGROUND_COLOR);
		}
		return OptionalInt.empty();
	}

	private static long getDisplayAmount(RecipeChainItem item, BookmarkSlotDisplayMode displayMode) {
		return switch (displayMode) {
			case DEFAULT -> item.calculatedAmount();
			case REAL -> item.realAmount();
			case SHIFT -> item.shiftAmount();
		};
	}

	private static Optional<String> getMultiplierText(BookmarkDisplayEntry<?> entry, RecipeChainItem item, BookmarkSlotDisplayMode displayMode) {
		if (entry.metadata().type().isGraphInput() || !entry.metadata().type().isRecipeAssociated()) {
			return Optional.empty();
		}
		long multiplier = item == null ? entry.metadata().multiplier() : item.realMultiplier();
		if (displayMode == BookmarkSlotDisplayMode.SHIFT && item != null) {
			multiplier = item.calculatedMultiplier();
		}
		if (displayMode != BookmarkSlotDisplayMode.DEFAULT && !entry.metadata().type().isGraphInput() && entry.metadata().recipeUid() != null) {
			return Optional.of("x" + formatAmount(Math.max(0, multiplier)));
		}
		if (item != null && item.type() != RecipeChainItemType.INGREDIENT && item.realMultiplier() != item.calculatedMultiplier()) {
			return Optional.of("x" + formatAmount(Math.max(0, multiplier)));
		}
		if (entry.isOutputRecipe() || multiplier > 1) {
			return Optional.of("x" + formatAmount(Math.max(0, multiplier)));
		}
		return Optional.empty();
	}

	private static OptionalInt getMultiplierTextColor(
		BookmarkDisplayEntry<?> entry,
		RecipeChainItem item,
		BookmarkSlotDisplayMode displayMode,
		Optional<String> multiplierText
	) {
		if (multiplierText.isEmpty() || displayMode != BookmarkSlotDisplayMode.DEFAULT) {
			return OptionalInt.empty();
		}
		long multiplier = item == null ? entry.metadata().multiplier() : item.realMultiplier();
		if (entry.isOutputRecipe() || multiplier > 1) {
			return OptionalInt.of(RECIPE_MARKER_TEXT_COLOR);
		}
		return OptionalInt.empty();
	}

	private static Optional<String> getRecipeMarkerText(BookmarkItemMetadata metadata, BookmarkRecipeMarkerMode recipeMarkerMode) {
		if (metadata.type().isCatalyst()) {
			return Optional.of("C");
		}
		if (recipeMarkerMode == BookmarkRecipeMarkerMode.TEXT &&
			!metadata.type().isGraphInput() &&
			metadata.recipeUid() != null) {
			return Optional.of("R");
		}
		return Optional.empty();
	}

	private static OptionalInt getRecipeMarkerTextColor(Optional<String> recipeMarkerText) {
		if (recipeMarkerText.filter("C"::equals).isPresent()) {
			return OptionalInt.of(0xFFFFFF55);
		}
		if (recipeMarkerText.isPresent()) {
			return OptionalInt.of(RECIPE_MARKER_TEXT_COLOR);
		}
		return OptionalInt.empty();
	}

	private static Optional<String> formatPositiveAmount(BookmarkItemMetadata metadata, long amount) {
		return formatAmountText(metadata, amount, false);
	}

	private static Optional<String> formatPlainBookmarkAmount(BookmarkItemMetadata metadata) {
		long amount = metadata.amount();
		if (amount <= 1 && !shouldShowChanceAmount(metadata)) {
			return Optional.empty();
		}
		return formatPositiveAmount(metadata, amount);
	}

	private static Optional<String> formatAmountText(BookmarkItemMetadata metadata, long amount, boolean showZero) {
		if (amount <= 0) {
			if ((showZero || shouldShowChanceAmount(metadata)) && amount == 0) {
				return Optional.of(formatChancePrefix(metadata) + "0");
			}
			return Optional.empty();
		}
		return Optional.of(formatChancePrefix(metadata) + BookmarkAmountFormatter.formatTypedAmount(amount, metadata.permutations()));
	}

	private static OptionalInt getAmountTextColor(BookmarkItemMetadata metadata) {
		if (metadata.chance() > 0 && metadata.chance() != BookmarkItemMetadata.CHANCE_FULL) {
			return OptionalInt.of(CHANCE_AMOUNT_TEXT_COLOR);
		}
		return OptionalInt.empty();
	}

	private static String formatChancePrefix(BookmarkItemMetadata metadata) {
		if (metadata.chance() > 0 && metadata.chance() != BookmarkItemMetadata.CHANCE_FULL) {
			return "~";
		}
		return "";
	}

	private static boolean shouldShowChanceAmount(BookmarkItemMetadata metadata) {
		return metadata.chance() > 0 &&
			metadata.chance() != BookmarkItemMetadata.CHANCE_FULL &&
			metadata.factor() > 0 &&
			metadata.multiplier() > 0;
	}

	private static String formatAmount(long amount) {
		return BookmarkAmountFormatter.formatItemAmount(amount);
	}
}
