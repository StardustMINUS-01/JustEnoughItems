package mezz.jei.common.bookmarks;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IProcessingStation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

final class VanillaProcessingStation implements IProcessingStation {
	private final ResourceLocation recipeType;
	private final Class<? extends AbstractContainerMenu> menuClass;
	private final int inputs;

	private VanillaProcessingStation(ResourceLocation recipeType, Class<? extends AbstractContainerMenu> menuClass, int inputs) {
		this.recipeType = recipeType;
		this.menuClass = menuClass;
		this.inputs = inputs;
	}

	static List<IProcessingStation> create() {
		return List.of(
			new VanillaProcessingStation(RecipeTypes.STONECUTTING.getUid(), StonecutterMenu.class, 1),
			new VanillaProcessingStation(RecipeTypes.SMITHING.getUid(), SmithingMenu.class, 3),
			new VanillaProcessingStation(RecipeTypes.ANVIL.getUid(), AnvilMenu.class, 2)
		);
	}

	@Override
	public ResourceLocation getRecipeTypeId() { return recipeType; }

	@Override
	public boolean canHandle(AbstractContainerMenu menu) { return menuClass.isInstance(menu); }

	@Override
	public List<Slot> getInputSlots(AbstractContainerMenu menu) {
		return IntStream.range(0, inputs).mapToObj(menu::getSlot).toList();
	}

	@Override
	public Slot getResultSlot(AbstractContainerMenu menu) { return menu.getSlot(inputs); }

	@Override
	public boolean selectRecipe(ServerPlayer player, @Nullable ResourceLocation recipeId) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu instanceof StonecutterMenu stonecutter) {
			var recipes = stonecutter.getRecipes();
			for (int i = 0; i < recipes.size(); i++) {
				if (recipes.get(i).getId().equals(recipeId)) {
					return stonecutter.clickMenuButton(player, i);
				}
			}
			return false;
		}
		if (menu instanceof SmithingMenu smithing) {
			smithing.createResult();
			return true;
		}
		if (menu instanceof AnvilMenu anvil) {
			anvil.createResult();
			return true;
		}
		return false;
	}

	@Override
	public boolean matchesOutput(ItemStack expected, ItemStack actual) {
		if (menuClass == AnvilMenu.class) {
			// Prior-work penalties are computed by the real anvil, not the JEI display recipe.
			expected = expected.copy();
			actual = actual.copy();
			expected.removeTagKey("RepairCost");
			actual.removeTagKey("RepairCost");
			if (expected.getTag() != null && expected.getTag().isEmpty()) {
				expected.setTag(null);
			}
			if (actual.getTag() != null && actual.getTag().isEmpty()) {
				actual.setTag(null);
			}
		}
		return IProcessingStation.super.matchesOutput(expected, actual);
	}
}
