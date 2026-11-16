package mezz.jei.gui.recipes.filtering;

import mezz.jei.gui.input.GuiTextFieldFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class RecipeSearchTextField extends GuiTextFieldFilter {
	private static final int PLACEHOLDER_COLOR = 0xFF666666;
	private final Component placeholder;

	public RecipeSearchTextField() {
		this(Component.translatable("gui.jei.recipe_filter.search"));
	}

	private RecipeSearchTextField(Component placeholder) {
		super(() -> false, mezz.jei.common.Internal.getClientConfigs().getClientConfig(), new mezz.jei.gui.search.ISearchCompletionProvider() {
			@Override
			public long getCompletionRevision() {
				return 0;
			}

			@Override
			public java.util.Collection<mezz.jei.common.search.PrefixInfo<mezz.jei.gui.ingredients.IListElementInfo<?>, mezz.jei.gui.ingredients.IListElement<?>>> getAllPrefixInfos() {
				return java.util.List.of();
			}

			@Override
			public java.util.Collection<mezz.jei.gui.ingredients.IListElementInfo<?>> getAllElementInfos() {
				return java.util.List.of();
			}
		}, new mezz.jei.gui.input.focus.GuiKeyboardFocus());
		this.placeholder = placeholder;
		setTextColor(0xFFFFFFFF);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		drawBackground(guiGraphics);
		if (getValue().isEmpty() && !isFocused()) {
			guiGraphics.drawString(
				Minecraft.getInstance().font,
				placeholder,
				getX(),
				getY(),
				PLACEHOLDER_COLOR,
				true
			);
			return;
		}
		drawForeground(guiGraphics, mouseX, mouseY, partialTicks);
	}
}
