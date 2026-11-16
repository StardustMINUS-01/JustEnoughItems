package mezz.jei.forge.input;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.Optional;

public final class ForgeUserInput {
	private ForgeUserInput() {}

	public static UserInput fromEvent(ScreenEvent.KeyPressed event) {
		return UserInput.fromVanilla(event.getKeyCode(), event.getScanCode(), event.getModifiers());
	}

	public static UserInput fromEvent(ScreenEvent.KeyReleased keyEvent) {
		InputConstants.Key input = InputConstants.getKey(keyEvent.getKeyCode(), keyEvent.getScanCode());
		double mouseX = MouseUtil.getX();
		double mouseY = MouseUtil.getY();
		int modifiers = keyEvent.getModifiers();
		return new UserInput(input, mouseX, mouseY, modifiers, InputPhase.RELEASE);
	}

	public static UserInput fromEvent(InputEvent.Key keyEvent) {
		InputConstants.Key input = InputConstants.getKey(keyEvent.getKey(), keyEvent.getScanCode());
		return new UserInput(input, 0, 0, keyEvent.getModifiers(), InputPhase.PRESS);
	}

	public static Optional<UserInput> fromEvent(ScreenEvent.MouseButtonPressed event) {
		return UserInput.fromVanilla(event.getMouseX(), event.getMouseY(), event.getButton(), InputPhase.PRESS);
	}

	public static Optional<UserInput> fromEvent(ScreenEvent.MouseButtonReleased event) {
		return UserInput.fromVanilla(event.getMouseX(), event.getMouseY(), event.getButton(), InputPhase.RELEASE);
	}
}
