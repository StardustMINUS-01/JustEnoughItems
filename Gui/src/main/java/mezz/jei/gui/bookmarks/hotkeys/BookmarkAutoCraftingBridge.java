/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forge/JEI bridge for GTNH NEI bookmark recipe-chain autocrafting.
 * It deliberately sends only one vanilla craft packet per activation because
 * server execution and client inventory sync happen asynchronously.
 */
public final class BookmarkAutoCraftingBridge {
	private static final int MAX_WAIT_TICKS = 40;
	private static final AtomicInteger NEXT_TASK_ID = new AtomicInteger(1);

	private BookmarkAutoCraftingBridge() {
	}

	public static Optional<Task> createTask(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		int containerId,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		BooleanSupplier stillValid
	) {
		return createTask(
			chainInputs,
			collapsedRecipeIds,
			targetSlotCount,
			containerId,
			inventorySupplier,
			availableStacksSupplier,
			recipeLayoutResolver,
			packetSender,
			stillValid,
			false
		);
	}

	public static Optional<Task> createTask(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		int containerId,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		BooleanSupplier stillValid,
		boolean craftAll
	) {
		if (chainInputs.isEmpty() || targetSlotCount <= 0) {
			return Optional.empty();
		}
		return Optional.of(new Task(
			chainInputs,
			collapsedRecipeIds,
			targetSlotCount,
			containerId,
			null,
			inventorySupplier,
			availableStacksSupplier,
			recipeLayoutResolver,
			packetSender,
			BookmarkAutoCraftingActivator.ClientFallbackStarter.DISABLED,
			Optional::empty,
			true,
			stillValid,
			craftAll
		));
	}

	public static Optional<Task> createClientFallbackTask(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		AbstractContainerMenu containerMenu,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		BookmarkAutoCraftingActivator.ClientFallbackStarter clientFallbackStarter,
		Supplier<Optional<Boolean>> clientFallbackResult,
		BooleanSupplier stillValid
	) {
		return createClientFallbackTask(
			chainInputs,
			collapsedRecipeIds,
			targetSlotCount,
			containerMenu,
			inventorySupplier,
			availableStacksSupplier,
			recipeLayoutResolver,
			clientFallbackStarter,
			clientFallbackResult,
			stillValid,
			false
		);
	}

	public static Optional<Task> createClientFallbackTask(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		AbstractContainerMenu containerMenu,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		BookmarkAutoCraftingActivator.ClientFallbackStarter clientFallbackStarter,
		Supplier<Optional<Boolean>> clientFallbackResult,
		BooleanSupplier stillValid,
		boolean craftAll
	) {
		if (chainInputs.isEmpty() || targetSlotCount <= 0) {
			return Optional.empty();
		}
		return Optional.of(new Task(
			chainInputs,
			collapsedRecipeIds,
			targetSlotCount,
			containerMenu.containerId,
			containerMenu,
			inventorySupplier,
			availableStacksSupplier,
			recipeLayoutResolver,
			packet -> {
			},
			clientFallbackStarter,
			clientFallbackResult,
			false,
			stillValid,
			craftAll
		));
	}

	public static boolean activate(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		int containerId,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		boolean simulate
	) {
		return activate(
			chainInputs,
			collapsedRecipeIds,
			targetSlotCount,
			containerId,
			inventorySupplier,
			availableStacksSupplier,
			recipeLayoutResolver,
			packetSender,
			simulate,
			false
		);
	}

	public static boolean activate(
		List<RecipeChainInput> chainInputs,
		Set<ResourceLocation> collapsedRecipeIds,
		int targetSlotCount,
		int containerId,
		Supplier<List<RecipeChainInput>> inventorySupplier,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		boolean simulate,
		boolean craftAll
	) {
		RecipeChainMath math = RecipeChainMath.of(chainInputs, collapsedRecipeIds);
		AtomicBoolean craftedOnePacket = new AtomicBoolean(false);
		List<RecipeChainInput> inventorySnapshot = List.copyOf(inventorySupplier.get());
		if (craftAll) {
			math.expandRootDemandForCraftAll(inventorySnapshot);
		}
		AutoCraftingManager.Result result = AutoCraftingManager.run(
			math,
			List.of(),
			() -> inventorySnapshot,
			(recipeUid, multiplier) -> {
				if (craftedOnePacket.get()) {
					return false;
				}
				return craft(
					recipeUid,
					multiplier,
					targetSlotCount,
					containerId,
					availableStacksSupplier,
					recipeLayoutResolver,
					packetSender,
					simulate,
					craftedOnePacket
				);
			},
			craftedOnePacket::get
		);
		return result.processed();
	}

	public static final class Task {
		private final int taskId = NEXT_TASK_ID.getAndIncrement();
		private final List<RecipeChainInput> chainInputs;
		private final Set<ResourceLocation> collapsedRecipeIds;
		private final int targetSlotCount;
		private final int containerId;
		private final AbstractContainerMenu clientFallbackMenu;
		private final Supplier<List<RecipeChainInput>> inventorySupplier;
		private final Supplier<List<ItemStack>> availableStacksSupplier;
		private final Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver;
		private final Consumer<PlayToServerPacket> packetSender;
		private final BookmarkAutoCraftingActivator.ClientFallbackStarter clientFallbackStarter;
		private final Supplier<Optional<Boolean>> clientFallbackResult;
		private final boolean serverAckMode;
		private final BooleanSupplier stillValid;
		private final boolean craftAll;
		private boolean active = true;
		private boolean waitingForAck;
		private boolean waitingForClientFallback;
		private boolean waitingForInventorySync;
		private List<RecipeChainInput> dispatchedInventorySnapshot = List.of();
		private List<RecipeChainInput> craftAllExpansionInventorySnapshot = List.of();
		private boolean craftAllExpansionInventoryCaptured;
		private ResourceLocation dispatchedRecipeUid;
		private int acknowledgedCraftedCount;
		private int waitTicks;
		private int expectedRequestId;
		private int nextRequestId = 1;

		private Task(
			List<RecipeChainInput> chainInputs,
			Set<ResourceLocation> collapsedRecipeIds,
			int targetSlotCount,
			int containerId,
			AbstractContainerMenu clientFallbackMenu,
			Supplier<List<RecipeChainInput>> inventorySupplier,
			Supplier<List<ItemStack>> availableStacksSupplier,
			Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
			Consumer<PlayToServerPacket> packetSender,
			BookmarkAutoCraftingActivator.ClientFallbackStarter clientFallbackStarter,
			Supplier<Optional<Boolean>> clientFallbackResult,
			boolean serverAckMode,
			BooleanSupplier stillValid,
			boolean craftAll
		) {
			this.chainInputs = List.copyOf(chainInputs);
			this.collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
			this.targetSlotCount = targetSlotCount;
			this.containerId = containerId;
			this.clientFallbackMenu = clientFallbackMenu;
			this.inventorySupplier = inventorySupplier;
			this.availableStacksSupplier = availableStacksSupplier;
			this.recipeLayoutResolver = recipeLayoutResolver;
			this.packetSender = packetSender;
			this.clientFallbackStarter = clientFallbackStarter;
			this.clientFallbackResult = clientFallbackResult;
			this.serverAckMode = serverAckMode;
			this.stillValid = stillValid;
			this.craftAll = craftAll;
		}

		public int taskId() {
			return taskId;
		}

		public boolean start() {
			if (!active || !stillValid.getAsBoolean()) {
				active = false;
				return false;
			}
			if (!dispatchNext()) {
				active = false;
				return false;
			}
			return true;
		}

		public boolean tick() {
			if (!active || !stillValid.getAsBoolean()) {
				active = false;
				return false;
			}
			if (waitingForAck) {
				waitTicks++;
				if (waitTicks > MAX_WAIT_TICKS) {
					active = false;
					return false;
				}
				return true;
			}
			if (waitingForClientFallback) {
				waitTicks++;
				if (waitTicks > MAX_WAIT_TICKS) {
					active = false;
					return false;
				}
				Optional<Boolean> result = clientFallbackResult.get();
				if (result.isEmpty()) {
					return true;
				}
				waitingForClientFallback = false;
				waitTicks = 0;
				if (!result.get()) {
					active = false;
					return false;
				}
				return true;
			}
			if (waitingForInventorySync) {
				waitTicks++;
				if (!inventoryHasExpectedResultIncreaseSinceDispatch()) {
					if (waitTicks > MAX_WAIT_TICKS) {
						active = false;
						return false;
					}
					return true;
				}
				waitingForInventorySync = false;
				waitTicks = 0;
			}
			if (!dispatchNext()) {
				active = false;
				return false;
			}
			return true;
		}

		public void handleAck(int requestId, int craftedCount) {
			if (!active || !waitingForAck || requestId != expectedRequestId) {
				return;
			}
			if (craftedCount <= 0) {
				active = false;
				waitingForAck = false;
				return;
			}
			waitingForAck = false;
			acknowledgedCraftedCount = craftedCount;
			waitingForInventorySync = true;
			waitTicks = 0;
		}

		private boolean dispatchNext() {
			RecipeChainMath math = RecipeChainMath.of(chainInputs, collapsedRecipeIds);
			AtomicBoolean craftedOnePacket = new AtomicBoolean(false);
			int requestId = nextRequestId++;
			dispatchedInventorySnapshot = List.copyOf(inventorySupplier.get());
			if (craftAll) {
				if (!craftAllExpansionInventoryCaptured) {
					craftAllExpansionInventorySnapshot = dispatchedInventorySnapshot;
					craftAllExpansionInventoryCaptured = true;
				}
				math.expandRootDemandForCraftAll(craftAllExpansionInventorySnapshot);
			}
			dispatchedRecipeUid = null;
			acknowledgedCraftedCount = 0;
			AutoCraftingManager.Result result = AutoCraftingManager.run(
				math,
				List.of(),
				() -> dispatchedInventorySnapshot,
				(recipeUid, multiplier) -> {
					if (craftedOnePacket.get()) {
						return false;
					}
					if (serverAckMode) {
						return craft(
							recipeUid,
							multiplier,
							targetSlotCount,
							containerId,
							availableStacksSupplier,
							recipeLayoutResolver,
							packetSender,
							false,
							taskId,
							requestId,
							craftedOnePacket,
							() -> dispatchedRecipeUid = recipeUid
						);
					}
					return craftClientFallback(
						recipeUid,
						targetSlotCount,
						availableStacksSupplier,
						recipeLayoutResolver,
						clientFallbackStarter,
						clientFallbackMenu,
						craftedOnePacket
					);
				},
				craftedOnePacket::get
			);
			if (!result.processed() || !craftedOnePacket.get()) {
				return false;
			}
			if (serverAckMode) {
				waitingForAck = true;
				expectedRequestId = requestId;
			} else {
				waitingForClientFallback = true;
			}
			waitTicks = 0;
			return true;
		}

		private boolean inventoryHasExpectedResultIncreaseSinceDispatch() {
			if (dispatchedRecipeUid == null || acknowledgedCraftedCount <= 0) {
				return false;
			}
			long before = inventoryAmountForRecipeResults(dispatchedInventorySnapshot, dispatchedRecipeUid);
			long after = inventoryAmountForRecipeResults(inventorySupplier.get(), dispatchedRecipeUid);
			long expectedIncrease = expectedResultAmount(dispatchedRecipeUid, acknowledgedCraftedCount);
			if (expectedIncrease <= 0) {
				return after > before;
			}
			return after >= saturatedAdd(before, expectedIncrease);
		}

		private long expectedResultAmount(ResourceLocation recipeUid, int craftedCount) {
			long amount = 0;
			for (RecipeChainInput input : chainInputs) {
				BookmarkItemMetadata metadata = input.metadata();
				if (metadata.type().isGraphOutput() && recipeUid.equals(metadata.recipeUid())) {
					amount = saturatedAdd(amount, metadata.amount(craftedCount));
				}
			}
			return amount;
		}

		private long inventoryAmountForRecipeResults(List<RecipeChainInput> inputs, ResourceLocation recipeUid) {
			long amount = 0;
			List<BookmarkItemMetadata> results = resultMetadataForRecipe(recipeUid);
			for (RecipeChainInput input : inputs) {
				BookmarkItemMetadata available = input.metadata();
				boolean matchesResult = results.stream()
					.anyMatch(result -> result.isSatisfiedBy(available));
				if (matchesResult) {
					amount = saturatedAdd(amount, available.amount());
				}
			}
			return amount;
		}

		private List<BookmarkItemMetadata> resultMetadataForRecipe(ResourceLocation recipeUid) {
			List<BookmarkItemMetadata> results = new ArrayList<>();
			for (RecipeChainInput input : chainInputs) {
				BookmarkItemMetadata metadata = input.metadata();
				if (metadata.type().isGraphOutput() && recipeUid.equals(metadata.recipeUid())) {
					results.add(metadata);
				}
			}
			return results;
		}

		private static long saturatedAdd(long first, long second) {
			try {
				return Math.addExact(first, second);
			} catch (ArithmeticException e) {
				return Long.MAX_VALUE;
			}
		}
	}

	private static boolean craft(
		ResourceLocation recipeUid,
		int multiplier,
		int targetSlotCount,
		int containerId,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		boolean simulate,
		AtomicBoolean craftedOnePacket
	) {
		return craft(
			recipeUid,
			multiplier,
			targetSlotCount,
			containerId,
			availableStacksSupplier,
			recipeLayoutResolver,
			packetSender,
			simulate,
			0,
			0,
			craftedOnePacket,
			() -> {
			}
		);
	}

	private static boolean craft(
		ResourceLocation recipeUid,
		int multiplier,
		int targetSlotCount,
		int containerId,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		boolean simulate,
		int taskId,
		int requestId,
		AtomicBoolean craftedOnePacket
	) {
		return craft(
			recipeUid,
			multiplier,
			targetSlotCount,
			containerId,
			availableStacksSupplier,
			recipeLayoutResolver,
			packetSender,
			simulate,
			taskId,
			requestId,
			craftedOnePacket,
			() -> {
			}
		);
	}

	private static boolean craft(
		ResourceLocation recipeUid,
		int multiplier,
		int targetSlotCount,
		int containerId,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		Consumer<PlayToServerPacket> packetSender,
		boolean simulate,
		int taskId,
		int requestId,
		AtomicBoolean craftedOnePacket,
		Runnable afterCraftAccepted
	) {
		IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutResolver.apply(recipeUid).orElse(null);
		if (recipeLayout == null) {
			return false;
		}

		List<ItemStack> availableStacks = copyStacks(availableStacksSupplier.get());
		Optional<BookmarkCraftingGridFill> fill = BookmarkCraftingGridFill.create(
			recipeLayout,
			targetSlotCount,
			multiplier,
			availableStacks
		);
		if (fill.isEmpty() || !canCraft(fill.get().targetStacks(), fill.get().multiplier(), availableStacks)) {
			return false;
		}

		craftedOnePacket.set(true);
		afterCraftAccepted.run();
		if (!simulate) {
			packetSender.accept(new PacketCraftingGridCraft(containerId, taskId, requestId, fill.get().multiplier(), fill.get().targetStacks()));
		}
		return true;
	}

	private static boolean craftClientFallback(
		ResourceLocation recipeUid,
		int targetSlotCount,
		Supplier<List<ItemStack>> availableStacksSupplier,
		Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> recipeLayoutResolver,
		BookmarkAutoCraftingActivator.ClientFallbackStarter clientFallbackStarter,
		AbstractContainerMenu containerMenu,
		AtomicBoolean craftedOnePacket
	) {
		if (containerMenu == null) {
			return false;
		}
		IRecipeLayoutDrawable<?> recipeLayout = recipeLayoutResolver.apply(recipeUid).orElse(null);
		if (recipeLayout == null) {
			return false;
		}

		List<ItemStack> availableStacks = copyStacks(availableStacksSupplier.get());
		Optional<BookmarkCraftingGridFill> fill = BookmarkCraftingGridFill.create(
			recipeLayout,
			targetSlotCount,
			1,
			availableStacks
		);
		if (fill.isEmpty() || !canCraft(fill.get().targetStacks(), fill.get().multiplier(), availableStacks)) {
			return false;
		}

		if (!clientFallbackStarter.start(containerMenu, fill.get().targetStacks())) {
			return false;
		}
		craftedOnePacket.set(true);
		return true;
	}

	private static boolean canCraft(List<ItemStack> targetStacks, int multiplier, List<ItemStack> availableStacks) {
		List<ItemStack> remaining = copyStacks(availableStacks);
		int craftCount = Math.max(1, multiplier);
		for (ItemStack targetStack : targetStacks) {
			if (targetStack.isEmpty()) {
				continue;
			}
			int required = targetStack.getCount() * craftCount;
			for (ItemStack availableStack : remaining) {
				if (required <= 0) {
					break;
				}
				if (availableStack.isEmpty() || !CraftingStackMatcher.matchesIngredientTemplate(targetStack, availableStack)) {
					continue;
				}
				int consumed = Math.min(required, availableStack.getCount());
				availableStack.shrink(consumed);
				required -= consumed;
			}
			if (required > 0) {
				return false;
			}
		}
		return true;
	}

	private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
		List<ItemStack> copies = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				copies.add(stack.copy());
			}
		}
		return copies;
	}

}
