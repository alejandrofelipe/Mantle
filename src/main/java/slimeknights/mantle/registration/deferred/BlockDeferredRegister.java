package slimeknights.mantle.registration.deferred;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.block.MantleCeilingHangingSignBlock;
import slimeknights.mantle.block.MantleStandingSignBlock;
import slimeknights.mantle.block.MantleWallHangingSignBlock;
import slimeknights.mantle.block.MantleWallSignBlock;
import slimeknights.mantle.block.StrippableLogBlock;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;
import slimeknights.mantle.item.BurnableBlockItem;
import slimeknights.mantle.item.BurnableHangingSignItem;
import slimeknights.mantle.item.BurnableSignItem;
import slimeknights.mantle.item.BurnableTallBlockItem;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.EnumObject.Builder;
import slimeknights.mantle.registration.object.FenceBuildingBlockObject;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.mantle.registration.object.WallBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject.WoodVariant;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deferred register to handle registering blocks with possible item forms
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class BlockDeferredRegister extends DeferredRegisterWrapper<Block> {
  private static final BlockBehaviour.Properties POTTED_PROPS = BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY);

  protected final SynchronizedDeferredRegister<Item> itemRegister;
  public BlockDeferredRegister(String modID) {
    super(Registries.BLOCK, modID);
    this.itemRegister = SynchronizedDeferredRegister.create(Registries.ITEM, modID);
  }

  @Override
  public void register(IEventBus bus) {
    super.register(bus);
    itemRegister.register(bus);
  }


  /* Resource keys */

  /** Creates a block resource key for the given name */
  private ResourceKey<Block> blockKey(String name) {
    return ResourceKey.create(Registries.BLOCK, resource(name));
  }

  /** Creates an item resource key for the given name */
  private ResourceKey<Item> itemKey(String name) {
    return ResourceKey.create(Registries.ITEM, resource(name));
  }

  /** Creates an item resource key matching the registry name of the given (already registered) block, for its block item */
  private static ResourceKey<Item> blockItemKey(Block block) {
    return ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
  }


  /* Blocks with no items */

  /**
   * Registers a block with the block registry
   * @param name   Block ID
   * @param block  Block supplier
   * @param <B>    Block class
   * @return  Block registry object
   */
  public <B extends Block> DeferredHolder<Block, B> registerNoItem(String name, Supplier<? extends B> block) {
    return register.register(name, block);
  }

  /**
   * Registers a block with the block registry, building it from the given properties with the ID set
   * @param name     Block ID
   * @param factory  Function to create the block from the properties
   * @param props    Block properties
   * @param <B>      Block class
   * @return  Block registry object
   */
  public <B extends Block> DeferredHolder<Block, B> registerNoItem(String name, Function<BlockBehaviour.Properties, ? extends B> factory, BlockBehaviour.Properties props) {
    ResourceKey<Block> key = blockKey(name);
    return registerNoItem(name, () -> factory.apply(props.setId(key)));
  }

  /**
   * Registers a block with the block registry
   * @param name   Block ID
   * @param props  Block properties
   * @return  Block registry object
   */
  public DeferredHolder<Block, Block> registerNoItem(String name, BlockBehaviour.Properties props) {
    return registerNoItem(name, Block::new, props);
  }


  /* Block item pairs */

  /**
   * Registers a block with the block registry, using the function for the BlockItem
   * @param name   Block ID
   * @param block  Block supplier
   * @param item   Function to create a BlockItem from a Block
   * @param <B>    Block class
   * @return  Block item registry object pair
   */
  public <B extends Block> ItemObject<B> register(String name, Supplier<? extends B> block, final Function<? super B, ? extends BlockItem> item) {
    DeferredHolder<Block, B> blockObj = registerNoItem(name, block);
    // 1.21.4: caller-supplied item function owns its Properties; its setId is done at the call site
    itemRegister.register(name, () -> item.apply(blockObj.get()));
    return new ItemObject<>(blockObj);
  }

  /**
   * Registers a block with the block registry, building it from the given properties with the ID set
   * @param name     Block ID
   * @param factory  Function to create the block from the properties
   * @param props    Block properties
   * @param item     Function to create a BlockItem from a Block
   * @param <B>      Block class
   * @return  Block item registry object pair
   */
  public <B extends Block> ItemObject<B> register(String name, Function<BlockBehaviour.Properties, ? extends B> factory, BlockBehaviour.Properties props, Function<? super B, ? extends BlockItem> item) {
    ResourceKey<Block> key = blockKey(name);
    // sets the block id here; the caller-supplied item function owns the item Properties, so its setId is done at the call site
    return register(name, () -> factory.apply(props.setId(key)), item);
  }

  /**
   * Registers a block with the block registry, using the function for the BlockItem
   * @param name        Block ID
   * @param blockProps  Block supplier
   * @param item        Function to create a BlockItem from a Block
   * @return  Block item registry object pair
   */
  public ItemObject<Block> register(String name, BlockBehaviour.Properties blockProps, Function<? super Block, ? extends BlockItem> item) {
    return register(name, Block::new, blockProps, item);
  }


  /* Building */

  /**
   * Registers a building block with slabs and stairs, using a custom block
   * @param name   Block name
   * @param block  Block supplier
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public BuildingBlockObject registerBuilding(String name, Supplier<? extends Block> block, Function<? super Block, ? extends BlockItem> item) {
    ItemObject<Block> blockObj = register(name, block, item);
    return new BuildingBlockObject(
        blockObj,
        this.register(name + "_slab", () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(blockObj.get()).setId(blockKey(name + "_slab"))), item),
        this.register(name + "_stairs", () -> new StairBlock(blockObj.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(blockObj.get()).setId(blockKey(name + "_stairs"))), item));
  }

  /**
   * Registers a block with slab, and stairs
   * @param name      Name of the block
   * @param props     Block properties
   * @param item      Function to get an item from the block
   * @return  BuildingBlockObject class that returns different block types
   */
  public BuildingBlockObject registerBuilding(String name, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    ItemObject<Block> blockObj = register(name, props, item);
    return new BuildingBlockObject(blockObj,
      register(name + "_slab", SlabBlock::new, props, item),
      register(name + "_stairs", p -> new StairBlock(blockObj.get().defaultBlockState(), p), props, item)
    );
  }

  /**
   * Registers a building block with slabs, stairs and wall, using a custom block
   * @param name   Block name
   * @param block  Block supplier
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public WallBuildingBlockObject registerWallBuilding(String name, Supplier<? extends Block> block, Function<? super Block, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, block, item);
    return new WallBuildingBlockObject(obj, this.register(name + "_wall", () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(obj.get()).setId(blockKey(name + "_wall"))), item));
  }

  /**
   * Registers a block with slab, stairs, and wall
   * @param name      Name of the block
   * @param props     Block properties
   * @param item      Function to get an item from the block
   * @return  StoneBuildingBlockObject class that returns different block types
   */
  public WallBuildingBlockObject registerWallBuilding(String name, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    return new WallBuildingBlockObject(
      registerBuilding(name, props, item),
      register(name + "_wall", WallBlock::new, props, item)
    );
  }

  /**
   * Registers a building block with slabs, stairs and wall, using a custom block
   * @param name   Block name
   * @param block  Block supplier
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public FenceBuildingBlockObject registerFenceBuilding(String name, Supplier<? extends Block> block, Function<? super Block, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, block, item);
    return new FenceBuildingBlockObject(obj, this.register(name + "_fence", () -> new FenceBlock(BlockBehaviour.Properties.ofFullCopy(obj.get()).setId(blockKey(name + "_fence"))), item));
  }

  /* Building variants using a custom block class, setting the main block id (1.21.4 requirement) */

  /** Registers a building block (slab, stairs) from a custom block factory, setting the block id */
  public BuildingBlockObject registerBuilding(String name, Function<BlockBehaviour.Properties,? extends Block> factory, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    ItemObject<Block> blockObj = register(name, factory, props, item);
    return new BuildingBlockObject(
        blockObj,
        this.register(name + "_slab", () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(blockObj.get()).setId(blockKey(name + "_slab"))), item),
        this.register(name + "_stairs", () -> new StairBlock(blockObj.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(blockObj.get()).setId(blockKey(name + "_stairs"))), item));
  }

  /** Registers a building block with a wall from a custom block factory, setting the block id */
  public WallBuildingBlockObject registerWallBuilding(String name, Function<BlockBehaviour.Properties,? extends Block> factory, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, factory, props, item);
    return new WallBuildingBlockObject(obj, this.register(name + "_wall", () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(obj.get()).setId(blockKey(name + "_wall"))), item));
  }

  /** Registers a building block with a fence from a custom block factory, setting the block id */
  public FenceBuildingBlockObject registerFenceBuilding(String name, Function<BlockBehaviour.Properties,? extends Block> factory, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, factory, props, item);
    return new FenceBuildingBlockObject(obj, this.register(name + "_fence", () -> new FenceBlock(BlockBehaviour.Properties.ofFullCopy(obj.get()).setId(blockKey(name + "_fence"))), item));
  }

  /**
   * Registers a block with slab, stairs, and fence
   * @param name      Name of the block
   * @param props     Block properties
   * @param item      Function to get an item from the block
   * @return  WoodBuildingBlockObject class that returns different block types
   */
  public FenceBuildingBlockObject registerFenceBuilding(String name, BlockBehaviour.Properties props, Function<? super Block, ? extends BlockItem> item) {
    return new FenceBuildingBlockObject(
      registerBuilding(name, props, item),
      register(name + "_fence", FenceBlock::new, props, item)
    );
  }

  /**
   * Registers a new wood object
   * @param name             Name of the wood object
   * @param behaviorCreator  Logic to create the behavior
   * @param flammable        If true, this wood type is flammable
   * @return Wood object
   */
  public WoodBlockObject registerWood(String name, Function<WoodVariant,BlockBehaviour.Properties> behaviorCreator, boolean flammable) {
    BlockSetType setType = new BlockSetType(resourceName(name));
    WoodType woodType = new WoodType(resourceName(name), setType);
    BlockSetType.register(setType);
    RegistrationHelper.registerWoodType(woodType);
    Item.Properties itemProps = new Item.Properties();

    // many of these are already burnable via tags, but simplier to set them all here
    Function<Integer, Function<? super Block, ? extends BlockItem>> burnableItem;
    Function<? super Block, ? extends BlockItem> burnableTallItem;
    BiFunction<? super Block, ? super Block, ? extends BlockItem> burnableSignItem;
    BiFunction<? super Block, ? super Block, ? extends BlockItem> burnableHangingSignItem;
    Item.Properties signProps = new Item.Properties().stacksTo(16);
    // itemProps and signProps are shared across all block items below; each lambda sets its own id (matching the block it is registered under) immediately before constructing
    if (flammable) {
      burnableItem     = burnTime -> block -> new BurnableBlockItem(block, itemProps.setId(blockItemKey(block)), burnTime);
      burnableTallItem = block -> new BurnableTallBlockItem(block, itemProps.setId(blockItemKey(block)), 200);
      burnableSignItem = (standing, wall) -> new BurnableSignItem(signProps.setId(blockItemKey(standing)), standing, wall, 200);
      burnableHangingSignItem = (standing, wall) -> new BurnableHangingSignItem(signProps.setId(blockItemKey(standing)), standing, wall, 200);
    } else {
      Function<? super Block, ? extends BlockItem> defaultItemBlock = block -> new BlockItem(block, itemProps.setId(blockItemKey(block)));
      burnableItem = burnTime -> defaultItemBlock;
      burnableTallItem = block -> new DoubleHighBlockItem(block, itemProps.setId(blockItemKey(block)));
      burnableSignItem = (standing, wall) -> new SignItem(standing, wall, signProps.setId(blockItemKey(standing)));
      burnableHangingSignItem = (standing, wall) -> new HangingSignItem(standing, wall, signProps.setId(blockItemKey(standing)));
    }

    // planks
    Function<? super Block, ? extends BlockItem> burnable300 = burnableItem.apply(300);
    BlockBehaviour.Properties planksProps = behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f);
    BuildingBlockObject planks = registerBuilding(name + "_planks", planksProps, block -> burnableItem.apply(block instanceof SlabBlock ? 150 : 300).apply(block));
    ItemObject<FenceBlock> fence = register(name + "_fence", () -> new FenceBlock(Properties.ofFullCopy(planks.get()).forceSolidOn().setId(blockKey(name + "_fence"))), burnable300);
    // logs and wood
    ItemObject<RotatedPillarBlock> strippedLog = register("stripped_" + name + "_log", () -> new RotatedPillarBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f).setId(blockKey("stripped_" + name + "_log"))), burnable300);
    ItemObject<RotatedPillarBlock> strippedWood = register("stripped_" + name + "_wood", () -> new RotatedPillarBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f).setId(blockKey("stripped_" + name + "_wood"))), burnable300);
    ItemObject<RotatedPillarBlock> log = register(name + "_log", () -> new StrippableLogBlock(strippedLog, behaviorCreator.apply(WoodBlockObject.WoodVariant.LOG).instrument(NoteBlockInstrument.BASS).strength(2.0f).setId(blockKey(name + "_log"))), burnable300);
    ItemObject<RotatedPillarBlock> wood = register(name + "_wood", () -> new StrippableLogBlock(strippedWood, behaviorCreator.apply(WoodBlockObject.WoodVariant.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0f).setId(blockKey(name + "_wood"))), burnable300);

    // doors
    ItemObject<DoorBlock> door = register(name + "_door", () -> new DoorBlock(setType, behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY).setId(blockKey(name + "_door"))), burnableTallItem);
    ItemObject<TrapDoorBlock> trapdoor = register(name + "_trapdoor", () -> new TrapDoorBlock(setType, behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never).setId(blockKey(name + "_trapdoor"))), burnable300);
    ItemObject<FenceGateBlock> fenceGate = register(name + "_fence_gate", () -> new FenceGateBlock(woodType, BlockBehaviour.Properties.ofFullCopy(fence.get()).setId(blockKey(name + "_fence_gate"))), burnable300);
    // redstone
    BlockBehaviour.Properties redstoneProps = behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollission().pushReaction(PushReaction.DESTROY).strength(0.5F);
    ItemObject<PressurePlateBlock> pressurePlate = register(name + "_pressure_plate", () -> new PressurePlateBlock(setType, redstoneProps.setId(blockKey(name + "_pressure_plate"))), burnable300);
    ItemObject<ButtonBlock> button = register(name + "_button", () -> new ButtonBlock(setType, 30, redstoneProps.setId(blockKey(name + "_button"))), burnableItem.apply(100));
    // signs
    DeferredHolder<Block, StandingSignBlock> standingSign = registerNoItem(name + "_sign", () -> new MantleStandingSignBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollission().strength(1.0F).setId(blockKey(name + "_sign")), woodType));
    DeferredHolder<Block, WallSignBlock> wallSign = registerNoItem(name + "_wall_sign", () -> new MantleWallSignBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollission().strength(1.0F).overrideLootTable(standingSign.get().getLootTable()).setId(blockKey(name + "_wall_sign")), woodType));
    DeferredHolder<Block, MantleCeilingHangingSignBlock> hangingSign = registerNoItem(name + "_hanging_sign", () -> new MantleCeilingHangingSignBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollission().strength(1.0F).setId(blockKey(name + "_hanging_sign")), woodType));
    DeferredHolder<Block, MantleWallHangingSignBlock> wallHangingSign = registerNoItem(name + "_wall_hanging_sign", () -> new MantleWallHangingSignBlock(behaviorCreator.apply(WoodBlockObject.WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollission().strength(1.0F).overrideLootTable(hangingSign.get().getLootTable()).setId(blockKey(name + "_wall_hanging_sign")), woodType));
    // tell mantle to inject these into the TE
    MantleSignBlockEntity.registerSignBlock(standingSign);
    MantleSignBlockEntity.registerSignBlock(wallSign);
    MantleHangingSignBlockEntity.registerSignBlock(hangingSign);
    MantleHangingSignBlockEntity.registerSignBlock(wallHangingSign);
    // sign is included automatically in asItem of the standing sign
    this.itemRegister.register(name + "_sign", () -> burnableSignItem.apply(standingSign.get(), wallSign.get()));
    this.itemRegister.register(name + "_hanging_sign", () -> burnableHangingSignItem.apply(hangingSign.get(), wallHangingSign.get()));
    // finally, return
    return new WoodBlockObject(resource(name), woodType,
                               planks, log, strippedLog, wood, strippedWood,
                               fence, fenceGate, door, trapdoor, pressurePlate, button,
                               standingSign, wallSign, hangingSign, wallHangingSign);
  }


  /* Flower pots */

  /**
   * Registers a potted form of the given block using the vanilla pot
   * @param name  Name of the flower
   * @param block Block to put in the block
   * @return  Potted block instance
   */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(String name, Supplier<? extends Block> block) {
    DeferredHolder<Block, FlowerPotBlock> potted = registerNoItem("potted_" + name, p -> new FlowerPotBlock(() -> (FlowerPotBlock)Blocks.FLOWER_POT, block, p), POTTED_PROPS);
    ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(resource(name), potted);
    return potted;
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(DeferredHolder<Block, ? extends Block> block) {
    return registerPotted(block.getId().getPath(), block);
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(ItemObject<? extends Block> block) {
    return registerPotted(block.getId().getPath(), block);
  }


  /* Enum */

  /**
   * Registers an item with multiple variants, prefixing the name with the value name
   * @param values    Enum values to use for this block
   * @param name      Name of the block
   * @param mapper    Function to get a block for the given enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      T[] values, String name, Function<T,? extends B> mapper, Function<? super B, ? extends BlockItem> item) {
    return registerEnum(values, name, (fullName, value) -> register(fullName, () -> mapper.apply(value), item));
  }

  /**
   * Registers a block with multiple variants, suffixing the name with the value name
   * @param name      Name of the block
   * @param values    Enum values to use for this block
   * @param mapper    Function to get a block for the given enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      String name, T[] values, Function<T,? extends B> mapper, Function<? super B, ? extends BlockItem> item) {
    return registerEnum(name, values, (fullName, value) -> register(fullName, () -> mapper.apply(value), item));
  }

  /**
   * Registers a block with enum variants, but no item form
   * @param values  Enum value list
   * @param name    Suffix after value name
   * @param mapper  Function to map types to blocks
   * @param <T>  Type of enum
   * @param <B>  Type of block
   * @return  Enum object
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T, B> registerEnumNoItem(T[] values, String name, Function<T, ? extends B> mapper) {
    return registerEnum(values, name, (fullName, value) -> registerNoItem(fullName, () -> mapper.apply(value)));
  }

  /* Enum variants that set the block id (1.21.4 requires Properties.setId before construction) */

  /**
   * Registers a block with multiple variants, prefixing the name with the value name. The mapper receives the block's
   * resource key so it can call {@link BlockBehaviour.Properties#setId(ResourceKey)} before constructing the block.
   * @param values    Enum values to use for this block
   * @param name      Name of the block
   * @param mapper    Function to get a block for the given key and enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      T[] values, String name, BiFunction<ResourceKey<Block>,T,? extends B> mapper, Function<? super B, ? extends BlockItem> item) {
    return registerEnum(values, name, (fullName, value) -> register(fullName, () -> mapper.apply(blockKey(fullName), value), item));
  }

  /**
   * Registers a block with multiple variants, suffixing the name with the value name. The mapper receives the block's
   * resource key so it can call {@link BlockBehaviour.Properties#setId(ResourceKey)} before constructing the block.
   * @param name      Name of the block
   * @param values    Enum values to use for this block
   * @param mapper    Function to get a block for the given key and enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      String name, T[] values, BiFunction<ResourceKey<Block>,T,? extends B> mapper, Function<? super B, ? extends BlockItem> item) {
    return registerEnum(name, values, (fullName, value) -> register(fullName, () -> mapper.apply(blockKey(fullName), value), item));
  }

  /**
   * Registers a block with enum variants and no item form. The mapper receives the block's resource key so it can call
   * {@link BlockBehaviour.Properties#setId(ResourceKey)} before constructing the block.
   * @param values  Enum value list
   * @param name    Suffix after value name
   * @param mapper  Function to map a key and type to a block
   * @return  Enum object
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnumNoItem(T[] values, String name, BiFunction<ResourceKey<Block>,T,? extends B> mapper) {
    return registerEnum(values, name, (fullName, value) -> registerNoItem(fullName, () -> mapper.apply(blockKey(fullName), value)));
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public <T extends Enum<T> & StringRepresentable, B extends Block> EnumObject<T, FlowerPotBlock> registerPottedEnum(T[] values, String name, EnumObject<T, B> block) {
    EnumObject.Builder<T, FlowerPotBlock> builder = new Builder<>(values[0].getDeclaringClass());
    for (T value : values) {
      Supplier<? extends B> supplier = block.getSupplier(value);
      if (supplier != null) {
        builder.put(value, registerPotted(value.getSerializedName() + "_" + name, supplier));
      }
    }
    return builder.build();
  }

  /** Registers a potted form of the given blocks using the vanilla pot, automatically choosing the values based on the passed object */
  public <T extends Enum<T> & StringRepresentable, B extends Block> EnumObject<T, FlowerPotBlock> registerPottedEnum(String name, EnumObject<T, B> block) {
    Collection<Entry<T,Supplier<? extends B>>> entries = block.entries();
    EnumObject.Builder<T, FlowerPotBlock> builder = new Builder<>(entries.iterator().next().getKey().getDeclaringClass());
    for (Entry<T,Supplier<? extends B>> entry : entries) {
      T value = entry.getKey();
      builder.put(value, registerPotted(value.getSerializedName() + "_" + name, entry.getValue()));
    }
    return builder.build();
  }


  /* Metal */

  /**
   * Creates a new metal item object
   * @param name           Metal name
   * @param tagName        Name to use for tags for this block
   * @param blockSupplier  Supplier for the block
   * @param blockItem      Block item
   * @param itemProps      Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, String tagName, Supplier<Block> blockSupplier, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    ItemObject<Block> block = register(name + "_block", blockSupplier, blockItem);
    DeferredHolder<Item, Item> ingot = itemRegister.register(name + "_ingot", () -> new Item(itemProps.setId(itemKey(name + "_ingot"))));
    DeferredHolder<Item, Item> nugget = itemRegister.register(name + "_nugget", () -> new Item(itemProps.setId(itemKey(name + "_nugget"))));
    return new MetalItemObject(tagName, block, ingot, nugget);
  }

  /**
   * Creates a new metal item object
   * @param name           Metal name
   * @param blockSupplier  Supplier for the block
   * @param blockItem      Block item
   * @param itemProps      Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, Supplier<Block> blockSupplier, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    return registerMetal(name, name, blockSupplier, blockItem, itemProps);
  }

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param tagName     Name to use for tags for this block
   * @param blockProps  Properties for the block
   * @param blockItem   Block item
   * @param itemProps   Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, String tagName, BlockBehaviour.Properties blockProps, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    return registerMetal(name, tagName, () -> new Block(blockProps.setId(blockKey(name + "_block"))), blockItem, itemProps);
  }

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param blockProps  Properties for the block
   * @param blockItem   Block item
   * @param itemProps   Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, BlockBehaviour.Properties blockProps, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    return registerMetal(name, name, blockProps, blockItem, itemProps);
  }

  /**
   * Creates a new metal item object using a custom block class, setting the block id (1.21.4 requirement)
   * @param name          Metal name
   * @param tagName       Name to use for tags for this block
   * @param blockFactory  Function to create the block from the properties
   * @param blockProps    Properties for the block
   * @param blockItem     Block item
   * @param itemProps     Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, String tagName, Function<BlockBehaviour.Properties,? extends Block> blockFactory, BlockBehaviour.Properties blockProps, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    return registerMetal(name, tagName, () -> blockFactory.apply(blockProps.setId(blockKey(name + "_block"))), blockItem, itemProps);
  }

  /**
   * Creates a new metal item object using a custom block class, setting the block id (1.21.4 requirement)
   * @param name          Metal name
   * @param blockFactory  Function to create the block from the properties
   * @param blockProps    Properties for the block
   * @param blockItem     Block item
   * @param itemProps     Properties for the item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, Function<BlockBehaviour.Properties,? extends Block> blockFactory, BlockBehaviour.Properties blockProps, Function<Block,? extends BlockItem> blockItem, Item.Properties itemProps) {
    return registerMetal(name, name, blockFactory, blockProps, blockItem, itemProps);
  }
}
