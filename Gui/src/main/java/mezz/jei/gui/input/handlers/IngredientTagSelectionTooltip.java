package mezz.jei.gui.input.handlers;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.gui.GuiRenderLayers;
import mezz.jei.common.gui.JeiGuiColors;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.input.ICharTypedHandler;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IGuiInputLayer;
import mezz.jei.gui.input.IPinnedTooltipHolder;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.MouseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Vector2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public final class IngredientTagSelectionTooltip implements IGuiInputLayer, IPinnedTooltipHolder, ICharTypedHandler, IDragHandler, ClientTooltipComponent, TooltipComponent {
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private final JeiTooltip tooltip = new JeiTooltip();
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private final ClientTooltipPositioner positioner = (screenWidth, screenHeight, x, y, width, height) -> {
		area = new ImmutableRect2i(area.x(), area.y(), width + 2 * PADDING, height + 2 * PADDING);
		return new Vector2i(area.x() + PADDING, area.y() + PADDING);
	};
	private @Nullable Screen sourceScreen;
	private List<String> tags = List.of();
	private record TagLine(int tagIndex, FormattedCharSequence text) {}

	private List<TagLine> rows = List.of();
	private List<FormattedCharSequence> title = List.of();
	private int headerHeight;
	private int visibleRows;
	private int firstRow;

	public IngredientTagSelectionTooltip() {
		tooltip.add(this);
	}

	public <T> void show(ITypedIngredient<T> ingredient, IIngredientManager ingredientManager, double mouseX, double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = minecraft.screen;
		if (screen == null) {
			return;
		}
		var font = minecraft.font;
		tags = IngredientClipboardText.getIngredientTags(ingredient, ingredientManager);
		Component name = Component.literal(IngredientClipboardText.getIngredientName(ingredient, ingredientManager));
		Component empty = Component.translatable("jei.tooltip.tags.empty");
		int contentWidth = Math.max(font.width(name), tags.stream().mapToInt(font::width).max().orElseGet(() -> font.width(empty)));
		int width = Math.min(Math.min(screen.width - 2 * PADDING, 360), contentWidth + 2 * PADDING + 3);
		int textWidth = Math.max(1, width - 2 * PADDING - 3);
		title = font.split(name, textWidth);
		rows = tags.isEmpty()
			? List.of(new TagLine(-1, empty.getVisualOrderText()))
			: IntStream.range(0, tags.size()).boxed()
				.flatMap(index -> font.split(Component.literal(tags.get(index)), textWidth).stream().map(line -> new TagLine(index, line)))
				.toList();
		headerHeight = title.size() * LINE_HEIGHT + 2;
		visibleRows = Math.min(rows.size(), Math.max(1, (screen.height - 4 * PADDING - headerHeight) / LINE_HEIGHT));
		int height = headerHeight + visibleRows * LINE_HEIGHT + 2 * PADDING;
		area = new ImmutableRect2i(
			Mth.clamp((int) mouseX + 8, PADDING, Math.max(PADDING, screen.width - width - PADDING)),
			Mth.clamp((int) mouseY + 8, PADDING, Math.max(PADDING, screen.height - height - PADDING)), width, height);
		firstRow = 0;
		sourceScreen = screen;
		PinnedTooltipManager.opened(this);
	}

	@Override
	public void update(double mouseX, double mouseY) {
		if (sourceScreen != Minecraft.getInstance().screen) {
			hide();
		}
	}

	@Override
	public void hide() {
		sourceScreen = null;
		tags = List.of();
		rows = List.of();
		title = List.of();
		area = ImmutableRect2i.EMPTY;
		PinnedTooltipManager.closed(this);
	}

	@Override
	public void unfocus() {
		hide();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return sourceScreen != null;
	}

	private int getTagIndex(double mouseX, double mouseY) {
		int top = area.y() + PADDING + headerHeight;
		if (mouseX < area.x() + PADDING || mouseX >= area.x() + area.width() - PADDING || mouseY < top || mouseY >= top + visibleRows * LINE_HEIGHT) {
			return -1;
		}
		return rows.get(firstRow + (int) (mouseY - top) / LINE_HEIGHT).tagIndex();
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (sourceScreen == null) {
			return Optional.empty();
		}
		if (input.getKey().getType() == InputConstants.Type.MOUSE) {
			int row = getTagIndex(input.getMouseX(), input.getMouseY());
			if (input.isSimulate()) {
				return Optional.of((releaseScreen, release, keys) -> {
					if (sourceScreen == screen && row == getTagIndex(release.getMouseX(), release.getMouseY())) {
						handleUserInput(releaseScreen, release, keys);
					}
					return Optional.of(this);
				});
			}
			if (row >= 0 && row < tags.size() && input.getKey().getValue() == InputConstants.MOUSE_BUTTON_LEFT) {
				Minecraft.getInstance().keyboardHandler.setClipboard(tags.get(row));
				JeiClientSoundUtil.playClickSound();
				hide();
			} else if (!area.contains(input.getMouseX(), input.getMouseY())) {
				hide();
			}
		} else if (!input.isSimulate() && input.getKey().getValue() == GLFW.GLFW_KEY_ESCAPE) {
			hide();
		}
		return Optional.of(this);
	}

	@Override
	public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
		if (sourceScreen == null) {
			return Optional.empty();
		}
		if (area.contains(mouseX, mouseY)) {
			firstRow = Mth.clamp(firstRow - (int) Math.signum(scrollDeltaY), 0, rows.size() - visibleRows);
		}
		return Optional.of(this);
	}

	@Override
	public boolean hasKeyboardFocus() {
		return sourceScreen != null;
	}

	@Override
	public boolean onCharTyped(char codePoint, int modifiers) {
		return hasKeyboardFocus();
	}

	@Override
	public Optional<IDragHandler> handleDragStart(Screen screen, UserInput input) {
		return sourceScreen == null ? Optional.empty() : Optional.of(this);
	}

	@Override
	public boolean handleDragComplete(Screen screen, UserInput input) {
		return true;
	}

	@Override
	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (sourceScreen == null) {
			return;
		}
		PinnedTooltipManager.draw(this, () -> {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, GuiRenderLayers.TOOLTIP_Z);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), JeiGuiColors.getColor(JeiGuiColors.GuiColor.INTERACTIVE_INGREDIENT_TOOLTIP_SCREEN_DIM));
			tooltip.draw(graphics, area.x() + PADDING, area.y() + PADDING, positioner);
			graphics.pose().popPose();
		});
	}

	@Override
	public int getWidth(Font font) {
		return area.width() - 2 * PADDING;
	}

	@Override
	public int getHeight() {
		return headerHeight + visibleRows * LINE_HEIGHT;
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
		int left = area.x();
		int top = area.y();
		int right = left + area.width();
		int bottom = top + area.height();
		graphics.fill(left + 1, top, right - 1, top + 1, 0xFFFFFFFF);
		graphics.fill(left + 1, bottom - 1, right - 1, bottom, 0xFFFFFFFF);
		graphics.fill(left, top + 1, left + 1, bottom - 1, 0xFFFFFFFF);
		graphics.fill(right - 1, top + 1, right, bottom - 1, 0xFFFFFFFF);
		for (var line : title) {
			graphics.drawString(font, line, x, y, 0xFFFFFFFF);
			y += LINE_HEIGHT;
		}
		y += 2;
		int hovered = getTagIndex(MouseUtil.getX(), MouseUtil.getY());
		for (int row = firstRow; row < firstRow + visibleRows; row++) {
			TagLine line = rows.get(row);
			if (line.tagIndex() == hovered && hovered >= 0) {
				graphics.fill(x - 1, y - 1, x + getWidth(font) - 2, y + LINE_HEIGHT - 1, 0x33FFFFFF);
			}
			graphics.drawString(font, line.text(), x, y, tags.isEmpty() ? 0xFFAAAAAA : 0xFFFFFFFF);
			y += LINE_HEIGHT;
		}
		if (rows.size() > visibleRows) {
			int trackHeight = visibleRows * LINE_HEIGHT;
			int thumbHeight = Math.max(4, trackHeight * visibleRows / rows.size());
			int thumbTop = area.y() + PADDING + headerHeight + (trackHeight - thumbHeight) * firstRow / (rows.size() - visibleRows);
			graphics.fill(right - PADDING - 1, thumbTop, right - PADDING, thumbTop + thumbHeight, 0xFFAAAAAA);
		}
	}
}
