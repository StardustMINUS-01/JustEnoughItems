package mezz.jei.common.bookmarks;

import mezz.jei.api.recipe.transfer.IProcessingStation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Register during mod initialization on both physical sides; first matching adapter wins. */
public final class ProcessingStations {
	private static final List<IProcessingStation> STATIONS = new CopyOnWriteArrayList<>(VanillaProcessingStation.create());

	private ProcessingStations() {}

	public static AutoCloseable register(IProcessingStation station) {
		STATIONS.addFirst(station);
		return () -> STATIONS.remove(station);
	}

	public static Optional<IProcessingStation> find(AbstractContainerMenu menu, ResourceLocation recipeType) {
		return STATIONS.stream().filter(s -> s.getRecipeTypeId().equals(recipeType) && s.canHandle(menu)).findFirst();
	}

	public static int getInputSlotCount(AbstractContainerMenu menu) {
		return STATIONS.stream().filter(s -> s.canHandle(menu)).findFirst().map(s -> s.getInputSlots(menu).size()).orElse(0);
	}
}
