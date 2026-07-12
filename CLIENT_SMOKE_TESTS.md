# Client Smoke Tests

Run these in a local client profile after the jar has been deployed. Keep one small creative test world with visible labels/signs so the layout can be reused after each build.

## Rendering And Selection

- **Idle rendering/culling:** place unpowered chain conveyors in all six facings. Check them from near, medium, and far distances without mouseover. The wheel, guard/chain geometry, and idle model should stay visible; no facing should only show the centre.
- **Selection outlines:** start a chain connection from each non-DOWN facing and hover valid/invalid targets. White, green, and red outlines should sit on the mounted wheel plane, offset inward from the mounting face, not always horizontally.
- **Preview lines:** while selecting a second conveyor, inspect the two intended path lines for wall, ceiling, and mixed-facing pairs. Lines should connect tangent-to-tangent without horizontal XZ assumptions.
- **Connection validation:** verify too-close, axial, too-steep, and reasonable planar connections for wall and ceiling mounts. The status message should match the expected reason.
- **Validation symmetry:** try the same mixed-axis link starting from each endpoint. Both selection orders must return the same valid/invalid result.
- **Loop interaction:** with a rideable chain, package, and Frogport, target the wheel loop from every mounting direction. The hit region and custom outline must follow the mounted wheel plane.
- **Flywheel modes:** repeat representative idle/connected/package checks with Flywheel visualization enabled and disabled.

## Package Movement

- **Package transfer:** send packages through simple same-facing wall chains, floor-to-wall, wall-to-ceiling, and a mixed `DOWN -> EAST -> UP -> NORTH` path. Packages should transfer to the next conveyor instead of looping on the target.
- **Routing discipline:** create a conveyor with an earlier non-routed exit and a later routed exit. A routed package should wait for the routed exit instead of taking the earlier fallback path.
- **Backpressure:** fill or block the routed target so it cannot accept more packages. The package should wait rather than rerouting to an unrelated fallback.
- **Speed reversal:** reverse rotation while a package is travelling between mixed-facing conveyors. It should flip progress smoothly using the actual visible chain length and not jump past either endpoint.
- **Package yaw:** watch packages around vertical loop segments. They should not spin as if the loop were horizontal; yaw should remain stable during pure vertical motion and update only when there is horizontal travel direction.
- **Frogports/ports:** test at least one travel-port extraction and one loop-port extraction on a wall or ceiling conveyor. Anticipation/export should still happen at the intended point.

## Persistence And Integration

- **Delayed chunk load:** create a mixed-facing connection across a chunk border, unload both chunks, then load only one endpoint for several seconds before loading the other. Rendering and package transfer must correct themselves when the target arrives.
- **Transforms:** rotate and mirror wall-, floor-, and ceiling-mounted conveyors with a structure or schematic workflow. `FACING` and connection offsets must transform together.
- **Dedicated server:** start a dedicated server with Create and this mod, join with a matching client, and exercise one mixed-facing package route.
