# Veil Iris Lights Compat

NeoForge 1.21.1 compatibility mod that routes Veil shader programs through the
active Iris shaderpack pipeline.

It captures Veil's processed GLSL, merges it into Iris gbuffer programs, handles
translucent shaders through `EntitiesTrans` or dithering, invalidates generated
programs when shaderpacks reload, and adds shaderpack-compatible Veil point and
area lights.

## Integration

Mods using Veil's standard `PointLightData` and `AreaLightData` automatically
use the shaderpack-compatible light pass. No direct dependency on this mod's
Java classes is required.

Blocks that should not occlude a light placed inside themselves can be added to:

```text
#veil_iris_lights:non_occluding
```

The tag can be supplied by a mod or datapack. Custom Veil light renderer types
outside the standard point and area light APIs require separate integration.

Client mods can also register a block directly when tags are unsuitable:

```java
VeilIrisLightOcclusion.registerNonOccluding(
    ResourceLocation.fromNamespaceAndPath("example", "light_block")
);
```

## Rendering Quality

- `Performance`: derivative normals and no voxel-shadow tracing.
- `Balanced`: derivative normals with voxel shadows.
- `High`: detailed depth-based normals with voxel shadows.

All presets keep the same point and area light limits, so changing quality does
not make individual light sources disappear.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228 or newer
- Iris 1.8.1 or newer
- Veil 4.0.0 or newer
