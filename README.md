# Veil Iris Lights Compat

NeoForge 1.21.1 compatibility mod that routes Veil shader programs through the
active Iris shaderpack pipeline.

It captures Veil's processed GLSL, merges it into Iris gbuffer programs, handles
translucent shaders through `EntitiesTrans` or dithering, invalidates generated
programs when shaderpacks reload, and supports Iris shadow rendering.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228 or newer
- Iris 1.8.1 or newer
- Veil 4.0.0 or newer
