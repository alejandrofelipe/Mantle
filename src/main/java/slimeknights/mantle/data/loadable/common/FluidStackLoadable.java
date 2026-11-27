package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import org.jspecify.annotations.Nullable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Loadable for a fluid stack */
@SuppressWarnings("unused")  // API
public class FluidStackLoadable {
  private FluidStackLoadable() {}

  /* reused lambdas */
  /** Getter for an item from a stack */
  private static final Function<FluidStack,Fluid> FLUID_GETTER = FluidStack::getFluid;
  /** Checks if a stack can be serialized to a primitive, ignoring count */
  private static final Predicate<FluidStack> COMPACT_COMPONENTS = FluidStack::isComponentsPatchEmpty;
  /** Maps a fluid stack that may be empty to a strictly not empty one */
  private static final BiFunction<FluidStack,ErrorFactory,FluidStack> NOT_EMPTY = (stack, error) -> {
    if (stack.isEmpty()) {
      throw error.create("FluidStack cannot be empty");
    }
    return stack;
  };

  /* fields */
  /** Field for an optional fluid */
  private static final LoadableField<Fluid,FluidStack> FLUID = Loadables.FLUID.defaultField("fluid", Fluids.EMPTY, false, FLUID_GETTER);
  /** Field for fluid stack count that allows empty */
  private static final LoadableField<Integer,FluidStack> AMOUNT = IntLoadable.FROM_ZERO.requiredField("amount", FluidStack::getAmount);
  /** Loadable for the component patch on a fluid stack, replacing the 1.20 NBT compound. */
  private static final Loadable<DataComponentPatch> PATCH_LOADABLE = new CodecLoadable<>(DataComponentPatch.CODEC);
  /** Field for fluid stack components, defaulting to an empty patch */
  private static final LoadableField<DataComponentPatch,FluidStack> PATCH = PATCH_LOADABLE.defaultField("components", DataComponentPatch.EMPTY, false, FluidStack::getComponentsPatch);


  /* Optional */
  /** Single item which may be empty with an amount of 1000 */
  public static final Loadable<FluidStack> OPTIONAL_BUCKET = fixedSize(FluidType.BUCKET_VOLUME);
  /** Loadable for a stack that may be empty with variable count */
  public static final RecordLoadable<FluidStack> OPTIONAL_STACK = RecordLoadable.create(FLUID, AMOUNT, (fluid, count) -> makeStack(fluid, count, DataComponentPatch.EMPTY));
  /** Loadable for a stack that may be empty with components and an amount of 1000 */
  public static final RecordLoadable<FluidStack> OPTIONAL_BUCKET_NBT = fixedSizeComponents(FluidType.BUCKET_VOLUME);
  /** Loadable for a stack that may be empty with variable count and components */
  public static final RecordLoadable<FluidStack> OPTIONAL_STACK_NBT = RecordLoadable.create(FLUID, AMOUNT, PATCH, FluidStackLoadable::makeStack);


  /* Required */
  /** Single item which may not be empty with an amount of 1000 */
  public static final Loadable<FluidStack> REQUIRED_BUCKET = notEmpty(OPTIONAL_BUCKET);
  /** Loadable for a stack that may not be empty with variable count */
  public static final RecordLoadable<FluidStack> REQUIRED_STACK = notEmpty(OPTIONAL_STACK);
  /** Loadable for a stack that may not be empty with components and an amount of 1000 */
  public static final RecordLoadable<FluidStack> REQUIRED_BUCKET_NBT = notEmpty(OPTIONAL_BUCKET_NBT);
  /** Loadable for a stack that may not be empty with variable count and components */
  public static final RecordLoadable<FluidStack> REQUIRED_STACK_NBT = notEmpty(OPTIONAL_STACK_NBT);


  /*
   * Templates: same JSON as the matching required stack loadables, but decode into a {@link FluidStackTemplate}, which can be
   * built before default data components are bound (recipe, loot and other datapack decoding, datagen). Use these for data;
   * the stack loadables above are for runtime data (NBT, network) where components are bound. Templates are never empty.
   */
  /** Template with variable amount */
  public static final RecordLoadable<FluidStackTemplate> REQUIRED_STACK_TEMPLATE = ComponentTemplate.STACK;
  /** Template with variable amount and components */
  public static final RecordLoadable<FluidStackTemplate> REQUIRED_STACK_NBT_TEMPLATE = ComponentTemplate.STACK_NBT;


  /* Helpers */

  /** Makes a fluid stack template from the given parameters, throwing if it would be empty */
  private static FluidStackTemplate makeTemplate(Fluid fluid, int amount, DataComponentPatch patch, ErrorFactory error) {
    if (fluid == Fluids.EMPTY || amount <= 0) {
      throw error.create("FluidStack cannot be empty");
    }
    return new FluidStackTemplate(fluid, amount, patch);
  }

  /** Makes a fluid stack from the given parameters */
  private static FluidStack makeStack(Fluid fluid, int amount, @Nullable DataComponentPatch patch) {
    if (fluid == Fluids.EMPTY || amount <= 0) {
      return FluidStack.EMPTY;
    }
    if (patch == null || patch.isEmpty()) {
      return new FluidStack(fluid, amount);
    }
    // NeoForge FluidStack has no (Fluid,int,DataComponentPatch) ctor; build from (Fluid,int) then apply the patch.
    FluidStack stack = new FluidStack(fluid, amount);
    stack.applyComponents(patch);
    return stack;
  }

  /** Creates a loadable for a stack with a single item */
  public static Loadable<FluidStack> fixedSize(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Count must be positive, received " + amount);
    }
    return Loadables.FLUID.flatXmap(fluid -> makeStack(fluid, amount, DataComponentPatch.EMPTY), FLUID_GETTER);
  }

  /** Creates a loadable for a stack with a single item, parsing components */
  public static RecordLoadable<FluidStack> fixedSizeComponents(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Amount must be positive, received " + amount);
    }
    return RecordLoadable.create(FLUID, PATCH, (fluid, patch) -> makeStack(fluid, amount, patch))
                         .compact(OPTIONAL_BUCKET, COMPACT_COMPONENTS);
  }

  /** Creates a non-empty variant of the loadable */
  public static Loadable<FluidStack> notEmpty(Loadable<FluidStack> loadable) {
    return loadable.validate(NOT_EMPTY);
  }

  /** Creates a non-empty variant of the loadable */
  public static RecordLoadable<FluidStack> notEmpty(RecordLoadable<FluidStack> loadable) {
    return loadable.validate(NOT_EMPTY);
  }

  /** Loadable for a non-empty fluid stack template, writing the same JSON as {@link #REQUIRED_STACK}/{@link #REQUIRED_STACK_NBT} */
  private enum ComponentTemplate implements RecordLoadable<FluidStackTemplate> {
    /** Ignores components */
    STACK(false),
    /** Reads components */
    STACK_NBT(true);

    private final boolean readComponents;
    ComponentTemplate(boolean readComponents) {
      this.readComponents = readComponents;
    }

    @Override
    public FluidStackTemplate deserialize(JsonObject json, TypedMap context) {
      DataComponentPatch patch = readComponents ? PATCH.get(json, context) : DataComponentPatch.EMPTY;
      return makeTemplate(FLUID.get(json, context), AMOUNT.get(json, context), patch, ErrorFactory.JSON_SYNTAX_ERROR);
    }

    @Override
    public void serialize(FluidStackTemplate template, JsonObject json) {
      // same keys and order as the fluid, amount and components fields of the stack loadables
      json.add("fluid", Loadables.FLUID.serialize(template.fluid().value()));
      json.add("amount", IntLoadable.FROM_ZERO.serialize(template.amount()));
      if (readComponents && !template.components().isEmpty()) {
        json.add("components", PATCH_LOADABLE.serialize(template.components()));
      }
    }

    @Override
    public FluidStackTemplate decode(FriendlyByteBuf buffer, TypedMap context) {
      FluidStackTemplate template = FluidStackTemplate.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer);
      if (!readComponents && !template.components().isEmpty()) {
        template = new FluidStackTemplate(template.fluid(), template.amount());
      }
      return template;
    }

    @Override
    public void encode(FriendlyByteBuf buffer, FluidStackTemplate template) {
      FluidStackTemplate.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, template);
    }
  }
}
