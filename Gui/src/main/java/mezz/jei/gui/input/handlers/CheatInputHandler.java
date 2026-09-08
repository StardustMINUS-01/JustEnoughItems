package mezz.jei.gui.input.handlers;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.util.CommandUtil;
import mezz.jei.gui.util.GiveAmount;
import mezz.jei.gui.overlay.bookmarks.ScrollStep;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class CheatInputHandler implements IUserInputHandler {
	private final CombinedRecipeFocusSource focusSource;
	private final IIngredientManager ingredientManager;
	private final IClientToggleState toggleState;
	private final CommandUtil commandUtil;
	private final IClientConfig clientConfig;
	private final ScrollStep scrollStep;

	public CheatInputHandler(
		CombinedRecipeFocusSource focusSource,
		IClientConfig clientConfig,
		IIngredientManager ingredientManager,
		IClientToggleState toggleState,
		IConnectionToServer serverConnection
	) {
		this(focusSource, clientConfig, ingredientManager, toggleState, serverConnection, new ScrollStep());
	}

	public CheatInputHandler(
		CombinedRecipeFocusSource focusSource,
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
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (!toggleState.isCheatItemsEnabled() ||
			!(screen instanceof AbstractContainerScreen<?>)
		) {
			return Optional.empty();
		}

		if (input.is(keyBindings.getCheatItemStack())) {
			Optional<IUserInputHandler> handler = handleGive(input, keyBindings, GiveAmount.MAX);
			if (handler.isPresent()) {
				return handler;
			}
		}

		if (input.is(keyBindings.getCheatOneItem())) {
			return handleGive(input, keyBindings, GiveAmount.ONE);
		}

		return Optional.empty();
	}

	private Optional<IUserInputHandler> handleGive(UserInput input, IInternalKeyMappings keyBindings, GiveAmount giveAmount) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.<IUserInputHandler>mapMulti((clicked, consumer) -> {
				ItemStack itemStack = clicked.getCheatItemStack(ingredientManager);
				if (!itemStack.isEmpty()) {
					if (!input.isSimulate()) {
						int amount = resolveGiveAmount(clientConfig.giveMode().getValue(), giveAmount,
							itemStack, clicked.getCheatGiveAmount(), scrollStep.getValue());
						commandUtil.giveStack(itemStack, amount);
					}
					consumer.accept(new SameElementInputHandler(this, clicked::isMouseOver));
				}
			})
			.findFirst();
	}

	static int resolveGiveAmount(GiveMode mode, GiveAmount giveAmount, ItemStack stack, Optional<Long> bookmarkAmount, long configuredAmount) {
		if (mode == GiveMode.MOUSE_PICKUP) {
			return giveAmount.getAmountForStack(stack);
		}
		long amount = bookmarkAmount.orElse(configuredAmount == 0 ? stack.getMaxStackSize() : configuredAmount);
		return (int) Math.clamp(amount, 1, Integer.MAX_VALUE);
	}
}
