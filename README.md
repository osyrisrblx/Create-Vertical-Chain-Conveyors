# Vertical Chain Conveyors

A Forge addon for Create that lets Chain Conveyors mount on floors, ceilings, and walls.

Vanilla Create Chain Conveyors are floor-mounted only. This mod extends Create's existing `chain_conveyor` block so the same item can be placed against any face and still participates in Create logistics: package movement, routing, chain rendering, selection previews, and ports.

## Features

- Place Chain Conveyors on all six mounting faces.
- Use the existing Create Chain Conveyor block and item; this mod does not register a replacement block.
- Render wheels, chains, selection outlines, and path preview lines in the mounted conveyor plane.
- Move packages through vertical and wall-mounted chains with axis-aware loop and travel math.
- Preserve connected chain state across world reloads.
- Keep floor-mounted Create behavior as close to vanilla as possible.

## Current Behavior

Chain links can be made between conveyors that face the same direction. For example, two wall-mounted conveyors on the same wall orientation can connect to each other, and two ceiling-mounted conveyors can connect to each other.

Connections between different mounting directions are intentionally rejected for now. This keeps the connection math, package routing, and visual behavior predictable while still supporting vertical conveyor layouts.

## Compatibility

- Minecraft: `1.20.1`
- Forge: `47.1.0+`
- Create: `6.0.x`
- Java: `17`

The mod is built and tested against Create `1.20.1-6.0.8`.

## Installation

Install this mod alongside Create in a Forge `1.20.1` instance.

Required mods:

- Create `6.0.x`
- Forge `47.1.0+`

Both the client and server should have the mod installed.

## Building From Source

Run from the repository root:

```sh
./scripts/gradle-win-java.sh build
```

The built jar is written to:

```text
build/libs/verticalchainconveyors-1.20.1-1.0.0.jar
```

The helper script uses Java 17 and supports local environment overrides for Windows/WSL development. Machine-specific build and deployment notes should live in `CLAUDE.local.md`, which is ignored by git.

## Testing

Run the fast unit test suite:

```sh
./scripts/gradle-win-java.sh test
```

The tests cover the reusable conveyor math, including wheel centers, tangent points, connection validation, package movement, speed reversal, chain point sampling, selection outlines, and render section tracking.

Manual client checks are documented in `CLIENT_SMOKE_TESTS.md`.

## Development Notes

This mod works through Mixins against Create's Chain Conveyor classes. The main implementation lives under:

```text
src/main/java/com/osyris/verticalchainconveyors/
```

Key concepts:

- A `FACING` property is added to Create's Chain Conveyor block state.
- `FACING` represents the direction the conveyor's mounting face points.
- Geometry is calculated in the plane perpendicular to `FACING.getAxis()`.
- Floor-mounted `facing=DOWN` paths defer to Create's original behavior wherever practical.

When changing mixin method signatures, verify against the runtime Create jar in `libs/`, not only against a source checkout. Loader/version differences can change method descriptors.

## License

MIT
