package mezz.jei.forge.compat.ae2;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkExternalStoragePull;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransferHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class Ae2BookmarkPullTransferHandler implements ServerBookmarkPullTransferHandler {
	private static final Logger LOGGER = LogManager.getLogger();

	private final StorageAccess storageAccess;

	public Ae2BookmarkPullTransferHandler(StorageAccess storageAccess) {
		this.storageAccess = storageAccess;
	}

	public static Optional<Ae2BookmarkPullTransferHandler> createIfLoaded() {
		try {
			return Optional.of(new Ae2BookmarkPullTransferHandler(new ReflectionStorageAccess()));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Failed to initialize AE2 bookmark pull bridge", e);
			return Optional.empty();
		}
	}

	@Override
	public OptionalInt pull(
		AbstractContainerMenu menu,
		int containerId,
		Container playerInventory,
		@Nullable ServerPlayer player,
		List<BookmarkPullTarget> targets
	) {
		if (!storageAccess.isStorageMenu(menu)) {
			return OptionalInt.empty();
		}
		if (menu.containerId != containerId || targets.isEmpty() || !storageAccess.canInteract(menu)) {
			return OptionalInt.of(0);
		}

		int moved = ServerBookmarkExternalStoragePull.pull(
			menu,
			containerId,
			playerInventory,
			targets,
			(target, amount) -> {
				Object itemKey = storageAccess.createItemKey(target.itemStack());
				if (itemKey == null) {
					return ItemStack.EMPTY;
				}
				long extracted = storageAccess.extract(menu, itemKey, amount);
				if (extracted <= 0) {
					return ItemStack.EMPTY;
				}
				ItemStack extractedStack = target.itemStack().copy();
				extractedStack.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
				return extractedStack;
			}
		);
		return OptionalInt.of(moved);
	}

	public interface StorageAccess {
		boolean isStorageMenu(AbstractContainerMenu menu);

		boolean canInteract(AbstractContainerMenu menu);

		@Nullable
		Object createItemKey(ItemStack stack);

		long extract(AbstractContainerMenu menu, Object itemKey, long amount);
	}

	private static final class ReflectionStorageAccess implements StorageAccess {
		private static final String ME_STORAGE_MENU = "appeng.menu.me.common.MEStorageMenu";
		private static final String AE_ITEM_KEY = "appeng.api.stacks.AEItemKey";
		private static final String STORAGE_HELPER = "appeng.api.storage.StorageHelper";
		private static final String ENERGY_SOURCE = "appeng.api.networking.energy.IEnergySource";
		private static final String ME_STORAGE = "appeng.api.storage.MEStorage";
		private static final String AE_KEY = "appeng.api.stacks.AEKey";
		private static final String ACTION_SOURCE = "appeng.api.networking.security.IActionSource";

		private final Class<?> menuClass;
		private final Field storageField;
		private final Field energySourceField;
		private final @Nullable Method getLinkStatusMethod;
		private final @Nullable Method linkStatusConnectedMethod;
		private final @Nullable Method isPoweredMethod;
		private final Method getActionSourceMethod;
		private final Method createItemKeyMethod;
		private final Method poweredExtractionMethod;

		private ReflectionStorageAccess() throws ReflectiveOperationException {
			this.menuClass = Class.forName(ME_STORAGE_MENU);
			Class<?> itemKeyClass = Class.forName(AE_ITEM_KEY);
			Class<?> storageHelperClass = Class.forName(STORAGE_HELPER);
			Class<?> energySourceClass = Class.forName(ENERGY_SOURCE);
			Class<?> storageClass = Class.forName(ME_STORAGE);
			Class<?> aeKeyClass = Class.forName(AE_KEY);
			Class<?> actionSourceClass = Class.forName(ACTION_SOURCE);

			this.storageField = findField(menuClass, "storage");
			this.energySourceField = findField(menuClass, "energySource", "powerSource");
			this.getLinkStatusMethod = findMethodOrNull(menuClass, "getLinkStatus");
			this.linkStatusConnectedMethod = getLinkStatusMethod == null ? null : findMethodOrNull(getLinkStatusMethod.getReturnType(), "connected");
			this.isPoweredMethod = findMethodOrNull(menuClass, "isPowered");
			this.getActionSourceMethod = menuClass.getMethod("getActionSource");
			this.createItemKeyMethod = itemKeyClass.getMethod("of", ItemStack.class);
			this.poweredExtractionMethod = storageHelperClass.getMethod(
				"poweredExtraction",
				energySourceClass,
				storageClass,
				aeKeyClass,
				long.class,
				actionSourceClass
			);
		}

		@Override
		public boolean isStorageMenu(AbstractContainerMenu menu) {
			return menuClass.isInstance(menu);
		}

		@Override
		public boolean canInteract(AbstractContainerMenu menu) {
			try {
				if (storageField.get(menu) == null || energySourceField.get(menu) == null) {
					return false;
				}
				if (getLinkStatusMethod != null && linkStatusConnectedMethod != null) {
					Object linkStatus = getLinkStatusMethod.invoke(menu);
					return Boolean.TRUE.equals(linkStatusConnectedMethod.invoke(linkStatus));
				}
				return isPoweredMethod == null || Boolean.TRUE.equals(isPoweredMethod.invoke(menu));
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to check AE2 terminal state for bookmark pull", e);
				return false;
			}
		}

		@Override
		public @Nullable Object createItemKey(ItemStack stack) {
			try {
				return createItemKeyMethod.invoke(null, stack);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to create AE2 item key for bookmark pull", e);
				return null;
			}
		}

		@Override
		public long extract(AbstractContainerMenu menu, Object itemKey, long amount) {
			try {
				Object energySource = energySourceField.get(menu);
				Object storage = storageField.get(menu);
				Object actionSource = getActionSourceMethod.invoke(menu);
				Object extracted = poweredExtractionMethod.invoke(null, energySource, storage, itemKey, amount, actionSource);
				return extracted instanceof Number number ? number.longValue() : 0;
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to extract item from AE2 terminal for bookmark pull", e);
				return 0;
			}
		}

		private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
			for (String name : names) {
				try {
					Field field = owner.getDeclaredField(name);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
			throw new NoSuchFieldException(String.join(", ", names));
		}

		private static @Nullable Method findMethodOrNull(Class<?> owner, String name) {
			try {
				return owner.getMethod(name);
			} catch (NoSuchMethodException e) {
				return null;
			}
		}
	}
}
