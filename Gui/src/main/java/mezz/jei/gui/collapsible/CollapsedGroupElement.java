package mezz.jei.gui.collapsible;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

/**
 * View adapter for an element that belongs to a collapsible group.
 * Alt + left click toggles the group; all other interactions delegate
 * to the wrapped element.
 */
public class CollapsedGroupElement<T> implements IElement<T> {
	private final IElement<T> delegate;
	private final CollapsibleGroup group;
	private final CollapsibleManager manager;
	private final boolean collapsed;
	private final boolean allowToggle;
	private final int groupSize;
	private final List<IElement<?>> hiddenMembers;

	public CollapsedGroupElement(
		IElement<T> delegate,
		CollapsibleGroup group,
		CollapsibleManager manager,
		boolean collapsed,
		boolean allowToggle,
		int groupSize,
		List<IElement<?>> hiddenMembers
	) {
		this.delegate = delegate;
		this.group = group;
		this.manager = manager;
		this.collapsed = collapsed;
		this.allowToggle = allowToggle;
		this.groupSize = groupSize;
		this.hiddenMembers = hiddenMembers;
	}

	public CollapsibleGroup group() {
		return group;
	}

	public boolean isCollapsed() {
		return collapsed;
	}

	public int getGroupSize() {
		return groupSize;
	}

	@Override
	public ITypedIngredient<T> getTypedIngredient() {
		return delegate.getTypedIngredient();
	}

	@Override
	public Optional<IBookmark> getBookmark() {
		return delegate.getBookmark();
	}

	@Override
	public @Nullable IDrawable createRenderOverlay() {
		return null;
	}

	/**
	 * Drawable that replaces the batch render for a collapsed group slot,
	 * drawing the background stack at a lower z and the representative at a
	 * higher z, following the GTNH NEI double-stack layering.
	 */
	public @Nullable IDrawable createDoubleStackDrawable() {
		if (!collapsed || hiddenMembers.isEmpty()) {
			return null;
		}
		ITypedIngredient<?> representative = delegate.getTypedIngredient();
		ITypedIngredient<?> second = hiddenMembers.getLast().getTypedIngredient();
		if (representative.getItemStack().isEmpty() || second.getItemStack().isEmpty()) {
			return null;
		}
		return new CollapsedItemOverlay(representative, second);
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		delegate.show(recipesGui, focusUtil, roles);
	}

	@Override
	public void getTooltip(
		JeiTooltip tooltip,
		IngredientGridTooltipHelper tooltipHelper,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientHelper<T> ingredientHelper
	) {
		delegate.getTooltip(tooltip, tooltipHelper, ingredientRenderer, ingredientHelper);
		if (groupSize > 1) {
			tooltip.add(Component.translatable(
				"jei.collapsible.group.tooltip", groupSize).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable(
				"jei.collapsible.toggle.hint").withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public void tick() {
		delegate.tick();
	}

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		if (!allowToggle || !InputModifiers.hasAlt(input)) {
			return false;
		}
		InputConstants.Key key = input.getKey();
		if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (!input.isSimulate()) {
			manager.toggleGroup(group.id());
			JeiClientSoundUtil.playClickSound();
		}
		return true;
	}
}
