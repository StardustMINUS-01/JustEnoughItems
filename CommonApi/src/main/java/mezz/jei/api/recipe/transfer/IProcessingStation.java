package mezz.jei.api.recipe.transfer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A current-menu processing station. Register the same adapter on client and server. */
public interface IProcessingStation {
	ResourceLocation getRecipeTypeId();

	boolean canHandle(AbstractContainerMenu menu);

	/** Input order must match the category's INPUT slots, not screen coordinates. */
	List<Slot> getInputSlots(AbstractContainerMenu menu);

	Slot getResultSlot(AbstractContainerMenu menu);

	/** Called on the server after filling inputs. Select/recompute the recipe using the real menu. */
	boolean selectRecipe(ServerPlayer player, @Nullable ResourceLocation recipeId);

	default boolean matchesOutput(ItemStack expected, ItemStack actual) {
		return !expected.isEmpty() && ItemStack.isSameItemSameTags(expected, actual) && actual.getCount() == expected.getCount();
	}
}
