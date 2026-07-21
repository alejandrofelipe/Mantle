# Extra-Heart HUD Render — Re-enable (Design)

Date: 2026-07-20 · Status: **approved by user (brainstorm)** · Backlog item **E** (Wave 3 / P3 polish),
Mantle repo. Basis: `2026-07-18-deferred-backlog-roadmap.md`.

## Context

Mantle ships a custom health-bar renderer (`slimeknights.mantle.client.ExtraHeartRenderHandler`) that draws hearts
from its own `extra_hearts.png` with **per-row color variants** — so a player boosted past 20 HP (Tinkers health
modifiers) sees the extra rows in distinct colors instead of vanilla's overlapping same-color stack, plus
poison/wither/freeze/absorption/hardcore variants, damage flash, regen bounce and low-health wiggle.

The render math is **already ported** to 1.21 (`GuiGraphics.blit`, compiles), but the event handler
**short-circuits** — `renderHealthbar` returns at `ExtraHeartRenderHandler.java:93` (`if (true) return;`). The port
comment (lines 87-95) records why: the renderer relied on Forge's `ForgeGui` internals — the `leftHeight` layout
cursor, `setupOverlayRenderState`, `shouldDrawSurvivalElements`, and the cancellable `RenderGuiOverlayEvent.Pre/Post`
pair — all removed in the 1.21 `Gui`/`LayeredDraw` rewrite. NeoForge's `RenderGuiLayerEvent.Pre` (already subscribed)
is cancellable but does not expose the `leftHeight` cursor. So the handler was disabled so vanilla hearts render.

## Goal

Re-enable the custom renderer by porting its event integration to the 1.21 `Gui`/NeoForge API — **no change to the
render math**. The feature is cosmetic (vanilla already draws functional hearts); this restores Mantle's styling.

## Scope

- **Mantle** (`repo/`): the access transformer + `ExtraHeartRenderHandler` event integration.
- **Tinkers** (`tinkers/`): one uitest scenario to verify it (the uitest suite/runner lives in Tinkers — Mantle is
  only the engine; NeoForge applies Mantle's runtime AT in the Tinkers client, so `Gui.leftHeight` is accessible
  there).

Out of scope: re-authoring the heart-drawing logic (`renderHearts`/`renderHeartRow`/`renderHeartsWithDamage`/
`setOffsets` are already ported); the `rightHeight` column (the handler only manages left-column health/absorption);
any other HUD element.

## Design

### 1. Access transformer — expose `Gui.leftHeight`

Add to `repo/src/main/resources/META-INF/accesstransformer.cfg` (mojmap `public-f`, mirroring the existing entries):
```
# ExtraHeartRenderHandler reads the vanilla HUD layout cursor to position and advance the health rows
public-f net.minecraft.client.gui.Gui leftHeight
```
NeoForge applies this at load time from Mantle's jar, so it is in effect in any client loading Mantle (Mantle's own
dev client and the Tinkers client alike).

### 2. `ExtraHeartRenderHandler.renderHealthbar` — event integration

- **Remove** the `if (true) { return; }` short-circuit (lines 93-95) and the two stale `TODO PORT` comment blocks.
- **Read** the real cursor: replace the hardcoded `int leftHeight = 39;` (line 135) with `int leftHeight =
  this.mc.gui.leftHeight;` (now accessible via the AT). `top = window.getGuiScaledHeight() - leftHeight` as before.
- **Cancel** the vanilla health layer so the custom hearts replace it: `event.setCanceled(true);` (valid on
  `RenderGuiLayerEvent.Pre`, per the code's own note at the old line 234). Do this once we commit to drawing (after
  the `hideGui`/`Player` guards pass).
- **Advance** the cursor so downstream left-column elements (armor, food) sit above the custom hearts, which may span
  multiple rows for >20 HP: after drawing, `this.mc.gui.leftHeight += <rows drawn - 1> * ROW_HEIGHT` (the base row is
  already accounted for by vanilla's reset; the plan pins the exact row count from `showHearts`/absorption/max-health
  rows the renderer computed). Mirror vanilla's advancement magnitude so armor doesn't overlap.

The existing body (health/absorption/max-health rows, damage, regen, wiggle) is unchanged.

### 3. Testing — a Tinkers uitest scenario

Add an `extra_hearts` scenario to the Tinkers uitest suite (reusing the established runner + `-PuitestOnly` filter):
- `prepare`: via `ctx.runOnServer`/`ctx.sendCommand`, set the player's max health above 20 (e.g.
  `attribute @s minecraft:max_health base set 30` and heal to full) so the extra rows render.
- `open`: no screen — aim the camera for the in-world capture (`UiTestScenario.open` supports this).
- The framework captures the in-world HUD → the PNG shows the custom multi-color extra hearts. A clean run (no crash
  in the handler) verifies the AT + cancel + cursor integration works at runtime; the screenshot is for aesthetic
  review (heart styling is a visual judgment).

This is the automatable verification for HUD rendering. Final aesthetic sign-off is the screenshot (and, if wanted, a
`runClient` smoke with a boosted health bar).

## Verification / open items for the plan

- Confirm the field is `net.minecraft.client.gui.Gui#leftHeight` (mojmap) and the `public-f` AT applies (build with
  the AT; the field access compiles + resolves at runtime).
- Confirm `event.setCanceled(true)` on `RenderGuiLayerEvent.Pre(PLAYER_HEALTH)` suppresses the vanilla health layer,
  and pin the exact `leftHeight` advancement (row count) so armor/food don't overlap the custom hearts.
- Confirm the Tinkers uitest framework captures the in-world HUD (not just screens) and that `sendCommand`/attribute
  reliably boosts max health before capture; pin the scenario against the existing scenario examples.

None are design risks — the AT pattern, the cancellable event, and the uitest framework all exist; these are
implementation details the plan pins with verified code + the uitest screenshot.

## Non-goals

- No re-authoring of heart-drawing math. No new Mantle-standalone uitest task (reuse Tinkers' runner). No other Wave 3
  items (F dormant/YAGNI, G cleanups) — separate.
