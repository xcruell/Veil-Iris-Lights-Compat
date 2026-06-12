# Veil Iris Lights Compat

**Brings Veil shader effects and dynamic lights into the active Iris shaderpack pipeline.**

![Veil Iris Lights Compat showcase]({{HERO_IMAGE_URL}})

> **Minecraft 1.21.1** · **NeoForge** · **Client-side only** · Current version: **1.3.0**

Veil effects normally render outside a shaderpack's G-buffer. With Iris shaders enabled, they can disappear, use incorrect depth, or stop contributing visible light.

Veil Iris Lights Compat, shortened to **VILC**, translates compatible Veil shaders for Iris and adds a dedicated shaderpack-compatible pass for standard Veil point and area lights.

## Features

- Renders compatible Veil effects through active Iris shaderpacks
- Translates Veil vertex and fragment behavior into Iris G-buffer programs
- Supports opaque and translucent Veil effects
- Uses Iris `EntitiesTrans` programs when available
- Provides a dithering fallback for unsupported translucent paths
- Integrates compatible Veil geometry into Iris shadow rendering
- Automatically rebuilds translated shaders after shaderpack reloads
- Supports standard Veil point lights
- Supports standard Veil area and cone lights
- Mixes multiple differently colored lights
- Reconstructs world positions and surface normals from shaderpack depth
- Adds angle-dependent diffuse lighting
- Supports configurable voxel occlusion and block shadows
- Uses perceptual color normalization for more even RGB brightness
- Preserves visible surface texture under bright and overlapping lights
- Falls back to normal Veil rendering while shaderpacks are disabled

VILC does not add blocks or gameplay content by itself. It renders lights created through Veil by other mods, including YesMenn! Colored Lights.

## In-Game Configuration

Open the configuration through the mod list or with `O` by default.

### Quality Presets

| Preset | Point Lights | Area Lights | Surface Normals | Voxel Shadows |
|---|---:|---:|---|---|
| **Performance** | 24 | 8 | Fast | Disabled |
| **Balanced** | 48 | 16 | Fast | Enabled |
| **High** | 96 | 32 | Detailed | Enabled |

Every performance option can also be changed independently:

- Point-light budget from 1 to 96
- Area-light budget from 1 to 32
- Detailed surface normals
- Voxel shadows

Appearance controls update live:

- Exposure
- Color strength
- Color saturation
- Neutral lift
- Luminance normalization limit

The Reset button only restores appearance controls. Custom light budgets and quality options remain untouched.

Configuration is stored in `config/veil_iris_lights.json`.

## Debugging

- Adds active pass state and light counts to the F3 debug screen
- Supports BetterF3
- Shows configured point- and area-light budgets
- Reports voxel-occlusion and rendering state

## Requirements

| Dependency | Requirement |
|---|---|
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.228 or newer |
| **Iris** | 1.8.1 or newer |
| **Veil** | 4.0.0 or newer |
| **Sodium** | Version required by Iris |

Install VILC only on clients. Dedicated servers do not need it.

## Mod Developer Support

Mods using Veil's standard `PointLightData` and `AreaLightData` automatically use VILC's shaderpack-compatible light pass. A direct Java dependency on VILC is not required for standard lights.

Blocks containing a light source can opt out of self-occlusion through:

```text
#veil_iris_lights:non_occluding
```

Client mods may also register blocks directly:

```java
VeilIrisLightOcclusion.registerNonOccluding(
    ResourceLocation.fromNamespaceAndPath("example", "light_block")
);
```

Custom Veil light renderer types outside the standard point- and area-light APIs require separate integration.

## Compatibility Notes

- Shaderpacks differ significantly, so unusual pipelines may still need specific compatibility work
- Voxel shadows use a camera-centered 64 × 64 × 64 block grid
- Complex partial block models are approximated
- Leaves use partial voxel transmission rather than texture-alpha sampling
- Occlusion outside the available voxel grid cannot be perfectly reconstructed
- Light budgets limit how many visible Veil lights are processed per frame

## Works Well With

- **YesMenn!** for configurable RGB Colored Lights
- Other mods using Veil point or area lights
- BetterF3 for extended debug layouts

---

Made by **xcruell**
