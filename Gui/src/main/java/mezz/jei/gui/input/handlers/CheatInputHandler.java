package mezz.jei.gui.input.handlers;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.platform.Services;
import mezz.jei.gui.input.IngredientFocusSource;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.ingredients.TypedIngredientUtil;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.util.CommandUtil;
import mezz.jei.gui.util.GiveAmount;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class CheatInputHandler implements IInputTarget {
	private final IngredientFocusSource focusSource;
	private final IIngredientManager ingredientManager;
	private final IClientToggleState toggleState;
	private final CommandUtil commandUtil;
	private final IClientConfig clientConfig;
	private final ScrollStep scrollStep;

	public CheatInputHandler(
		IngredientFocusSource focusSource,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection
	) {
		this(focusSource, clientConfig, ingredientManager, toggleState, serverConnection, new ScrollStep());
	}

	public CheatInputHandler(
		IngredientFocusSource focusSource,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection,
		ScrollStep scrollStep
	) {
		this.focusSource = focusSource;
		this.ingredientManager = ingredientManager;
		this.toggleState = toggleState;
		this.commandUtil = new CommandUtil(clientConfig, serverConnection);
		this.clientConfig = clientConfig;
		this.scrollStep = scrollStep;
	}

	@Override
	public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!toggleState.isCheatItemsEnabled() ||
			!(screen instanceof AbstractContainerScreen<?>)
		) {
			return Optional.empty();
		}

		if (input.is(keyBindings.getCheatItemStack())) {
			Optional<IInputInteraction> handler = handleGive(input, keyBindings, GiveAmount.MAX);
			if (handler.isPresent()) {
				return handler;
			}
		}

		if (input.is(keyBindings.getCheatOneItem())) {
			return handleGive(input, keyBindings, GiveAmount.ONE);
		}

		return Optional.empty();
	}

	private Optional<IInputInteraction> handleGive(UserInput input, IInternalKeyMappings keyBindings, GiveAmount giveAmount) {
		return focusSource.getIngredientForInput(input, keyBindings)
			.filter(mezz.jei.gui.input.IClickableIngredientInternal::canClickToCheat)
			.<IInputInteraction>mapMulti((clicked, consumer) -> {
				ItemStack itemStack = TypedIngredientUtil.getCheatItemStack(ingredientManager, clicked.getTypedIngredient());
				if (!itemStack.isEmpty()) {
					consumer.accept(InputAction.run(() -> {
							IIngredientType<?> type = clicked.getTypedIngredient().getType();
							var fluidHelper = Services.PLATFORM.getFluidHelper();
							long bucketVolume = type.equals(fluidHelper.getFluidIngredientType()) ? fluidHelper.bucketVolume() : 0;
							int amount = resolveGiveAmount(clientConfig.giveMode().get(), giveAmount,
								itemStack, clicked.getCheatGiveAmount(), scrollStep.getValue(), type, bucketVolume);
							commandUtil.giveStack(itemStack, amount);
						})
						.within(clicked::isMouseOver).when(() -> toggleState.isCheatItemsEnabled() && clicked.getElement().isVisible()));
				}
			})
			.findFirst();
	}

	static int resolveGiveAmount(GiveMode mode, GiveAmount giveAmount, ItemStack stack, Optional<Long> bookmarkAmount, long configuredAmount, IIngredientType<?> type, long bucketVolume) {
		if (!type.equals(VanillaTypes.ITEM_STACK)) {
			if (bucketVolume > 0 && mode == GiveMode.INVENTORY && bookmarkAmount.isPresent()) {
				long buckets = Math.ceilDiv(bookmarkAmount.get(), bucketVolume);
				return (int) Math.clamp(buckets, 1, Integer.MAX_VALUE);
			}
			return stack.getCount();
		}
		if (mode == GiveMode.MOUSE_PICKUP) {
			return giveAmount.getAmountForStack(stack);
		}
		long amount = bookmarkAmount.orElse(configuredAmount == 0 ? stack.getMaxStackSize() : configuredAmount);
		return (int) Math.clamp(amount, 1, Integer.MAX_VALUE);
	}
}
