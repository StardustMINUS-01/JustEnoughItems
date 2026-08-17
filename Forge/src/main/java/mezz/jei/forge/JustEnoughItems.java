package mezz.jei.forge;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.Constants;
import mezz.jei.common.bookmarks.CraftingGridCraftExecutors;
import mezz.jei.common.bookmarks.CraftingGridFillExecutors;
import mezz.jei.forge.compat.ae2.Ae2CraftingGridCraftExecutor;
import mezz.jei.forge.compat.sophisticated.SophisticatedCraftingGridCraftExecutor;
import mezz.jei.forge.compat.sophisticated.SophisticatedCraftingGridFillExecutor;
import mezz.jei.forge.compat.tconstruct.TinkerCraftingGridCraftExecutor;
import mezz.jei.forge.compat.tconstruct.TinkerCraftingGridFillExecutor;
import mezz.jei.common.config.IServerConfig;
import mezz.jei.common.util.MinecraftLocaleSupplier;
import mezz.jei.common.util.Translator;
import mezz.jei.forge.config.ServerConfig;
import mezz.jei.forge.events.PermanentEventSubscriptions;
import mezz.jei.forge.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ModIds.JEI_ID)
public class JustEnoughItems {
	public JustEnoughItems() {
		Translator.setLocaleSupplier(new MinecraftLocaleSupplier());
		IEventBus eventBus = MinecraftForge.EVENT_BUS;
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		PermanentEventSubscriptions subscriptions = new PermanentEventSubscriptions(eventBus, modEventBus);

		ModLoadingContext modLoadingContext = ModLoadingContext.get();
		IServerConfig serverConfig = ServerConfig.register(modLoadingContext);

		NetworkHandler networkHandler = new NetworkHandler(Constants.NETWORK_CHANNEL_ID, "1.0.0");

		// Optional mod integrations (loaded only when the corresponding mod is installed):
		// AE2 crafting terminals / wireless crafting terminals, Sophisticated crafting upgrades,
		// Tinkers' Construct workstations.
		Ae2CraftingGridCraftExecutor.createIfLoaded()
			.ifPresent(CraftingGridCraftExecutors::registerExecutor);
		SophisticatedCraftingGridCraftExecutor.createIfLoaded()
			.ifPresent(CraftingGridCraftExecutors::registerExecutor);
		SophisticatedCraftingGridFillExecutor.createIfLoaded()
			.ifPresent(CraftingGridFillExecutors::registerExecutor);
		TinkerCraftingGridCraftExecutor.createIfLoaded()
			.ifPresent(CraftingGridCraftExecutors::registerExecutor);
		TinkerCraftingGridFillExecutor.createIfLoaded()
			.ifPresent(CraftingGridFillExecutors::registerExecutor);

		JustEnoughItemsCommon jeiCommon = new JustEnoughItemsCommon(networkHandler, serverConfig);
		jeiCommon.register(subscriptions);

		JustEnoughItemsClientSafeRunner clientSafeRunner = new JustEnoughItemsClientSafeRunner(networkHandler, subscriptions, serverConfig);
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> clientSafeRunner::registerClient);
	}
}
