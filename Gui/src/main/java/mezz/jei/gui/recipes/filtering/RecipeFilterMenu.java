package mezz.jei.gui.recipes.filtering;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public final class RecipeFilterMenu implements mezz.jei.gui.input.IGuiInputLayer {
	private static final List<RecipeFilterMode> MODES = List.of(RecipeFilterMode.DEFAULT, RecipeFilterMode.PREFERRED, RecipeFilterMode.NOT_PREFERRED, RecipeFilterMode.DISABLED, RecipeFilterMode.ALL);
	private static final int WIDTH = 126, ROW_HEIGHT = 20;
	private final RecipeFilterSettings settings;
	private final Runnable apply;
	private final BiFunction<Double, Double, Optional<IRecipeCategory<?>>> categoryAt;
	private ImmutableRect2i main = ImmutableRect2i.EMPTY;
	private ImmutableRect2i sub = ImmutableRect2i.EMPTY;
	private int section = -1;
	private @Nullable ResourceLocation category;
	private boolean open;

	public RecipeFilterMenu(RecipeFilterSettings settings, Runnable apply, BiFunction<Double, Double, Optional<IRecipeCategory<?>>> categoryAt) {
		this.settings = settings;
		this.apply = apply;
		this.categoryAt = categoryAt;
	}

	public void open(ImmutableRect2i anchor) {
		category = null;
		show(anchor.x(), anchor.y() + anchor.height());
	}

	private void show(int x, int y) {
		var window = Minecraft.getInstance().getWindow();
		main = new ImmutableRect2i(Math.max(0, Math.min(x, window.getGuiScaledWidth() - WIDTH)), Math.max(0, Math.min(y, window.getGuiScaledHeight() - 2 * ROW_HEIGHT)), WIDTH, 2 * ROW_HEIGHT);
		section = -1;
		sub = ImmutableRect2i.EMPTY;
		open = true;
	}

	public boolean isOpen() { return open; }

	@Override
	public boolean isMouseOver(double x, double y) { return open; }

	@Override
	public void unfocus() { open = false; }

	private void expand(int section) {
		this.section = section;
		var window = Minecraft.getInstance().getWindow();
		int x = main.x() + WIDTH + 2;
		if (x + WIDTH > window.getGuiScaledWidth())
			x = Math.max(0, main.x() - WIDTH - 2);
		sub = new ImmutableRect2i(x, Math.max(0, Math.min(main.y(), window.getGuiScaledHeight() - 5 * ROW_HEIGHT)), WIDTH, 5 * ROW_HEIGHT);
	}

	public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!open)
			return;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 600);
		if (category != null) {
			var state = RecipeCategoryPreferences.get();
			drawRows(graphics, main, List.of(
				Component.translatable("gui.jei.recipe_filter.category.disabled"), Component.translatable("gui.jei.recipe_filter.category.preferred")),
				new boolean[]{state.disabled().contains(category), state.preferred().contains(category)}, mouseX, mouseY);
		} else {
			drawRows(graphics, main, List.of(Component.translatable("gui.jei.recipe_filter.visible_range"), Component.translatable("gui.jei.recipe_filter.search_scope")), new boolean[2], mouseX, mouseY);
			graphics.drawString(Minecraft.getInstance().font, ">", main.x() + main.width() - 10, main.y() + 6, 0xFFFFFFFF);
			graphics.drawString(Minecraft.getInstance().font, ">", main.x() + main.width() - 10, main.y() + ROW_HEIGHT + 6, 0xFFFFFFFF);
			if (section >= 0) {
				List<Component> labels = section == 0 ? MODES.stream().map(mode -> Component.translatable("gui.jei.recipe_filter.mode." + mode.name().toLowerCase(java.util.Locale.ROOT))).map(Component.class::cast).toList() : java.util.Arrays.stream(RecipeSearchScope.values()).map(scope -> Component.translatable("gui.jei.recipe_filter.scope." + scope.name().toLowerCase(java.util.Locale.ROOT))).map(Component.class::cast).toList();
				boolean[] selected = new boolean[5];
				selected[section == 0 ? MODES.indexOf(settings.getMode()) : settings.getScope().ordinal()] = true;
				drawRows(graphics, sub, labels, selected, mouseX, mouseY);
			}
		}
		graphics.pose().popPose();
	}

	private static void drawRows(GuiGraphics graphics, ImmutableRect2i area, List<Component> labels, boolean[] selected, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		graphics.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), 0xFF242424);
		for (int i = 0; i < labels.size(); i++) {
			int y = area.y() + i * ROW_HEIGHT;
			if (area.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_HEIGHT)
				graphics.fill(area.x(), y, area.x() + area.width(), y + ROW_HEIGHT, 0xFF505050);
			if (selected[i])
				graphics.drawString(font, "\u2713", area.x() + 4, y + 6, 0xFF99DD99);
			graphics.drawString(font, font.plainSubstrByWidth(labels.get(i).getString(), area.width() - 20), area.x() + 15, y + 6, 0xFFFFFFFF);
		}
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
		double x = input.getMouseX(), y = input.getMouseY();
		if (input.getKey().getType() != InputConstants.Type.MOUSE) {
			if (!open)
				return Optional.empty();
			if (!input.isSimulate() && input.getKey().getValue() == GLFW.GLFW_KEY_ESCAPE)
				open = false;
			return Optional.of(this);
		}
		if (!open) {
			if (input.getKey().getValue() != InputConstants.MOUSE_BUTTON_RIGHT)
				return Optional.empty();
			var hovered = categoryAt.apply(x, y);
			if (hovered.isEmpty())
				return Optional.empty();
			if (!input.isSimulate()) {
				category = hovered.get().getRecipeType().getUid();
				show((int) x, (int) y);
			}
			return Optional.of(this);
		}
		if (input.isSimulate())
			return Optional.of(this);
		if (main.contains(x, y)) {
			int row = (int) (y - main.y()) / ROW_HEIGHT;
			if (category != null) {
				RecipeCategoryPreferences.toggle(category, row == 0);
				open = false;
				apply.run();
			} else
				expand(row);
		} else if (section >= 0 && sub.contains(x, y)) {
			int row = (int) (y - sub.y()) / ROW_HEIGHT;
			if (section == 0)
				settings.setMode(MODES.get(row));
			else
				settings.setScope(RecipeSearchScope.values()[row]);
			open = false;
			apply.run();
		} else
			open = false;
		return Optional.of(this);
	}

	@Override
	public Optional<IUserInputHandler> handleMouseScrolled(double x, double y, double dx, double dy) {
		return open ? Optional.of(this) : Optional.empty();
	}
}
