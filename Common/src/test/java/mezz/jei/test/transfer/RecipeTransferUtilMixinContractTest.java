package mezz.jei.test.transfer;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.common.transfer.RecipeTransferUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeTransferUtilMixinContractTest {
	@Test
	public void testTransferRecipeSignatureIsStableForThirdPartyMixins() throws Exception {
		Method transferRecipe = RecipeTransferUtil.class.getDeclaredMethod(
			"transferRecipe",
			IRecipeTransferManager.class,
			AbstractContainerMenu.class,
			IRecipeLayoutDrawable.class,
			Player.class,
			boolean.class,
			boolean.class
		);
		assertNotNull(transferRecipe, "RecipeTransferUtil must keep the 6-arg transferRecipe method");
		assertEquals(
			Optional.class,
			transferRecipe.getReturnType(),
			"RecipeTransferUtil.transferRecipe must keep returning Optional; third-party mixins (e.g. DataEnergistics) target this descriptor"
		);
		assertEquals(
			IRecipeTransferError.class,
			((java.lang.reflect.ParameterizedType) transferRecipe.getGenericReturnType()).getActualTypeArguments()[0]
		);
		int modifiers = transferRecipe.getModifiers();
		assertTrue(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers), "RecipeTransferUtil.transferRecipe must stay private static");
	}
}
