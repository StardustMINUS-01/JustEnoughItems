package mezz.jei.neoforge;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.ModIds;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.IngredientTooltipComponent;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkGhostOverlayTargetSlots;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridgeRegistry;
import mezz.jei.gui.config.InternalKeyMappings;
import mezz.jei.library.plugins.vanilla.crafting.JeiShapedRecipe;
import mezz.jei.library.recipes.RecipeSerializers;
import mezz.jei.library.startup.JeiStarter;
import mezz.jei.library.startup.StartData;
import mezz.jei.neoforge.chat.JeiChatEventHandler;
import mezz.jei.neoforge.chat.JeiChatTooltipEventHandler;
import mezz.jei.neoforge.chat.JeiInternalShowCommand;
import mezz.jei.neoforge.events.PermanentEventSubscriptions;
import mezz.jei.neoforge.compat.ae2.Ae2BookmarkStorageSnapshotProvider;
import mezz.jei.neoforge.compat.ae2.Ae2AvailableStacksProvider;
import mezz.jei.neoforge.compat.ae2.Ae2CraftingGridTargetSlotProvider;
import mezz.jei.neoforge.compat.ae2.Ae2GroupDropCompat;
import mezz.jei.neoforge.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import mezz.jei.neoforge.compat.ae2.Ae2JeiSearchTextCompat;
import mezz.jei.neoforge.compat.sophisticated.SophisticatedAvailableStacksProvider;
import mezz.jei.neoforge.compat.sophisticated.SophisticatedCraftingGridTargetSlotProvider;
import mezz.jei.neoforge.network.NetworkHandler;
import mezz.jei.neoforge.plugins.neoforge.NeoForgeGuiPlugin;
import mezz.jei.neoforge.startup.ForgePluginFinder;
import mezz.jei.neoforge.startup.StartEventObserver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class JustEnoughItemsClient {
	private final PermanentEventSubscriptions subscriptions;
	private final JeiStarter jeiStarter;

	public JustEnoughItemsClient(
		NetworkHandler networkHandler,
		PermanentEventSubscriptions subscriptions
	) {
		this.subscriptions = subscriptions;
		IConnectionToServer serverConnection = networkHandler.getConnectionToServer();

		List<IModPlugin> plugins = ForgePluginFinder.getModPlugins();
		StartData startData = new StartData(
			plugins,
			serverConnection
		);

		this.jeiStarter = new JeiStarter(startData);

		StartEventObserver startEventObserver = new StartEventObserver(this.jeiStarter::start, this.jeiStarter::stop);
		startEventObserver.register(subscriptions);
	}

	public void register() {
		subscriptions.register(RegisterClientReloadListenersEvent.class, this::onRegisterReloadListenerEvent);
		subscriptions.register(RegisterClientTooltipComponentFactoriesEvent.class, this::onRegisterClientTooltipEvent);
		subscriptions.register(RecipesUpdatedEvent.class, this::onRecipesUpdatedEvent);
		subscriptions.register(GameShuttingDownEvent.class, e -> onGameShuttingDown());
		subscriptions.register(RegisterKeyMappingsEvent.class, e -> {
			InternalKeyMappings keyMappings = new InternalKeyMappings(e::register);
			Internal.setKeyMappings(keyMappings);
		});

		JeiChatEventHandler.register(subscriptions);
		JeiChatTooltipEventHandler.register(subscriptions);
		JeiInternalShowCommand.register(subscriptions);

		IEventBus modEventBus = subscriptions.getModEventBus();
		DeferredRegister<RecipeSerializer<?>> deferredRegister = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, ModIds.JEI_ID);
		deferredRegister.register(modEventBus);

		Supplier<RecipeSerializer<?>> jeiShaped = deferredRegister.register("jei_shaped", JeiShapedRecipe.Serializer::new);
		RecipeSerializers.register(jeiShaped);

		Ae2BookmarkStorageSnapshotProvider.createIfLoaded()
			.ifPresent(BookmarkExternalStorageSnapshots::registerProvider);
		Ae2AvailableStacksProvider.createIfLoaded()
			.ifPresent(BookmarkAvailableStacksProviders::registerProvider);
		SophisticatedAvailableStacksProvider.createIfLoaded()
			.ifPresent(BookmarkAvailableStacksProviders::registerProvider);
		Ae2CraftingGridTargetSlotProvider.createIfLoaded()
			.ifPresent(BookmarkGhostOverlayTargetSlots::registerProvider);
		SophisticatedCraftingGridTargetSlotProvider.createIfLoaded()
			.ifPresent(BookmarkGhostOverlayTargetSlots::registerProvider);
		Ae2RecipeChainPatternEncodingBridge.createIfLoaded()
			.ifPresent(Ae2RecipeChainPatternEncodingBridgeRegistry::register);
		Ae2JeiSearchTextCompat.register();
		Ae2GroupDropCompat.register();
	}

	private void onGameShuttingDown() {
		jeiStarter.stop();
		Internal.onClientStopping();
	}

	private void onRecipesUpdatedEvent(RecipesUpdatedEvent event) {
		List<RecipeHolder<?>> recipes = List.copyOf(event.getRecipeManager().getRecipes());
		if (!recipes.isEmpty()) {
			Internal.setClientSyncedRecipes(recipes);
		}
	}

	private void onRegisterReloadListenerEvent(RegisterClientReloadListenersEvent event) {
		Textures textures = Internal.getTextures();
		event.registerReloadListener(textures.getGuiSpriteManager());
		event.registerReloadListener(createReloadListener());
	}

	private void onRegisterClientTooltipEvent(RegisterClientTooltipComponentFactoriesEvent event) {
		TooltipComponentFactories.register(event);
		event.register(IngredientTooltipComponent.class, Function.identity());
	}

	private ResourceManagerReloadListener createReloadListener() {
		return (ResourceManager resourceManager) -> {
			NeoForgeGuiPlugin.getResourceReloadHandler()
				.ifPresent(r -> r.onResourceManagerReload(resourceManager));
		};
	}

}
