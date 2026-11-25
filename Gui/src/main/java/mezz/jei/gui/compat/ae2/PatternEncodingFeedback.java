package mezz.jei.gui.compat.ae2;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

public final class PatternEncodingFeedback {
	public static final PatternEncodingFeedback INSTANCE = new PatternEncodingFeedback(Util::getMillis);
	private static final int DURATION_MS = 1000;
	private static final int FADE_OUT_MS = 150;
	private static final BookmarkSlotVisuals PLAIN_SLOT = new BookmarkSlotVisuals(OptionalInt.empty(), OptionalInt.empty(),
		Optional.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty());

	public enum Outcome {
		ENCODED(0x9956CF72), INVENTORY_FULL(0x99F4CA55), FAILED(0x99EC5A59), NONE(0);

		private final int color;

		Outcome(int color) {
			this.color = color;
		}
	}

	public record RecipeKey(ResourceLocation type, ResourceLocation id) {}
	public record Entry(RecipeKey recipe, Outcome outcome) {}
	private record Highlight(Outcome outcome, long start) {}

	private final LongSupplier clock;
	private final LinkedHashMap<RecipeKey, Highlight> highlights = new LinkedHashMap<>();
	private @Nullable Component message;
	private long messageStart;
	private int layoutWidth;
	private List<FormattedCharSequence> lines = List.of();
	private int textWidth;

	PatternEncodingFeedback(LongSupplier clock) {
		this.clock = clock;
	}

	public void showMessage(Component message) {
		show(message, List.of());
	}

	public <T> void showRecipeFailure(Component message, IRecipeLayoutDrawable<T> layout) {
		var category = layout.getRecipeCategory();
		var id = category.getRegistryName(layout.getRecipe());
		show(message, id == null ? List.of() : List.of(new Entry(new RecipeKey(category.getRecipeType().getUid(), id), Outcome.FAILED)));
	}

	public void show(Component message, List<Entry> entries) {
		long now = clock.getAsLong();
		highlights.values().removeIf(highlight -> now - highlight.start >= DURATION_MS);
		for (Entry entry : entries) {
			highlights.remove(entry.recipe);
			if (entry.outcome != Outcome.NONE) {
				highlights.put(entry.recipe, new Highlight(entry.outcome, now));
			}
		}
		while (highlights.size() > RecipeChainPatternEncodeRequestFactory.MAX_REQUESTS) {
			highlights.pollFirstEntry();
		}
		this.message = message;
		this.messageStart = now;
		this.layoutWidth = 0;
	}

	public Optional<BookmarkSlotVisuals> applyHighlight(BookmarkItemMetadata metadata, Optional<BookmarkSlotVisuals> original) {
		if (highlights.isEmpty() || metadata.recipeUid() == null || metadata.recipeTypeUid() == null) {
			return original;
		}
		Highlight highlight = highlights.get(new RecipeKey(metadata.recipeTypeUid(), metadata.recipeUid()));
		if (highlight == null) {
			return original;
		}
		float opacity = opacity(clock.getAsLong() - highlight.start);
		if (opacity <= 0) {
			return original;
		}
		var visuals = original.orElse(PLAIN_SLOT);
		int target = highlight.outcome.color;
		int base = visuals.backgroundColor().orElse(visuals.markerBackgroundColor().orElse(target & 0xFFFFFF));
		return Optional.of(new BookmarkSlotVisuals(OptionalInt.of(FastColor.ARGB32.lerp(opacity, base, target)), visuals.markerBackgroundColor(),
			visuals.amountText(), visuals.amountTextColor(), visuals.multiplierText(), visuals.multiplierTextColor(),
			visuals.recipeMarkerText(), visuals.recipeMarkerTextColor(), visuals.border()));
	}

	static float opacity(long elapsed) {
		return Math.clamp((DURATION_MS - elapsed) / (float) FADE_OUT_MS, 0f, 1f);
	}

	public void draw(GuiGraphics graphics, Font font) {
		if (message == null) {
			return;
		}
		long elapsed = clock.getAsLong() - messageStart;
		if (elapsed >= DURATION_MS) {
			clear();
			return;
		}
		float opacity = opacity(elapsed);
		int alpha = Math.round(255 * opacity);
		if (alpha < 4) {
			return;
		}
		int availableWidth = Math.max(1, graphics.guiWidth() - 24);
		if (layoutWidth != availableWidth) {
			lines = font.split(message, availableWidth);
			textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
			layoutWidth = availableWidth;
		}
		int center = graphics.guiWidth() / 2;
		int y = 8;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 600);
		graphics.fill(center - textWidth / 2 - 5, y - 3, center + (textWidth + 1) / 2 + 5, y + lines.size() * font.lineHeight + 2, Math.round(190 * opacity) << 24);
		for (var line : lines) {
			graphics.drawString(font, line, center - font.width(line) / 2, y, alpha << 24 | 0xFFFFFF);
			y += font.lineHeight;
		}
		graphics.pose().popPose();
	}

	public void clear() {
		message = null;
		lines = List.of();
		highlights.clear();
	}
}
