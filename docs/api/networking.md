# Networking

A thin wrapper around NeoForge's `CustomPacketPayload` system that gives each mod a versioned channel plus convenient "send" helpers (server, player, around-a-position, tracking-an-entity).

## What it's for

NeoForge 1.21.1 replaced the old `SimpleChannel` networking with the vanilla **payload** system: every packet is now a `CustomPacketPayload` with an associated `StreamCodec`, registered on `RegisterPayloadHandlersEvent`. Mantle's `NetworkWrapper` keeps that boilerplate small for a consuming mod — you create one wrapper per channel, define each packet as a `record`, and the wrapper hands you typed `sendTo* ` helpers backed by `PacketDistributor` so you never touch the distributor directly. The `ISimplePacket` / `IThreadsafePacket` interfaces standardize the receive side so the registrar can call `payload.handle(context)` uniformly.

## Key types

| Type | Purpose |
| --- | --- |
| `slimeknights.mantle.network.NetworkWrapper` | One channel: holds the channel `ResourceLocation` + version string and exposes the send helpers. |
| `slimeknights.mantle.network.MantleNetwork` | Mantle's own channel instance + its `registerPayloads(RegisterPayloadHandlersEvent)` — a worked example to copy. |
| `slimeknights.mantle.network.packet.ISimplePacket` | Base packet interface: extends `CustomPacketPayload` and adds `handle(IPayloadContext)`. |
| `slimeknights.mantle.network.packet.IThreadsafePacket` | `ISimplePacket` whose `handle` is pre-wrapped in `IPayloadContext.enqueueWork(...)`; you implement `handleThreadsafe`. |
| `slimeknights.mantle.network.packet.SwingArmPacket` | Reference packet (record) showing `TYPE` + `STREAM_CODEC` + `handleThreadsafe`. |

## How to use

### 1. Create a `NetworkWrapper`

Make one static instance in your mod, giving it a unique channel `ResourceLocation` and a **version string**. Bump the version whenever your packet wire format changes so mismatched clients/servers are rejected.

```java
public final class MyModNetwork {
  // channel id = <yourmodid>:network, protocol version "1"
  public static final NetworkWrapper INSTANCE =
    new NetworkWrapper(ResourceLocation.fromNamespaceAndPath(MyMod.MOD_ID, "network"), "1");
}
```

> The no-version constructor `new NetworkWrapper(channelName)` exists but is `@Deprecated` — always pass a version.

### 2. Define a packet as a record

A packet is a `record` implementing `ISimplePacket` (or `IThreadsafePacket` for automatic main-thread dispatch). It needs three things: a `CustomPacketPayload.Type`, a `StreamCodec`, and the receive logic. The packet's **namespace comes from the `Type`'s `ResourceLocation`** — not from the channel.

`IThreadsafePacket` is the common choice: its default `handle` already calls `context.enqueueWork(...)`, so your `handleThreadsafe` runs on the game thread where touching the world/player is safe.

```java
public record ShowMessagePacket(int entityId, Component message) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<ShowMessagePacket> TYPE =
    new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MyMod.MOD_ID, "show_message"));

  // StreamCodec.composite pairs each codec with its record accessor, then the constructor reference
  public static final StreamCodec<RegistryFriendlyByteBuf, ShowMessagePacket> STREAM_CODEC =
    StreamCodec.composite(
      ByteBufCodecs.VAR_INT,                 ShowMessagePacket::entityId,
      ComponentSerialization.STREAM_CODEC,   ShowMessagePacket::message,
      ShowMessagePacket::new);

  @Override
  public CustomPacketPayload.Type<ShowMessagePacket> type() {
    return TYPE;
  }

  // runs on the main thread thanks to IThreadsafePacket
  @Override
  public void handleThreadsafe(IPayloadContext context) {
    Player player = context.player(); // receiver; client player for play-to-client
    player.displayClientMessage(message(), false);
  }
}
```

If you need the raw context behavior (e.g. you handle threading yourself), implement `ISimplePacket` directly and override `handle(IPayloadContext)` instead of `handleThreadsafe`.

For an enum field, use NeoForge's helper as `SwingArmPacket` does:

```java
NeoForgeStreamCodecs.enumCodec(InteractionHand.class)
```

### 3. Register payloads on `RegisterPayloadHandlersEvent`

Add a mod-bus listener (note: this is the **mod** event bus, not the game bus). Get a `PayloadRegistrar` from the event — its argument is the **protocol version string** (pass your wrapper's `version` field), and call `playToClient` / `playToServer` per packet with its `TYPE`, `STREAM_CODEC`, and a handler that forwards to `payload.handle(context)`.

```java
// in your mod constructor:  bus.addListener(RegisterPayloadHandlersEvent.class, MyModNetwork::registerPayloads);

public static void registerPayloads(RegisterPayloadHandlersEvent event) {
  PayloadRegistrar registrar = event.registrar(MyModNetwork.INSTANCE.version);

  // S2C: handled on the client
  registrar.playToClient(ShowMessagePacket.TYPE, ShowMessagePacket.STREAM_CODEC,
    (payload, context) -> payload.handle(context));

  // C2S: handled on the server
  registrar.playToServer(SomeActionPacket.TYPE, SomeActionPacket.STREAM_CODEC,
    (payload, context) -> payload.handle(context));
}
```

This mirrors `MantleNetwork.registerPayloads`, which registers Mantle's book/lectern/swing-arm packets the same way.

### 4. Send packets via the wrapper helpers

`NetworkWrapper` exposes send methods that wrap `PacketDistributor`. Pick the helper matching the audience; `sendTo*` methods skip `FakePlayer` automatically where relevant.

```java
NetworkWrapper net = MyModNetwork.INSTANCE;

// client -> server
net.sendToServer(new SomeActionPacket(...));

// server -> one player
net.sendTo(new ShowMessagePacket(entity.getId(), text), serverPlayer);

// server -> everyone watching a block position (e.g. a block update effect)
net.sendToClientsAround(payload, serverLevel, blockPos);

// server -> everyone tracking an entity (and the entity itself if it's a player)
net.sendToTrackingAndSelf(new SwingArmPacket(entity, InteractionHand.MAIN_HAND), entity);

// server -> everyone tracking an entity (excluding the entity)
net.sendToTracking(payload, entity);

// send a raw vanilla packet to one entity
net.sendVanillaPacket(vanillaPacket, player);
```

The available send helpers and their `PacketDistributor` backing:

| Method | Distributor call |
| --- | --- |
| `sendToServer(CustomPacketPayload)` | `PacketDistributor.sendToServer` |
| `sendTo(CustomPacketPayload, Player)` / `sendTo(..., ServerPlayer)` | `PacketDistributor.sendToPlayer` |
| `sendToClientsAround(payload, ServerLevel, BlockPos)` | `PacketDistributor.sendToPlayersTrackingChunk` |
| `sendToTrackingAndSelf(payload, Entity)` | `PacketDistributor.sendToPlayersTrackingEntityAndSelf` |
| `sendToTracking(payload, Entity)` | `PacketDistributor.sendToPlayersTrackingEntity` |
| `sendVanillaPacket(Packet<?>, Entity)` | direct `ServerPlayer.connection.send` |

## Datagen

Networking has no data providers or builders — packets are pure code (record + `Type` + `StreamCodec`), registered at runtime on `RegisterPayloadHandlersEvent`. There is nothing to emit during datagen.

## NeoForge 1.21.1 notes

- The old Forge `SimpleChannel` (with `registerMessage`/`INSTANCE.send(PacketDistributor.X.with(...), msg)`) is gone. Every packet is now a `CustomPacketPayload` carrying a `CustomPacketPayload.Type` and a `StreamCodec`, registered via the `PayloadRegistrar` on `RegisterPayloadHandlersEvent`.
- `event.registrar(String)` takes the **protocol version**, not a namespace. The payload's namespace lives on each packet's `CustomPacketPayload.Type` `ResourceLocation`. `MantleNetwork` passes `INSTANCE.version`.
- Receive handlers get an `IPayloadContext` (NeoForge), not the old `NetworkEvent.Context Supplier`. Use `context.enqueueWork(...)` for main-thread work (handled for you by `IThreadsafePacket`) and `context.player()` for the receiving player.
- Encoding moved from manual `FriendlyByteBuf` read/write methods to `StreamCodec` over `RegistryFriendlyByteBuf`; build composites with `StreamCodec.composite` and use `ByteBufCodecs` / `NeoForgeStreamCodecs` (e.g. `enumCodec`) for fields.
- Sending no longer uses `SimpleChannel.send`; it goes through static `PacketDistributor` methods, which `NetworkWrapper`'s helpers wrap.

See the [migration guide](../migration/1.20-to-1.21.1.md#8-networking) for details.
