package mezz.jei.forge.platform;

import mezz.jei.common.Internal;
import mezz.jei.common.platform.IPlatformConfigHelper;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.forge.input.ConfigKeyBinding;
import mezz.jei.gui.config.screen.JeiConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

public class ConfigHelper implements IPlatformConfigHelper {

	@Override
	public Path getModConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Optional<Screen> getConfigScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		ErrorUtil.checkNotNull(minecraft.screen, "minecraft.screen");
		return Optional.of(new JeiConfigScreen(minecraft.screen, Internal.getJeiRuntime().getConfigManager(), ConfigKeyBinding.create()));
	}
}
