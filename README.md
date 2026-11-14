# Storm Boss — Original Fabric prototype

**Status: UNVERIFIED, NOT PLAYABLE AS DELIVERED.** This ZIP is a GitHub repository source tree, not a compiled JAR. The Minecraft 26.3 dependency coordinates and APIs have not been checked against published releases, and compilation has not been performed. The provided source may need mapping/API changes before it compiles.

## Implemented in source (prototype only)
- Operator commands `/stormboss summon` and `/stormboss disrupt`
- Vanilla Wither entity as the encounter body, with custom name
- Four time-based encounter stages
- Nearby-entity tractor pull and proximity damage
- Smoke, portal particles, and ink-particle tendril effects
- Limited terrain destruction (only stages 3–4, respects mobGriefing)
- Disruption attack to damage the boss

## NOT implemented
- Original multipart 3D Wither Storm model, animated physical tentacles, block absorption/rendering, complex AI, command-block crafting and story-style final battle
- Custom textures and sounds
- World save persistence of stage state, multiplayer network sync, tuning/performance testing
- Verified Fabric/Minecraft 26.3 build, runnable `.jar`, Modrinth release

## Build prerequisites
1. Verify Minecraft version naming and published compatible Fabric Loader, Fabric API and Fabric Loom versions.
2. Replace REPLACE_WITH_... values in gradle.properties.
3. Install JDK and Gradle; run `gradle build` (or add a verified Gradle wrapper).
4. Resolve any API/mapping changes; only upload a compiled, tested JAR to Modrinth.

This project uses no code or assets from Cracker's Wither Storm Mod.
