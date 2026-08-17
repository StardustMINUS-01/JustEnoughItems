package mezz.jei.forge.compat.tconstruct;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

import java.util.List;
import java.util.Optional;

/**
 * Extracts the crafting grid slots and result slot from Tinkers' Construct workstation menus.
 *
 * CraftingStation: standard 3x3 grid (slots 0-8) with the result slot at index 9.
 * TinkerStation: tool slot 0 + flat input slots (getInputSlots()) + result slot at inputCount + 1.
 */
public final class TinkerCraftingGridAccess {
	private final List<Slot> craftingSlots;
	private final Slot resultSlot;
	private final int resultSlotIndex;

	private TinkerCraftingGridAccess(List<Slot> craftingSlots, Slot resultSlot, int resultSlotIndex) {
		this.craftingSlots = List.copyOf(craftingSlots);
		this.resultSlot = resultSlot;
		this.resultSlotIndex = resultSlotIndex;
	}

	public static Optional<TinkerCraftingGridAccess> find(AbstractContainerMenu menu) {
		if (menu instanceof CraftingStationContainerMenu craftingStation) {
			if (menu.slots.size() <= 9) {
				return Optional.empty();
			}
			List<Slot> gridSlots = menu.slots.subList(0, 9);
			Slot resultSlot = menu.slots.get(9);
			return Optional.of(new TinkerCraftingGridAccess(gridSlots, resultSlot, 9));
		}
		if (menu instanceof TinkerStationContainerMenu tinkerStation) {
			List<Slot> inputSlots = tinkerStation.getInputSlots();
			int resultSlotIndex = inputSlots.size() + 1;
			if (menu.slots.size() <= resultSlotIndex) {
				return Optional.empty();
			}
			Slot resultSlot = menu.slots.get(resultSlotIndex);
			return Optional.of(new TinkerCraftingGridAccess(inputSlots, resultSlot, resultSlotIndex));
		}
		return Optional.empty();
	}

	public List<Slot> craftingSlots() {
		return craftingSlots;
	}

	public Slot resultSlot() {
		return resultSlot;
	}

	public int resultSlotIndex() {
		return resultSlotIndex;
	}
}
