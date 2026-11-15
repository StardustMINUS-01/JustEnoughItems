package mezz.jei.gui.chat;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.chat.JeiChatItemLinkHover;
import mezz.jei.common.chat.JeiChatRecipeLinks;
import mezz.jei.common.chat.JeiChatRecipeLinks.RecipeLink;
import mezz.jei.gui.chat.ChatIngredientTooltip.IngredientTooltipData;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.IPinnedTooltipHolder;
import mezz.jei.gui.input.PinnedTooltipManager;
import mezz.jei.gui.input.handlers.IngredientShortcutInputHandler;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.IInternalKeyMappings;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.Rect2i;
import org.joml.Vector2i;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class ChatRecipeTooltip implements IPinnedTooltipHolder {
	public static final ChatRecipeTooltip INSTANCE = new ChatRecipeTooltip();
	private @Nullable Style style;
	private @Nullable IJeiRuntime runtime;
	private @Nullable IRecipeLayoutDrawable<?> layout;
	private @Nullable ITypedIngredient<?> ingredient;
	private @Nullable Screen source;
	private Rect2i area = new Rect2i(0, 0, 0, 0);
	private Rect2i icon = new Rect2i(0, 0, 0, 0);
	private boolean pinned;
	private int anchorX;
	private int anchorY;
	private @Nullable ClientTooltipPositioner ingredientPositioner;
	private @Nullable IngredientShortcutInputHandler shortcuts;

	public void setShortcuts(IngredientShortcutInputHandler shortcuts) {
		this.shortcuts = shortcuts;
	}

	public boolean hasTags() {
		return source instanceof ChatScreen && shortcuts != null && shortcuts.getTagSelectionTooltip().hasKeyboardFocus();
	}

	public boolean copy(ITypedIngredient<?> ingredient, UserInput input, IInternalKeyMappings keys) {
		return shortcuts != null && shortcuts.handlePreviewCopy(ingredient, input, keys);
	}

	public boolean scroll(double x, double y, double dx, double dy) {
		if (hasTags()) {
			return shortcuts.getTagSelectionTooltip().scroll(x, y, dx, dy);
		}
		return isPinned();
	}

	public boolean isPinned() {
		return pinned && Screen.hasShiftDown() && source == net.minecraft.client.Minecraft.getInstance().screen;
	}

	public boolean isVisible() {
		return layout != null || ingredient != null;
	}

	public boolean isUnpinnedIngredient() {
		return layout == null && ingredient != null && !isPinned();
	}

	public void update(Screen screen, double mouseX, double mouseY) {
		if (!(screen instanceof ChatScreen)) {
			if (source != null) {
				clear();
			}
			return;
		}
		IJeiRuntime currentRuntime = Internal.getOptionalJeiRuntime().orElse(null);
		if (source != screen || runtime != currentRuntime) {
			clear();
		}
		if (hasTags()) {
			return;
		}
		source = screen;
		IPinnedTooltipHolder active = PinnedTooltipManager.getActiveHolder();
		if (active != null && active != this) {
			hide();
			return;
		}
		if (isVisible() && Screen.hasShiftDown()) {
			pinned = true;
			PinnedTooltipManager.opened(this);
			return;
		}
		if (pinned) {
			pinned = false;
			PinnedTooltipManager.closed(this);
		}
		anchorX = (int) mouseX;
		anchorY = (int) mouseY;
		Style hovered = JeiChatItemLinkHover.getHoveredStyle(screen, mouseX, mouseY).orElse(null);
		if (Objects.equals(style, hovered) && runtime == currentRuntime) {
			return;
		}
		style = hovered;
		runtime = currentRuntime;
		layout = null;
		ingredient = null;
		ingredientPositioner = null;
		area = new Rect2i(0, 0, 0, 0);
		if (currentRuntime != null) {
			layout = JeiChatRecipeLinks.parse(hovered).flatMap(ChatRecipeTooltip::resolve).orElse(null);
			ingredient = ChatIngredientTooltip.getIngredientForHoveredText(hovered).orElse(null);
		}
		if (isVisible() && Screen.hasShiftDown()) {
			pinned = true;
			PinnedTooltipManager.opened(this);
		}
	}

	public Optional<IngredientTooltipData<?>> getIngredientTooltip() {
		if (ingredient == null || runtime == null) {
			return Optional.empty();
		}
		return Optional.of(ChatIngredientTooltip.createTooltipData(ingredient, runtime.getIngredientManager()));
	}

	public Optional<? extends IClickableIngredient<?>> getIngredient(IClickableIngredientFactory factory, double x, double y) {
		if (!isPinned() || hasTags()) {
			return Optional.empty();
		}
		if (layout != null) {
			return layout.getSlotUnderMouse(x, y).flatMap(slot -> slot.slot().getDisplayedIngredient()
				.flatMap(value -> {
					var rect = slot.slot().getAreaIncludingBackground();
					return factory.createBuilder(value).buildWithArea(rect.getX() + slot.offset().x(), rect.getY() + slot.offset().y(), rect.getWidth(), rect.getHeight());
				}));
		}
		if (ingredient != null && icon.contains((int) x, (int) y)) {
			return factory.createBuilder(ingredient).buildWithArea(icon);
		}
		return Optional.empty();
	}

	public static Optional<IRecipeLayoutDrawable<?>> resolve(RecipeLink recipe) {
		return Internal.getOptionalJeiRuntime().flatMap(runtime -> new FocusedRecipeLayoutResolver(runtime.getRecipeManager())
			.resolve(new FocusedRecipe(recipe.recipeType(), recipe.recipeId()), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()));
	}

	public boolean draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (hasTags()) {
			return false;
		}
		// Ordinary item links are drawn from RenderTooltipEvent.Pre on NeoForge.
		if (!isVisible() || isUnpinnedIngredient()) {
			return false;
		}
		var pose = graphics.pose();
		pose.pushPose();
		try {
			pose.translate(0, 0, 400);
			if (isPinned()) {
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x80000000);
			}
			if (layout != null) {
				var rect = layout.getRect();
				var border = layout.getRectWithBorder();
				Vector2i position = position(graphics.guiWidth(), graphics.guiHeight(), border.getWidth(), border.getHeight());
				layout.setPosition(position.x() + rect.getX() - border.getX(), position.y() + rect.getY() - border.getY());
				layout.drawRecipe(graphics, isPinned() ? mouseX : -1, isPinned() ? mouseY : -1);
			} else if (ingredient != null && runtime != null) {
				drawIngredient(graphics, ingredient);
			}
			if (isPinned()) {
				graphics.renderOutline(area.getX() - 1, area.getY() - 1, area.getWidth() + 2, area.getHeight() + 2, 0xFFFFFFFF);
				if (layout != null) {
					layout.getSlotUnderMouse(mouseX, mouseY).ifPresent(slot -> slot.slot().drawTooltip(graphics, mouseX, mouseY));
				}
			}
		} finally {
			pose.popPose();
		}
		return true;
	}

	private Vector2i position(int screenWidth, int screenHeight, int width, int height) {
		Vector2i pos = isPinned() && area.getWidth() > 0 ? new Vector2i(area.getX(), area.getY()) : new Vector2i(DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, anchorX, anchorY, width, height));
		pos.x = Math.max(4, Math.min(pos.x, screenWidth - width - 4));
		pos.y = Math.max(4, Math.min(pos.y, screenHeight - height - 4));
		area = new Rect2i(pos.x, pos.y, width, height);
		return pos;
	}

	private <T> void drawIngredient(GuiGraphics graphics, ITypedIngredient<T> value) {
		drawIngredient(graphics, ChatIngredientTooltip.createTooltipData(value, runtime.getIngredientManager()));
	}

	public <T> void drawIngredientTooltip(GuiGraphics graphics, IngredientTooltipData<T> data) {
		var pose = graphics.pose();
		pose.pushPose();
		try {
			pose.translate(0, 0, 400);
			drawIngredient(graphics, data);
		} finally {
			pose.popPose();
		}
	}

	private <T> void drawIngredient(GuiGraphics graphics, IngredientTooltipData<T> data) {
		if (ingredientPositioner == null) {
			// The interactive host identifies an attached tooltip by its positioner.
			ingredientPositioner = createIngredientPositioner(data.ingredientRenderer());
		}
		ITypedIngredient<?> sourceIngredient = ingredient;
		IJeiRuntime sourceRuntime = runtime;
		data.tooltip().setInteractionSource(() -> ingredient == sourceIngredient && runtime == sourceRuntime && isVisible());
		data.tooltip().draw(graphics, anchorX, anchorY, data.typedIngredient(), data.ingredientRenderer(), data.ingredientManager(), ingredientPositioner);
	}

	ClientTooltipPositioner createIngredientPositioner(IIngredientRenderer<?> renderer) {
		return (sw, sh, x, y, w, h) -> {
			Vector2i pos = position(sw, sh, w + 8, h + 8);
			icon = new Rect2i(pos.x + 5, pos.y + 5, renderer.getWidth(), renderer.getHeight());
			return new Vector2i(pos.x + 4, pos.y + 4);
		};
	}

	public void tick() {
		if (source == null) {
			return;
		}
		if (runtime != Internal.getOptionalJeiRuntime().orElse(null)) {
			clear();
		}
		if (layout != null && !isPinned()) {
			layout.tick();
		}
	}

	public void onRuntimeStopped() {
		clear();
		shortcuts = null;
	}

	public void clear() {
		if (shortcuts != null) {
			shortcuts.getTagSelectionTooltip().resetInput();
		}
		ChatIngredientTooltip.clearSharedIngredient();
		runtime = null;
		source = null;
		hide();
	}

	@Override
	public void hide() {
		// Replacing the preview with the tag picker must not reset the new owner.
		style = null;
		layout = null;
		ingredient = null;
		ingredientPositioner = null;
		pinned = false;
		PinnedTooltipManager.closed(this);
	}
}
