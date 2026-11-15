package mezz.jei.neoforge.input;

import com.mojang.blaze3d.platform.InputConstants;

import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.Optional;

public final class ForgeUserInput {
	private ForgeUserInput() {}

	public static UserInput fromEvent(ScreenEvent.KeyPressed event) {
		return UserInput.fromVanilla(event.getKeyCode(), event.getScanCode(), event.getModifiers());
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
