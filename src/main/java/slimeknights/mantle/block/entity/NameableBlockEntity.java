package slimeknights.mantle.block.entity;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Extension of tile entity to make it namable
 */
public abstract class NameableBlockEntity extends MantleBlockEntity implements INameableMenuProvider {
	private static final String TAG_CUSTOM_NAME = "CustomName";

	/**
	 * Codec storing the component as a JSON string, the format used by Mantle before 1.21.8 ({@code Component.Serializer.toJson}), so existing worlds keep loading.
	 * When decoding, falls back to the vanilla component format if the tag is not a string.
	 */
	private static final Codec<Component> JSON_STRING_CODEC = new Codec<>() {
		@Override
		public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
			DynamicOps<JsonElement> jsonOps = ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE;
			return ComponentSerialization.CODEC.encodeStart(jsonOps, input).flatMap(json -> ops.mergeToPrimitive(prefix, ops.createString(json.toString())));
		}

		@Override
		public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<String> string = ops.getStringValue(input);
			if (string.result().isEmpty()) {
				return ComponentSerialization.CODEC.decode(ops, input);
			}
			DynamicOps<JsonElement> jsonOps = ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE;
			try {
				JsonElement json = JsonParser.parseString(string.result().get());
				return ComponentSerialization.CODEC.parse(jsonOps, json).map(component -> Pair.of(component, input));
			} catch (JsonParseException e) {
				return DataResult.error(e::getMessage);
			}
		}
	};

	/** Default title for this tile entity */
	@Getter
	private final Component defaultName;
	/** Title set to this tile entity */
	@Getter @Setter
	private Component customName;

	public NameableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Component defaultTitle) {
		super(type, pos, state);
		this.defaultName = defaultTitle;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(TAG_CUSTOM_NAME, JSON_STRING_CODEC).ifPresent(name -> this.customName = name);
	}

	@Override
	protected void saveSynced(ValueOutput output) {
		super.saveSynced(output);
		if (this.hasCustomName()) {
			output.store(TAG_CUSTOM_NAME, JSON_STRING_CODEC, this.customName);
		}
	}
}
