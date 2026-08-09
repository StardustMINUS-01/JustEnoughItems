package mezz.jei.gui.plugins;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.ModIds;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiFeatures;
import mezz.jei.gui.GuiProperties;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@JeiPlugin
public class JeiGuiPlugin implements IModPlugin {
	private static final List<Consumer<IGuiHandlerRegistration>> guiHandlerHooks = new ArrayList<>();
	private @Nullable IJeiFeatures jeiFeatures;

	public static void addGuiHandlerHook(Consumer<IGuiHandlerRegistration> hook) {
		guiHandlerHooks.add(hook);
	}

	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "gui");
	}

	@Override
	public void configureJei(IJeiFeatures jeiFeatures) {
		this.jeiFeatures = jeiFeatures;
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		if (!isJeiGuiEnabled()) {
			return;
		}

		IIngredientManager ingredientManager = registration.getJeiHelpers().getIngredientManager();
		registration.addGuiScreenHandler(AbstractContainerScreen.class, GuiProperties::create);
		registration.addGuiScreenHandler(ChatScreen.class, new ChatScreenHandler(ingredientManager));
		registration.addGuiScreenHandler(RecipesGui.class, RecipesGui::getProperties);
		for (Consumer<IGuiHandlerRegistration> hook : guiHandlerHooks) {
			hook.accept(registration);
		}
	}

	private boolean isJeiGuiEnabled() {
		IJeiFeatures jeiFeatures = this.jeiFeatures;
		return jeiFeatures == null || jeiFeatures.isJeiGuiEnabled();
	}
}
