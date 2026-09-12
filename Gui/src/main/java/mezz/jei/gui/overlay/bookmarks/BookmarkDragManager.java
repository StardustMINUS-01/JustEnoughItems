package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.Optional;

public class BookmarkDragManager {
	private final BookmarkOverlay bookmarkOverlay;
	private @Nullable BookmarkDrag<?> bookmarkDrag;

	public BookmarkDragManager(BookmarkOverlay bookmarkOverlay) {
		this.bookmarkOverlay = bookmarkOverlay;
	}

	public void updateDrag(int mouseX, int mouseY) {
		if (bookmarkDrag != null) {
			bookmarkDrag.update(mouseX, mouseY);
		}
	}

	public boolean drawDraggedItem(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (bookmarkDrag != null) {
			return bookmarkDrag.drawItem(guiGraphics, mouseX, mouseY);
		}
		return false;
	}

	public void stopDrag() {
		if (this.bookmarkDrag != null) {
			this.bookmarkDrag.stop();
			this.bookmarkDrag = null;
		}
	}

	private <V> boolean handleClickIngredient(IDraggableIngredientInternal<V> clicked, UserInput input) {
		IElement<V> element = clicked.getElement();
		return element
			.getBookmark()
			.map(bookmark -> {
				List<IBookmarkDragTarget> targets = bookmarkOverlay.createBookmarkDragTargets();
				ImmutableRect2i clickedArea = clicked.getArea();
				BookmarkDragSelection selection = BookmarkDragSelection.create(
					bookmarkOverlay.getBookmarkList(),
					bookmark,
					bookmarkOverlay.getPanelSlots(),
					clickedArea
				);
				this.bookmarkDrag = new BookmarkDrag<>(
					bookmarkOverlay,
					targets,
					bookmark,
					selection,
					input.getMouseX(),
					input.getMouseY(),
					clickedArea
				);
				return true;
			})
			.orElse(false);
	}

	public IDragHandler createDragHandler() {
		return new DragHandler();
	}

	public static boolean shouldStartBookmarkRearrangeDrag(boolean shiftDown, boolean dragToRearrangeBookmarksEnabled) {
		return false;
	}

	private class DragHandler implements IDragHandler {
		@Override
		public Optional<IDragHandler> handleDragStart(Screen screen, UserInput input) {
			if (input.getKey().getType() != InputConstants.Type.MOUSE ||
				input.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT
			) {
				stopDrag();
				return Optional.empty();
			}
			if (Screen.hasShiftDown()) {
				stopDrag();
				return Optional.empty();
			}

			IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
			if (!shouldStartBookmarkRearrangeDrag(Screen.hasShiftDown(), clientConfig.dragToRearrangeBookmarksEnabled().getValue())) {
				stopDrag();
				return Optional.empty();
			}

			Minecraft minecraft = Minecraft.getInstance();
			LocalPlayer player = minecraft.player;
			if (player == null) {
				return Optional.empty();
			}

			return bookmarkOverlay.getDraggableIngredientUnderMouse(input.getMouseX(), input.getMouseY())
				.findFirst()
				.flatMap(clicked -> {
					ItemStack mouseItem = player.containerMenu.getCarried();
					if (mouseItem.isEmpty() &&
						handleClickIngredient(clicked, input)
					) {
						return Optional.of(this);
					}
					return Optional.empty();
				});
		}

		@Override
		public boolean handleDragComplete(Screen screen, UserInput input) {
			if (bookmarkDrag == null) {
				return false;
			}
			boolean success = bookmarkDrag.onClick(input);
			bookmarkDrag = null;
			return success;
		}

		@Override
		public void handleDragCanceled() {
			stopDrag();
		}
	}
}
