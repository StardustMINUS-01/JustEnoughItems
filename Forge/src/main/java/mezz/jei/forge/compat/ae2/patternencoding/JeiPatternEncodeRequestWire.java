package mezz.jei.forge.compat.ae2.patternencoding;

import appeng.api.stacks.GenericStack;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record JeiPatternEncodeRequestWire(
	ResourceLocation recipeTypeUid,
	ResourceLocation recipeUid,
	JeiPatternEncodeMode mode,
	List<@Nullable GenericStack> sparseInputs,
	List<@Nullable GenericStack> sparseOutputs,
	List<JeiPatternCatalystWire> catalysts,
	List<@Nullable GenericStack> canonicalInputGuides,
	@Nullable ResourceLocation canonicalRecipeId,
	boolean allowSubstitution,
	boolean allowFluidSubstitution
) {
	public static final int MAX_STACKS_PER_SIDE = 128;
	public static final int MAX_REQUESTS = 256;

	public JeiPatternEncodeRequestWire {
		sparseInputs = Collections.unmodifiableList(new ArrayList<>(sparseInputs));
		sparseOutputs = Collections.unmodifiableList(new ArrayList<>(sparseOutputs));
		catalysts = Collections.unmodifiableList(new ArrayList<>(catalysts));
		canonicalInputGuides = Collections.unmodifiableList(new ArrayList<>(canonicalInputGuides));
	}

	public static void write(FriendlyByteBuf buffer, JeiPatternEncodeRequestWire request) {
		buffer.writeResourceLocation(request.recipeTypeUid());
		buffer.writeResourceLocation(request.recipeUid());
		buffer.writeEnum(request.mode());
		writeGenericStackList(buffer, request.sparseInputs());
		writeGenericStackList(buffer, request.sparseOutputs());
		if (request.catalysts().size() > MAX_STACKS_PER_SIDE) {
			throw new IllegalArgumentException("Too many catalysts: " + request.catalysts().size());
		}
		buffer.writeVarInt(request.catalysts().size());
		for (JeiPatternCatalystWire catalyst : request.catalysts()) {
			JeiPatternCatalystWire.write(buffer, catalyst);
		}
		writeGenericStackList(buffer, request.canonicalInputGuides());
		buffer.writeBoolean(request.canonicalRecipeId() != null);
		if (request.canonicalRecipeId() != null) {
			buffer.writeResourceLocation(request.canonicalRecipeId());
		}
		buffer.writeBoolean(request.allowSubstitution());
		buffer.writeBoolean(request.allowFluidSubstitution());
	}

	public static JeiPatternEncodeRequestWire read(FriendlyByteBuf buffer) {
		ResourceLocation recipeTypeUid = buffer.readResourceLocation();
		ResourceLocation recipeUid = buffer.readResourceLocation();
		JeiPatternEncodeMode mode = buffer.readEnum(JeiPatternEncodeMode.class);
		List<@Nullable GenericStack> sparseInputs = readGenericStackList(buffer);
		List<@Nullable GenericStack> sparseOutputs = readGenericStackList(buffer);
		int catalystCount = buffer.readVarInt();
		if (catalystCount < 0 || catalystCount > MAX_STACKS_PER_SIDE) {
			throw new IllegalArgumentException("Invalid catalyst count: " + catalystCount);
		}
		List<JeiPatternCatalystWire> catalysts = new ArrayList<>(catalystCount);
		for (int i = 0; i < catalystCount; i++) {
			catalysts.add(JeiPatternCatalystWire.read(buffer));
		}
		List<@Nullable GenericStack> canonicalInputGuides = readGenericStackList(buffer);
		@Nullable ResourceLocation canonicalRecipeId = buffer.readBoolean() ? buffer.readResourceLocation() : null;
		boolean allowSubstitution = buffer.readBoolean();
		boolean allowFluidSubstitution = buffer.readBoolean();
		return new JeiPatternEncodeRequestWire(
			recipeTypeUid,
			recipeUid,
			mode,
			sparseInputs,
			sparseOutputs,
			catalysts,
			canonicalInputGuides,
			canonicalRecipeId,
			allowSubstitution,
			allowFluidSubstitution
		);
	}

	private static void writeGenericStackList(FriendlyByteBuf buffer, List<@Nullable GenericStack> stacks) {
		if (stacks.size() > MAX_STACKS_PER_SIDE) {
			throw new IllegalArgumentException("Too many generic stacks: " + stacks.size());
		}
		buffer.writeVarInt(stacks.size());
		for (GenericStack stack : stacks) {
			GenericStack.writeBuffer(stack, buffer);
		}
	}

	private static List<@Nullable GenericStack> readGenericStackList(FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > MAX_STACKS_PER_SIDE) {
			throw new IllegalArgumentException("Invalid generic stack list size: " + size);
		}
		List<@Nullable GenericStack> stacks = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			stacks.add(GenericStack.readBuffer(buffer));
		}
		return Collections.unmodifiableList(stacks);
	}
}
