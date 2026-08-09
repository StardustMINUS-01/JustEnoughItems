package mezz.jei.gui.overlay.ingredients;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.GuiRenderLayers;
import mezz.jei.common.gui.elements.OffsetDrawable;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.SafeIngredientUtil;
import mezz.jei.common.collect.ListMultiMap;
import mezz.jei.gui.bookmarks.BookmarkSlotBorder;
import mezz.jei.gui.overlay.IngredientListSlotContext;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public class IngredientListRenderer {
	private static final int BLACKLIST_COLOR = 0xDDFF0000;
	private static final int WILDCARD_BLACKLIST_COLOR = 0xDDFFA500;
	private static final float BOOKMARK_SLOT_TEXT_SCALE = 0.65f;

	private final List<IngredientListSlot> slots = new ArrayList<>();
	private final ListMultiMap<IIngredientType<?>, BatchRenderElement<?>> renderElementsByType = new ListMultiMap<>();
	private final List<IDrawable> renderOverlays = new ArrayList<>();
	private final IIngredientManager ingredientManager;
	private final boolean searchable;
	private Function<IngredientListSlotContext, Optional<BookmarkSlotVisuals>> slotVisualsResolver = context -> Optional.empty();
	private Optional<IngredientListSlot> hoveredSlot = Optional.empty();

	private int blocked = 0;

	public IngredientListRenderer(IIngredientManager ingredientManager, boolean searchable) {
		this.ingredientManager = ingredientManager;
		this.searchable = searchable;
	}

	public void clear() {
		slots.clear();
		renderElementsByType.clear();
		renderOverlays.clear();
		blocked = 0;
	}

	public int size() {
		return (int) slots.stream()
			.filter(slot -> !slot.isBlocked())
			.count();
	}

	public void add(IngredientListSlot ingredientListSlot) {
		slots.add(ingredientListSlot);
		addRenderElement(ingredientListSlot);
	}

	public void setSlotVisualsResolver(Function<IngredientListSlotContext, Optional<BookmarkSlotVisuals>> slotVisualsResolver) {
		this.slotVisualsResolver = slotVisualsResolver;
	}

	private void addRenderElement(IngredientListSlot ingredientListSlot) {
		ingredientListSlot.getOptionalElement()
			.ifPresent(element -> {
				ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
				IIngredientType<?> ingredientType = typedIngredient.getType();
				ImmutableRect2i renderArea = ingredientListSlot.getRenderArea();
				BatchRenderElement<?> batchRenderElement = new BatchRenderElement<>(typedIngredient.getIngredient(), renderArea.x(), renderArea.y());
				renderElementsByType.put(ingredientType, batchRenderElement);
				IDrawable renderOverlay = element.createRenderOverlay();
				if (renderOverlay != null) {
					renderOverlays.add(OffsetDrawable.create(renderOverlay, renderArea.x(), renderArea.y()));
				}
			});
	}

	public Stream<IngredientListSlot> getSlots() {
		return slots.stream()
			.filter(s -> !s.isBlocked());
	}

	public int getColumnCount() {
		return slots.stream()
			.filter(slot -> !slot.isBlocked())
			.findFirst()
			.map(first -> (int) slots.stream()
				.filter(slot -> !slot.isBlocked())
				.filter(slot -> slot.getArea().getY() == first.getArea().getY())
				.count())
			.orElse(0);
	}

	public void set(final int startIndex, List<IElement<?>> ingredientList) {
		blocked = 0;
		renderElementsByType.clear();
		renderOverlays.clear();

		Iterator<IElement<?>> elementIterator = ingredientList.listIterator(startIndex);

		for (IngredientListSlot ingredientListSlot : slots) {
			if (ingredientListSlot.isBlocked()) {
				ingredientListSlot.clear();
				blocked++;
			} else if (elementIterator.hasNext()) {
				IElement<?> element = elementIterator.next();
				while (shouldSkipInvisibleElement(element) && elementIterator.hasNext()) {
					element = elementIterator.next();
				}
				if (element.isLayoutPlaceholder()) {
					ingredientListSlot.clear();
				} else if (element.isVisible()) {
					ingredientListSlot.setElement(element);
					addRenderElement(ingredientListSlot);
				} else if (element.reservesInvisibleSpace()) {
					ingredientListSlot.setElement(element);
				} else {
					ingredientListSlot.clear();
				}
			} else {
				ingredientListSlot.clear();
			}
		}
	}

	private static boolean shouldSkipInvisibleElement(IElement<?> element) {
		return !element.isLayoutPlaceholder() &&
			!element.isVisible() &&
			!element.reservesInvisibleSpace();
	}

	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		this.hoveredSlot = findHoveredSlot(mouseX, mouseY);
		renderSlotBackgrounds(guiGraphics);
		renderSlotBorders(guiGraphics);

		if (searchable && Internal.getClientToggleState().isEditModeEnabled()) {
			renderEditMode(guiGraphics);
		}

		for (Map.Entry<IIngredientType<?>, List<BatchRenderElement<?>>> entry : renderElementsByType.entrySet()) {
			renderBatch(guiGraphics, entry);
		}

		for (IDrawable overlay : renderOverlays) {
			overlay.draw(guiGraphics);
		}

		renderSlotTextOverlays(guiGraphics);
		this.hoveredSlot = Optional.empty();
	}

	private Optional<IngredientListSlot> findHoveredSlot(int mouseX, int mouseY) {
		return slots.stream()
			.filter(slot -> !slot.isBlocked())
			.filter(slot -> slot.isMouseOver(mouseX, mouseY))
			.findFirst();
	}

	private void renderSlotBackgrounds(GuiGraphics guiGraphics) {
		boolean drewBackground = false;
		int hoveredSlotIndex = hoveredSlot.map(slots::indexOf).orElse(-1);
		for (int i = 0; i < slots.size(); i++) {
			IngredientListSlot slot = slots.get(i);
			Optional<BookmarkSlotVisuals> visuals = getSlotVisuals(slot, i, hoveredSlotIndex);
			OptionalInt backgroundColor = visuals
				.map(BookmarkSlotVisuals::backgroundColor)
				.orElseGet(OptionalInt::empty);
			OptionalInt markerBackgroundColor = visuals
				.map(BookmarkSlotVisuals::markerBackgroundColor)
				.orElseGet(OptionalInt::empty);
			OptionalInt color = backgroundColor.isPresent() ? backgroundColor : markerBackgroundColor;
			if (color.isPresent()) {
				drewBackground = true;
				ImmutableRect2i area = slot.getArea();
				RenderSystem.enableBlend();
				guiGraphics.fill(
					RenderType.guiOverlay(),
					area.getX(),
					area.getY(),
					area.getX() + area.getWidth(),
					area.getY() + area.getHeight(),
					color.getAsInt()
				);
			}
		}
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		if (drewBackground) {
			RenderSystem.disableBlend();
		}
	}

	private void renderSlotBorders(GuiGraphics guiGraphics) {
		Matrix4f matrix = guiGraphics.pose().last().pose();
		BufferBuilder bufferBuilder = null;
		int hoveredSlotIndex = hoveredSlot.map(slots::indexOf).orElse(-1);
		for (int i = 0; i < slots.size(); i++) {
			IngredientListSlot slot = slots.get(i);
			Optional<BookmarkSlotBorder> border = getSlotVisuals(slot, i, hoveredSlotIndex)
				.flatMap(BookmarkSlotVisuals::border);
			if (border.isPresent()) {
				if (bufferBuilder == null) {
					bufferBuilder = Tesselator.getInstance()
						.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
				}
				drawBorder(bufferBuilder, matrix, slot.getArea(), border.get());
			}
		}
		if (bufferBuilder != null) {
			RenderSystem.enableBlend();
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
			RenderSystem.disableBlend();
		}
	}

	private static void drawBorder(
		BufferBuilder bufferBuilder,
		Matrix4f matrix,
		ImmutableRect2i area,
		BookmarkSlotBorder border
	) {
		int color = border.color();
		float x = area.getX();
		float y = area.getY();
		float width = area.getWidth();
		float height = area.getHeight();
		if (border.left()) {
			addQuad(bufferBuilder, matrix, x - 0.5F, y - 0.5F, x + 0.5F, y + height + 0.5F, color);
		}
		if (border.right()) {
			addQuad(bufferBuilder, matrix, x + width - 0.5F, y - 0.5F, x + width + 0.5F, y + height + 0.5F, color);
		}
		if (border.top()) {
			addQuad(bufferBuilder, matrix, x - 0.5F, y - 0.5F, x + width + 0.5F, y + 0.5F, color);
		}
		if (border.bottom()) {
			addQuad(bufferBuilder, matrix, x - 0.5F, y + height - 0.5F, x + width + 0.5F, y + height + 0.5F, color);
		}
	}

	private static void addQuad(
		BufferBuilder bufferBuilder,
		Matrix4f matrix,
		float x1,
		float y1,
		float x2,
		float y2,
		int color
	) {
		bufferBuilder.addVertex(matrix, x1, y1, 0).setColor(color);
		bufferBuilder.addVertex(matrix, x1, y2, 0).setColor(color);
		bufferBuilder.addVertex(matrix, x2, y2, 0).setColor(color);
		bufferBuilder.addVertex(matrix, x2, y1, 0).setColor(color);
	}

	private void renderSlotTextOverlays(GuiGraphics guiGraphics) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int hoveredSlotIndex = hoveredSlot.map(slots::indexOf).orElse(-1);
		for (int i = 0; i < slots.size(); i++) {
			IngredientListSlot slot = slots.get(i);
			getSlotVisuals(slot, i, hoveredSlotIndex).ifPresent(visuals -> {
				ImmutableRect2i area = slot.getArea();
				visuals.multiplierText()
					.ifPresent(text -> drawTopLeftText(guiGraphics, font, area, text, visuals.multiplierTextColor().orElse(0xFFFFFFFF)));
				visuals.recipeMarkerText()
					.ifPresent(text -> {
						if (text.equals("C")) {
							drawBottomLeftText(guiGraphics, font, area, text, visuals.recipeMarkerTextColor().orElse(0xFFFFFF55));
						} else {
							drawTopRightText(guiGraphics, font, area, text, visuals.recipeMarkerTextColor().orElse(0xFFFFFF55));
						}
					});
				visuals.amountText()
					.ifPresent(text -> drawBottomRightText(guiGraphics, font, area, text, visuals.amountTextColor().orElse(0xFFFFFFFF)));
			});
		}
	}

	private Optional<BookmarkSlotVisuals> getSlotVisuals(IngredientListSlot slot, int slotIndex, int hoveredSlotIndex) {
		Optional<IElement<?>> element = slot.getOptionalElement()
			.filter(IElement::isVisible);
		if (element.isEmpty()) {
			return Optional.empty();
		}
		Optional<IElement<?>> hoveredElement = hoveredSlot
			.flatMap(IngredientListSlot::getOptionalElement);
		IngredientListSlotContext context = new IngredientListSlotContext(
			element.get(),
			hoveredElement,
			slotIndex,
			hoveredSlotIndex,
			getRowIndex(slot),
			hoveredSlot.map(this::getRowIndex).orElse(-1)
		);
		return slotVisualsResolver.apply(context);
	}

	private int getRowIndex(IngredientListSlot slot) {
		return slot.getArea().getY();
	}

	private static void drawTopLeftText(GuiGraphics guiGraphics, Font font, ImmutableRect2i area, String text, int color) {
		drawScaledText(guiGraphics, font, text, area.getX() + 1, area.getY() + 1, color);
	}

	private static void drawTopRightText(GuiGraphics guiGraphics, Font font, ImmutableRect2i area, String text, int color) {
		int x = area.getX() + area.getWidth() - scaledTextWidth(font, text);
		drawScaledText(guiGraphics, font, text, x, area.getY() + 1, color);
	}

	private static void drawBottomLeftText(GuiGraphics guiGraphics, Font font, ImmutableRect2i area, String text, int color) {
		int y = area.getY() + area.getHeight() - Math.round(font.lineHeight * BOOKMARK_SLOT_TEXT_SCALE);
		drawScaledText(guiGraphics, font, text, area.getX() + 1, y, color);
	}

	private static void drawBottomRightText(GuiGraphics guiGraphics, Font font, ImmutableRect2i area, String text, int color) {
		int x = area.getX() + area.getWidth() - scaledTextWidth(font, text);
		int y = area.getY() + area.getHeight() - Math.round(font.lineHeight * BOOKMARK_SLOT_TEXT_SCALE);
		drawScaledText(guiGraphics, font, text, x, y, color);
	}

	private static int scaledTextWidth(Font font, String text) {
		return Math.round(font.width(text) * BOOKMARK_SLOT_TEXT_SCALE);
	}

	private static void drawScaledText(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, GuiRenderLayers.OVERLAY_DECORATION_Z);
		guiGraphics.pose().scale(BOOKMARK_SLOT_TEXT_SCALE, BOOKMARK_SLOT_TEXT_SCALE, 1);
		guiGraphics.drawString(font, text, 0, 0, color, true);
		guiGraphics.pose().popPose();
	}

	private <T> void renderBatch(GuiGraphics guiGraphics, Map.Entry<IIngredientType<?>, List<BatchRenderElement<?>>> entry) {
		@SuppressWarnings("unchecked")
		IIngredientType<T> type = (IIngredientType<T>) entry.getKey();
		IIngredientRenderer<T> ingredientRenderer = ingredientManager.getIngredientRenderer(type);
		@SuppressWarnings("unchecked")
		List<BatchRenderElement<T>> elements = (List<BatchRenderElement<T>>) (Object) entry.getValue();
		SafeIngredientUtil.renderBatch(guiGraphics, type, ingredientRenderer, elements);
	}

	private void renderEditMode(GuiGraphics guiGraphics) {
		IEditModeConfig editModeConfig = Internal.getJeiRuntime().getEditModeConfig();

		for (IngredientListSlot slot : slots) {
			slot.getOptionalElement()
				.filter(IElement::isVisible)
				.ifPresent(element -> {
					renderEditMode(guiGraphics, slot.getArea(), slot.getPadding(), element.getTypedIngredient(), editModeConfig);
				});
		}

		RenderSystem.enableBlend();
	}

	private static <T> void renderEditMode(GuiGraphics guiGraphics, ImmutableRect2i area, int padding, ITypedIngredient<T> typedIngredient, IEditModeConfig config) {
		Set<IEditModeConfig.HideMode> hideModes = config.getIngredientHiddenUsingConfigFile(typedIngredient);
		if (!hideModes.isEmpty()) {
			boolean wildcard = hideModes.contains(IEditModeConfig.HideMode.WILDCARD);
			boolean single = hideModes.contains(IEditModeConfig.HideMode.SINGLE);
			if (wildcard && single) {
				guiGraphics.fill(
					RenderType.guiOverlay(),
					area.getX() + padding,
					area.getY() + padding,
					area.getX() + 16 + padding,
					area.getY() + 8 + padding,
					WILDCARD_BLACKLIST_COLOR
				);
				guiGraphics.fill(
					RenderType.guiOverlay(),
					area.getX() + padding,
					area.getY() + 8 + padding,
					area.getX() + 16 + padding,
					area.getY() + 16 + padding,
					BLACKLIST_COLOR
				);
			} else if (wildcard) {
				guiGraphics.fill(
					RenderType.guiOverlay(),
					area.getX() + padding,
					area.getY() + padding,
					area.getX() + 16 + padding,
					area.getY() + 16 + padding,
					WILDCARD_BLACKLIST_COLOR
				);
			} else if (single) {
				guiGraphics.fill(
					RenderType.guiOverlay(),
					area.getX() + padding,
					area.getY() + padding,
					area.getX() + 16 + padding,
					area.getY() + 16 + padding,
					BLACKLIST_COLOR
				);
			}
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
	}
}
