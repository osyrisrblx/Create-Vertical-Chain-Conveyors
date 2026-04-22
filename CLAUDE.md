# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this mod does

NeoForge mod for Minecraft 1.21.1 that extends Create 6.0.10's Chain Conveyor to support all 6 mounting directions (floor, ceiling, and the 4 walls), instead of just floor-mounted. It does this entirely via Mixins on Create's existing `chain_conveyor` block — no new block is registered, so one item and all existing Create logistics (packages, frogports, routing) work transparently across mixed orientations.

- Mod ID: `verticalchainconveyors`
- Package: `com.osyris.verticalchainconveyors`
- Target: Create 6.0.x · NeoForge 21.1.x · MC 1.21.1 · Java 21

## Build

```sh
./scripts/gradle-win-java.sh build
```

The helper stages the repo into a Windows-local workspace (under `%LOCALAPPDATA%\Temp\vcc-build\workspace`), runs `gradlew.bat` there with Java 21, and syncs `build/` back to the repo when the build succeeds. This avoids NeoForge's NFRT / `binarypatcher` stalls when reading/writing through `\\wsl.localhost\…`. Local Java paths, cache paths, workspace paths, and deploy destinations belong in `CLAUDE.local.md`, which is intentionally gitignored.

Output: `build/libs/verticalchainconveyors-1.21.1-1.0.0.jar`

After a successful build, deploy to the local runtime locations listed in `CLAUDE.local.md` if that file exists.

## Testing loop

Use the focused JUnit suite before the full build when changing conveyor geometry, routing, selection previews, or package orientation. It runs the extracted math in `VCCChainConveyorMath` without launching Minecraft, so it is the fastest way to catch bad axis assumptions.

**Fast tests** (run from repo root):

```sh
./scripts/gradle-win-java.sh test
```

Current fast coverage lives in `src/test/java/com/osyris/verticalchainconveyors/VCCChainConveyorMathTest.java` and `src/test/java/com/osyris/verticalchainconveyors/VCCChainConveyorMovementTest.java`. It checks:

- wheel-centre bias for all six `FACING` values
- connection tangent points staying in the correct source and target wheel planes and radius
- mixed-facing connection endpoints and target loop-entry angles
- loop and travel package positions staying in the expected wheel/connection path
- axis-relative connection validation for axial and too-steep candidate links
- connection preview normals for selection path lines
- reversed tangent-side math
- fallback loop exit choice for recovery after a package has orbited without reaching a route
- routed package movement over multiple simulated loop ticks
- routed packages waiting for their real exit instead of taking earlier fallback exits
- backed-up routed targets holding packages instead of rerouting them incorrectly
- forced recovery only after a full loop, with incoming-connection return as the last resort
- speed reversal for packages already travelling on mixed-facing connections
- high-speed loop ticks that cross multiple possible exits
- chain point sampling for drops/destroy particles on mixed and axis-parallel orientations
- horizontal yaw stabilization for vertical loop motion
- axis-aware selection outline points offsetting inward from every mounting face

When fixing a geometry bug, first move any reusable world-space math into `VCCChainConveyorMath`, add or update a JUnit case that reproduces the axis/orientation failure, then run `test`. After the fast suite passes, run the full `build` command above; `build` includes the JUnit suite. After a successful build, deploy the jar to the configured local Minecraft locations if available.

Manual in-client checks live in `CLIENT_SMOKE_TESTS.md`. Use that checklist after deploying a jar, especially for rendering/culling, selection previews, live package movement, speed reversal, and Frogport interactions that unit tests cannot fully prove.

## Architecture

### The FACING property replaces Create's hardcoded horizontal-only logic

Create's `ChainConveyorBlock` hardcodes `getRotationAxis() == Axis.Y` and its placement/connection handlers assume a horizontal wheel in the XZ plane. This mod injects a `BlockStateProperties.FACING` property onto the block and rewires the handful of methods that depend on wheel orientation. The rest of Create (packages, chain rendering, network logic) doesn't care about the wheel axis — they use `ConnectionStats.start`/`end` world-space vectors that we now compute per-axis.

`FACING` is the direction the block's **bottom (mounting face) points** — i.e., `ctx.getClickedFace().getOpposite()`. So `facing=DOWN` means mounted on floor (the Create default), `facing=NORTH` means mounted on a wall whose face points south, etc.

### How the wheel plane is chosen

For each facing, the wheel spins around `facing.getAxis()` and sits in the plane perpendicular to it. The wheel centre is biased toward the mounting face by 0.125 blocks (Create's original 6/16 offset from the floor becomes a generic `0.5 + facing.getStepVec() * 0.125`).

The rotation conventions use a right-hand-rule cyclic pattern. For a connection offset `diff`:

| Axis | Rotation around | atan2 args | baseVec (length 1.25) |
|------|-----------------|------------|------------------------|
| Y    | Y               | `(x, z)`   | `(0, 0, 1.25)`         |
| X    | X               | `(z, y)`   | `(0, 1.25, 0)`         |
| Z    | Z               | `(y, x)`   | `(1.25, 0, 0)`         |

All three mixins that care about geometry (`ChainConveyorBlockEntityMixin`, `ChainConveyorShapeBBMixin`, `ChainConveyorVisualMixin`) use the **same** cyclic pattern — keep it consistent when editing.

### The mixins

All mixins live in `src/main/java/com/osyris/verticalchainconveyors/mixin/` and are declared in `src/main/resources/verticalchainconveyors.mixins.json`. Mixins that target Create classes use `remap = false` because Create's code is already under its own (non-mapped) names. Mixins targeting Minecraft classes use `remap = true`; NeoForge runs on Mojmap names at runtime so the literal Mojmap names (e.g., `createBlockStateDefinition`, `getStateForPlacement`, `getShape`, `getInteractionShape`) match directly.

- **`ChainConveyorBlockStateMixin`** — injects `FACING` into the generic `Block.createBlockStateDefinition` on the declaring class (we can't `@Shadow` an inherited method), guarded by an `instanceof ChainConveyorBlock` check.
- **`ChainConveyorBlockMixin`** — overrides `getRotationAxis`, `getStateForPlacement`, and shape methods on `ChainConveyorBlock` itself.
- **`ChainConveyorBlockEntityMixin`** — replaces `calculateConnectionStats` for non-DOWN facings and pushes the current facing to `AxisContext` before `updateChainShapes` runs.
- **`ChainConveyorShapeBBMixin`** — reads facing from `AxisContext` in its constructor, then overrides `getChainPosition` and `getVec` for package-on-loop positioning.
- **`ChainConveyorConnectionHandlerMixin`** — replaces chain-placement validation (planar distance perpendicular to axis, axial displacement for the 45° slope check) and replaces the highlight outline (rings in the wheel plane rather than XZ).
- **`ChainConveyorVisualMixin` (client-only)** — rotates the wheel instance and rebuilds each chain-guide instance after Create's `setupGuards` runs. Needs `VCCVisualAccessor` (for `blockState`, `blockEntity`, `visualPos` on `AbstractBlockEntityVisual`) and `VCCChainVisualAccessor` (for the private `guards` list on `ChainConveyorVisual`).

### The AxisContext bridge

`ChainConveyorBB` is an inner class whose constructor takes only a `Vec3 center`, with no way to know which facing produced it. Rather than inflating its signature, `ChainConveyorBlockEntityMixin.updateChainShapes` pushes the block's `Direction` into `AxisContext.CURRENT_FACING` (a `ThreadLocal<Direction>`) just before the BB is constructed, and `ChainConveyorShapeBBMixin`'s `<init>` injector reads it. The initial value is `Direction.DOWN` so any unrelated code path that triggers a BB construction behaves like vanilla Create.

### Resource overrides

`src/main/resources/assets/create/blockstates/chain_conveyor.json` overrides Create's single-variant blockstate with a 6-variant file keyed on `facing=…`. Rotations follow Minecraft's model-rotation convention so that the block's darker "bottom" face always ends up on the mounting surface.

## Gotchas when editing

- **Mixin `@Shadow` cannot see inherited members.** If a method comes from a parent class (e.g., `Block.createBlockStateDefinition` inherited by `ChainConveyorBlock`), target the method on the declaring class by its Mojmap name and use `(Parent)(Object)this` casts instead of `@Shadow`.
- **`@Shadow` on a non-null `final` field silently nullifies it** in the bytecode-merged class. This killed `ChainConveyorVisual.guards` — use an `@Accessor` interface instead.
- **`facing=DOWN` paths must short-circuit to Create's original code.** Every geometry mixin checks `if (facing == Direction.DOWN) return;` before running our code — this keeps floor-mounted behaviour bit-identical to vanilla Create so existing builds are unaffected.
- **`getRotationAxis` is called by Flywheel's `RotatingInstance.setup`**, which means our override drives the shaft's rotation axis but NOT its positive/negative direction. The shaft model is only axis-oriented; distinguishing `UP` from `DOWN` (etc.) requires a separate visual flip.
- **VoxelShape coordinates outside `[0, 1]` are legal** and are used for the wheel's overhang.
- **`ChainConveyorConnectionHandler` is client-only** — `Minecraft.getInstance()` is safe there.

## Cross-reference: Create source

When a local Create source checkout is available, compare against the matching Create 6.0.x branch. Key files when debugging geometry:

- `content/kinetics/chainConveyor/ChainConveyorBlock.java` — placement/shape (what `ChainConveyorBlockMixin` rewrites)
- `content/kinetics/chainConveyor/ChainConveyorBlockEntity.java` — `calculateConnectionStats`, `updateChainShapes`
- `content/kinetics/chainConveyor/ChainConveyorShape.java` — `ChainConveyorBB` inner class
- `content/kinetics/chainConveyor/ChainConveyorConnectionHandler.java` — `validateAndConnect`, `highlightConveyor`
- `content/kinetics/chainConveyor/ChainConveyorVisual.java` — `setupGuards` (wheel + guard instance setup)
- `content/kinetics/base/SingleAxisRotatingVisual.java` — parent class of `ChainConveyorVisual`; renders the rotating shaft via `rotatingModel`

When writing or changing mixin method descriptors, verify the actual runtime signature against `libs/create-1.21.1-6.0.10.jar` rather than relying only on the checked-out source. For example the runtime NeoForge jar's `ChainConveyorBlockEntity.read` signature is `read(CompoundTag, HolderLookup.Provider, boolean)` — the `HolderLookup.Provider` was added in 1.20.5+.

```sh
javap \
  -classpath libs/create-1.21.1-6.0.10.jar \
  -p com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity
```

## Dependencies

`libs/` contains Create 6.0.10 and its embedded transitive JARs (Flywheel, Ponder, Registrate) extracted from `create-1.21.1-6.0.10.jar`. These are `compileOnly` — at runtime the server/client has Create installed, so the mod only ships its own thin bytecode.
