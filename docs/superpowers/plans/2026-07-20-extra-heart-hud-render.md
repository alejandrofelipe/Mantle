# Extra-Heart HUD Render — Re-enable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-enable Mantle's custom extra-heart HUD renderer by porting its event integration to the 1.21 `Gui`/NeoForge API (the render math is already ported; only the ForgeGui `leftHeight`/cancel integration was stubbed).

**Architecture:** Expose `Gui.leftHeight` via a Mantle access transformer; in `ExtraHeartRenderHandler.renderHealthbar`, drop the `if (true) return` short-circuit, read the real layout cursor, cancel the vanilla health layer, and advance the cursor past the custom rows. Verify at runtime with a Tinkers uitest scenario (the uitest suite lives in Tinkers; NeoForge applies Mantle's runtime AT in the Tinkers client).

**Tech Stack:** Java 21, NeoForge 21.1.234, NeoGradle (composite: Mantle `repo/` consumed by Tinkers `tinkers/`), the Mantle uitest engine + Tinkers uitest suite (screenshots via `-Dmantle.uitest=true` / `runClientUiTest`).

**Spec:** `repo/docs/superpowers/specs/2026-07-20-extra-heart-hud-render-design.md`

**Reference:** current `repo/src/main/java/slimeknights/mantle/client/ExtraHeartRenderHandler.java` (render math intact; integration stubbed at lines 87-95, 134-135, 234-235).

## Global Constraints

- **Build/test on Windows via PowerShell**, `JAVA_HOME` = `C:\Users\aleja\scoop\apps\temurin21-jdk\current` in the SAME command as gradlew. Daemon disabled → cold runs slow, not failures; background long runs. The JVM "Sharing is only supported…" stderr warning makes PowerShell report exit 1 even on `BUILD SUCCESSFUL` — **check the `BUILD SUCCESSFUL`/`FAILED` line, not the exit code.**
- **Mantle change is in `repo/`; the uitest scenario is in `tinkers/`.** Run the Mantle build from `repo`, the Tinkers build/uitest from `tinkers`.
- Access transformer uses **mojmap** names, `public-f <class> <field>`, appended to `repo/src/main/resources/META-INF/accesstransformer.cfg`.
- Conventional-commits in English. **No `Co-Authored-By` / "Generated with" trailer.**
- Cosmetic scope: **do not** change the heart-drawing math (`renderHearts`/`renderHeartRow`/`renderHeartsWithDamage`/`setOffsets`). Left column only (no `rightHeight`).

---

### Task 1: Mantle — access transformer + event integration

**Files:**
- Modify: `repo/src/main/resources/META-INF/accesstransformer.cfg`
- Modify: `repo/src/main/java/slimeknights/mantle/client/ExtraHeartRenderHandler.java`

**Interfaces:**
- Produces: a runtime-active `ExtraHeartRenderHandler` that replaces the vanilla `PLAYER_HEALTH` layer and manages `Gui.leftHeight`. Verified visually by Task 2.

- [ ] **Step 1: Add the access transformer entry**

Append to `repo/src/main/resources/META-INF/accesstransformer.cfg`:
```
# ExtraHeartRenderHandler reads/advances the vanilla HUD layout cursor to position the custom health rows
public-f net.minecraft.client.gui.Gui leftHeight
```

- [ ] **Step 2: Remove the short-circuit + stale comments**

In `ExtraHeartRenderHandler.renderHealthbar`, delete the port-comment block and short-circuit (current lines 87-95):
```java
    // TODO PORT (stage 6 book): the custom heart renderer relied on Forge's ForgeGui internals
    //  ... (whole comment block) ...
    if (true) {
      return;
    }
```

- [ ] **Step 3: Read the real layout cursor + cancel the vanilla layer**

Replace the hardcoded placeholder (current lines 134-135):
```java
    // TODO PORT (stage 6 book): leftHeight was ForgeGui's layout cursor; placeholder until re-implemented
    int leftHeight = 39;
```
with:
```java
    int leftHeight = this.mc.gui.leftHeight;
```
And cancel the vanilla health layer once committed to drawing — add right after `this.mc.getProfiler().push("health");`:
```java
    // our custom hearts replace the vanilla health layer
    event.setCanceled(true);
```
Note (implementer): `event` is `RenderGuiLayerEvent.Pre` (cancellable). Confirm `this.mc.gui.leftHeight` resolves (AT applied). The read is at the health layer's base cursor (~39) as before; its real value is what the AT unlocks.

- [ ] **Step 4: Advance the layout cursor past the custom rows**

Replace the stale trailing comment (current lines 234-235) and advance the cursor before the profiler pop. After the absorption render block (before `RenderSystem.setShaderTexture(0, ICON_VANILLA);`), add:
```java
    // advance the vanilla HUD layout cursor so armor/food sit above the rows we drew
    int rowsDrawn = 1;                                   // the health row
    if (absorb > 0 && !compactAbsorption) rowsDrawn++;   // a separate absorption row
    if (renderer != HeartRenderer.NO_MAX && maxHealth > 20) rowsDrawn++; // the max-health container overflow row
    this.mc.gui.leftHeight += rowsDrawn * ROW_HEIGHT;
```
Note (implementer): this mirrors the renderer's own row logic (`absorptionOffset`, the NO_MAX overflow). Task 2's screenshot verifies armor/food don't overlap the hearts; tune `rowsDrawn` if they do. Leave the existing `RenderSystem.setShaderTexture`/`disableBlend`/profiler-pop tail intact.

- [ ] **Step 5: Build Mantle to verify the AT applies + it compiles**

Run (background): `... gradlew.bat -p "...\repo" build`
Expected: BUILD SUCCESSFUL. (Compile confirms `this.mc.gui.leftHeight` resolves under the AT and `event.setCanceled(true)` is valid; Mantle's own unit battery unaffected.)

- [ ] **Step 6: Commit (Mantle repo)**
```
git -C "...\repo" add src/main/resources/META-INF/accesstransformer.cfg src/main/java/slimeknights/mantle/client/ExtraHeartRenderHandler.java
git -C "...\repo" commit -m "feat(client): re-enable the custom extra-heart HUD renderer"
```

---

### Task 2: Tinkers — uitest scenario + runtime verification

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java`

**Interfaces:**
- Consumes: the re-enabled Mantle handler (Task 1).

- [ ] **Step 1: Add the `extra_hearts` scenario + register it**

In `TinkerUiTestScenarios`, register it in `clientSetup` (after the existing registrations, ~line 70):
```java
    UiTestScenarios.register(new ExtraHeartsScenario());
```
And add the scenario class (mirrors `CastingPourScenario`'s no-menu, in-world capture):
```java
  /** No menu: boosts the player past 20 HP and captures the in-world HUD to verify Mantle's custom extra-heart renderer. */
  private static class ExtraHeartsScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(60, 0, 0);

    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("extra_hearts");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
      // 40 max HP -> a full extra heart row, so the custom multi-color rows render; heal to full so all show filled
      ctx.sendCommand("effect give @s minecraft:health_boost 600 4 true");
      ctx.sendCommand("effect give @s minecraft:instant_health 1 20 true");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      // no screen — the in-world HUD health bar is the capture target
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }
```
Note (implementer): confirm the effect ids/commands boost + fill health in 1.21.1 (`health_boost` amplifier 4 → +20 HP → 40 max; `instant_health` to fill). If the shot still shows 20 hearts, verify the attribute path (`attribute @s minecraft:max_health base set 40`) as a fallback and heal via `instant_health`.

- [ ] **Step 2: Run the uitest scenario**

Run (background): `... gradlew.bat -p "...\tinkers" runClientUiTest -PuitestOnly=extra_hearts`
Expected: the run completes with **0 FATAL** and the client self-exits (the now-active Mantle handler runs without crashing — this is the runtime verification of the AT + cancel + cursor integration). The suite writes `extra_hearts.png`.

- [ ] **Step 3: Review the screenshot (visual verification)**

Open the generated `extra_hearts.png` (under the Tinkers uitest screenshot output dir — see COMMANDS.md "Automated tests & screenshots"). Confirm: the health bar shows Mantle's custom hearts (from `extra_hearts.png`, not vanilla), the >20-HP hearts render in a **distinct color**, and armor/hotbar are not overlapped by the heart rows (else tune `rowsDrawn` in Task 1 Step 4 and re-run). Heart styling is an aesthetic judgment — this screenshot is the sign-off.

- [ ] **Step 4: Full build (no regression)**

Run (background): `... gradlew.bat -p "...\tinkers" build`
Expected: BUILD SUCCESSFUL; the Tinkers battery + uitest scenario compile; no regression.

- [ ] **Step 5: Commit (Tinkers repo)**
```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java src/generated/... (any committed screenshot baseline, per COMMANDS.md)
git -C "...\tinkers" commit -m "test(uitest): capture the extra-heart HUD renderer"
```

---

## Self-Review

- **Spec coverage:** AT for `Gui.leftHeight` → Task 1 Step 1; remove short-circuit + read cursor + cancel + advance → Task 1 Steps 2-4; Tinkers uitest (boost health, in-world HUD capture) → Task 2. ✓
- **Placeholder scan:** no TBD/TODO; the effect-command and `rowsDrawn` notes are implementer verification guidance (verified by the screenshot), not deferred work. The heart math is explicitly unchanged. ✓
- **Type consistency:** `this.mc.gui.leftHeight` (int field, AT-exposed) read in Step 3 and advanced in Step 4; `event.setCanceled(true)` on the existing `RenderGuiLayerEvent.Pre event`; `absorb`/`compactAbsorption`/`maxHealth`/`renderer`/`ROW_HEIGHT` are all already-computed locals/constants in `renderHealthbar`. The scenario uses the established `UiTestScenario`/`UiTestContext`/`UiTestScenarios.register` API. ✓
- **Cross-repo ordering:** Task 1 (Mantle) precedes Task 2 (Tinkers), which consumes the re-enabled handler via the composite build. ✓
