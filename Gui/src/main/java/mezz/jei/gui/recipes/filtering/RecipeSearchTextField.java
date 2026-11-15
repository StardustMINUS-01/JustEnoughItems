package mezz.jei.gui.recipes.filtering;

import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.input.focus.GuiKeyboardFocus;
import mezz.jei.gui.search.ISearchCompletionProvider;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Collection;
import java.util.List;

public final class RecipeSearchTextField extends GuiTextFieldFilter {
	private static final int PLACEHOLDER_COLOR = 0xFF666666;
	private static final ISearchCompletionProvider NO_COMPLETIONS = new ISearchCompletionProvider() {
		@Override
		public long getCompletionRevision() { return 0; }
		@Override
		public Collection<PrefixInfo<IListElementInfo<?>, IListElement<?>>> getAllPrefixInfos() { return List.of(); }
		@Override
		public Collection<IListElementInfo<?>> getAllElementInfos() { return List.of(); }
	};
	private final Component placeholder;

	public RecipeSearchTextField(IClientConfig clientConfig, GuiKeyboardFocus keyboardFocus) {
		this(clientConfig, keyboardFocus, Component.translatable("gui.jei.recipe_filter.search"));
	}

	private RecipeSearchTextField(IClientConfig clientConfig, GuiKeyboardFocus keyboardFocus, Component placeholder) {
		super(() -> false, clientConfig, NO_COMPLETIONS, keyboardFocus, placeholder);
		this.placeholder = placeholder;
		setTextColor(0xFFFFFFFF);
	}

	@Override
	public void updateCompletion(int mouseX, int mouseY) {
		// Ingredient-list completions do not understand the recipe query's role prefixes.
		hideCompletion();
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
