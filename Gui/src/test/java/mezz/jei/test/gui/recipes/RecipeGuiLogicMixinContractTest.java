package mezz.jei.test.gui.recipes;

import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeGuiLogicMixinContractTest {
	@Test
	public void setStateSignatureIsStableForThirdPartyMixins() throws Exception {
		Method setState = RecipeGuiLogic.class.getDeclaredMethod("setState", ILookupState.class, boolean.class);

		assertEquals(boolean.class, setState.getReturnType());
		assertTrue(Modifier.isPrivate(setState.getModifiers()));
		assertFalse(Modifier.isStatic(setState.getModifiers()));
	}
}
