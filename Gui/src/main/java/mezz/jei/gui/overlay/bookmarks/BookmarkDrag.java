package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.common.util.SafeIngredientUtil;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;

import java.util.List;

public class BookmarkDrag<T> {
	private static final int PREVIEW_ACCEPT_COLOR = 0x553399FF;
	private static final int PREVIEW_REJECT_COLOR = 0x55FF3333;

	private final BookmarkOverlay bookmarkOverlay;
	private final List<IBookmarkDragTarget> targets;
	private final BookmarkDragSelection selection;
	private final double mouseStartX;
	private final double mouseStartY;
	private final IBookmark bookmark;
	private final ImmutableRect2i origin;
	private final long dragCanStartTime;

	public BookmarkDrag(
		BookmarkOverlay bookmarkOverlay,
		List<IBookmarkDragTarget> targets,
		IBookmark bookmark,
		BookmarkDragSelection selection,
		double mouseX,
		double mouseY,
		ImmutableRect2i origin
	) {
		this.bookmarkOverlay = bookmarkOverlay;
		this.targets = targets;
		this.bookmark = bookmark;
		this.selection = selection;
		this.origin = origin;
		this.mouseStartX = mouseX;
		this.mouseStartY = mouseY;
		IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
		this.dragCanStartTime = System.currentTimeMillis() + clientConfig.getDragDelayMs();
	}

	public static boolean canStart(BookmarkDrag<?> drag, double mouseX, double mouseY) {
		if (System.currentTimeMillis() < drag.dragCanStartTime) {
			return false;
		}
		ImmutableRect2i origin = drag.origin;
		final Vec2 center;
		if (origin.isEmpty()) {
			center = new Vec2((float) drag.mouseStartX, (float) drag.mouseStartY);
		} else {
			if (origin.contains(mouseX, mouseY)) {
				return false;
			}
			center = new Vec2(
				origin.getX() + (origin.getWidth() / 2.0f),
				origin.getY() + (origin.getHeight() / 2.0f)
			);
		}

		double mouseXDist = center.x - mouseX;
		double mouseYDist = center.y - mouseY;
		double mouseDistSq = mouseXDist * mouseXDist + mouseYDist * mouseYDist;
		return mouseDistSq > 64.0;
	}

	public void update(int mouseX, int mouseY) {
		if (bookmark.isVisible() && !canStart(this, mouseX, mouseY)) {
			return;
		}

		for (IBookmark selectedBookmark : selection.bookmarks()) {
			selectedBookmark.setVisible(false);
		}
		bookmarkOverlay.getScreenPropertiesUpdater()
			.updateMouseExclusionArea(new ImmutablePoint2i(mouseX, mouseY))
			.update();
	}

	public boolean drawItem(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (bookmark.isVisible()) {
			return false;
		}

		drawPreview(guiGraphics, mouseX, mouseY);
		drawDraggedSelection(guiGraphics, mouseX, mouseY);
		return true;
	}

	private void drawDraggedSelection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		for (BookmarkDragSelection.PreviewSlot slot : selection.previewSlots()) {
			ITypedIngredient<?> typedIngredient = slot.bookmark().getElement().getTypedIngredient();
			drawIngredient(guiGraphics, ingredientManager, typedIngredient, mouseX - 8 + slot.relativeX(), mouseY - 8 + slot.relativeY());
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <V> void drawIngredient(
		GuiGraphics guiGraphics,
		IIngredientManager ingredientManager,
		ITypedIngredient<V> typedIngredient,
		int x,
		int y
	) {
		IIngredientRenderer<V> renderer = ingredientManager.getIngredientRenderer(typedIngredient.getType());
		SafeIngredientUtil.render(guiGraphics, renderer, typedIngredient, x, y);
	}

	private void drawPreview(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		for (IBookmarkDragTarget target : targets) {
			ImmutableRect2i area = target.getArea();
			if (MathUtil.contains(area, mouseX, mouseY)) {
				target.getPreview(bookmark, mouseX, mouseY)
					.ifPresent(preview -> {
						ImmutableRect2i previewArea = preview.area();
						int color = preview.rejected() ? PREVIEW_REJECT_COLOR : PREVIEW_ACCEPT_COLOR;
						guiGraphics.fill(
							previewArea.getX(),
							previewArea.getY(),
							previewArea.getX() + previewArea.getWidth(),
							previewArea.getY() + previewArea.getHeight(),
							color
						);
					});
				return;
			}
		}
	}

	public boolean onClick(UserInput input) {
		if (bookmark.isVisible()) {
			return false;
		}

		for (IBookmarkDragTarget target : targets) {
			ImmutableRect2i area = target.getArea();
			if (MathUtil.contains(area, input.getMouseX(), input.getMouseY())) {
				if (!input.isSimulate()) {
					target.accept(bookmark, input.getMouseX(), input.getMouseY());
					stop();
					return true;
				}
			}
		}
		if (!input.isSimulate()) {
			stop();
		}
		return false;
	}

	public void stop() {
		for (IBookmark selectedBookmark : selection.bookmarks()) {
			selectedBookmark.setVisible(true);
		}
		bookmarkOverlay.getScreenPropertiesUpdater()
			.updateMouseExclusionArea(null)
			.update();
	}
}
