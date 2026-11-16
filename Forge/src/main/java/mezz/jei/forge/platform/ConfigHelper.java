package mezz.jei.forge.platform;

import mezz.jei.common.Internal;
import mezz.jei.common.platform.IPlatformConfigHelper;
import mezz.jei.forge.input.ConfigKeyBinding;
import mezz.jei.gui.config.screen.JeiConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

public class ConfigHelper implements IPlatformConfigHelper {

	@Override
	public Path getModConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Optional<Screen> getConfigScreen(String modId, @Nullable Screen parent) {
		if ("jei".equals(modId) && Internal.getJeiRuntime() != null && parent != null) {
			Minecraft minecraft = Minecraft.getInstance();
			return Optional.of(new JeiConfigScreen(Internal.getJeiRuntime().getConfigManager(), ConfigKeyBinding.create(),
				screen -> net.minecraftforge.client.ForgeHooksClient.pushGuiLayer(minecraft, screen),
				() -> net.minecraftforge.client.ForgeHooksClient.popGuiLayer(minecraft)));
		}
		return ModList.get()
			.getModContainerById(modId)
			.flatMap(modContainer -> modContainer.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class))
			.map(ConfigScreenHandler.ConfigScreenFactory::screenFunction)
			.map(screenFactory -> screenFactory.apply(Minecraft.getInstance(), parent));
	}
}
