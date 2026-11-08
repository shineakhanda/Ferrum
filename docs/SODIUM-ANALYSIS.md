# How Sodium-family renderers get their speed (Embeddium analysis)

Source studied: Embeddium (embeddedt/embeddium, commit `aa6657d`, LGPL-3.0), the Forge port of
Sodium 0.5. Ferrum's code was written from this analysis; no Embeddium/Sodium code is copied.

## Embeddium's renderer, piece by piece

| Technique | Where in Embeddium | What it does | Ferrum |
|---|---|---|---|
| Section graph culling | `render/chunk/occlusion/OcclusionCuller` | Breadth-first walk from the camera section through 6-direction "can see through" links; only walks outwards; frustum + distance test per section | Vanilla 26.3/1.21.11 already does a graph walk (`SectionOcclusionGraph`). No change needed. |
| Fog occlusion | `RenderSectionManager.getEffectiveRenderDistance` | When fog is fully opaque, sections beyond the fog end are not even visited | Vanilla's fog end follows render distance, so the gain is small; not added. |
| Region batching + multi-draw | `render/chunk/region/RenderRegion`, `DefaultChunkRenderer` | 8x4x8 sections share one vertex buffer; one `glMultiDrawElementsBaseVertex` per region per pass | 26.3 has its own multi-draw-indirect path (Ferrum can force it on Intel: "Force Terrain Multi-Draw"). 1.21.11 has no equivalent. |
| Block face culling | `DefaultChunkRenderer.getVisibleFaces` | Each section's quads are stored in 7 slices by facing; slices facing away from the camera are not drawn (about half the terrain triangles) | **Added** as Ferrum's terrain path: quads are reordered by facing at build time (`render/terrain/FaceSorter`), and draws are split into visible facing runs at draw time (`TerrainDrawMixin`), for both versions and for 26.3's multi-draw-indirect path. |
| Compact vertex format | `vertex/format/impl/CompactChunkVertex` | 20-byte vertices instead of vanilla's larger block format | Would need custom terrain shaders and a replacement renderer. Not done. |
| **Render pass optimization** | `compile/pipeline/BlockRenderer.chooseOptimalBuilder` + `SpriteContentsMixin` | Cutout quads whose sprite has no transparent pixel go to the solid pass; translucent quads with only 0/255 alpha go to cutout | **Added** (cutout to solid only): `render/RenderPassOptimizer`, `SpriteContentsMixin`, `ModelBlockRendererMixin` in both versions. |
| Animate only visible textures | `features/textures/animations/tracking` | Only animated sprites used by visible sections are updated | Ferrum has this (`AnimatedSpriteTracker`). |
| Entity culling | `render/entity` | Skip entities in sections not visible this frame | Ferrum goes further: ray-traced occlusion for entities and block entities (`culling/OcclusionCuller`). |
| Translucency sorting | `sorting/TranslucentQuadAnalyzer` | Sorts only when needed | Vanilla 26.3/1.21.11 already sorts on demand. |

## What render pass optimization changes

Vanilla assigns a chunk layer per block. A grass block is in the cutout layer only because of its side
overlay, so its fully opaque top, bottom and dirt sides are drawn with the cutout shader. That shader
contains `discard`, and any shader that can discard turns off early-Z and hidden-surface removal on
tile-based GPUs (Adreno, Mali, PowerVR, Xclipse). Each covered pixel is then shaded before the GPU
knows it is hidden.

Ferrum scans every sprite once at load time (`SpriteContentsMixin`: is every pixel of every frame at
alpha 255?). While a section compiles, a quad headed for CUTOUT with a fully opaque sprite is written
into the SOLID layer of the same section. The result is identical (the quad could never discard), and
translucent geometry is never touched.

F3 line "Solid-promoted quads" counts how many quads were moved. Toggle: Performance > Render Pass
Optimization. Reload chunks (F3+A) after changing it.

## Block face culling (Ferrum terrain path)

1. **Build time** (`SectionCompilerMixin` + `TerrainFaces` + `FaceSorter`): every finished SOLID / CUTOUT section mesh is reordered in place into the groups -X, -Y, -Z, unassigned, +X, +Y, +Z. A quad goes to an axis group when its four corners lie in one axis plane, and the sign of its counter-clockwise geometric normal (vanilla `FaceInfo` winding) gives the direction. For each group the extreme plane is recorded (smallest +X plane, largest -X plane, ...).
2. **Draw time** (`TerrainDrawMixin`): when vanilla adds a section's draw, Ferrum checks the camera against those planes. A +X group whose smallest plane is at or beyond the camera contains only back faces, which terrain pipelines cull anyway. Visible groups are merged into contiguous runs (one or two draws per section in practice), and the single vanilla draw is replaced by those runs. The shared sequential quad index buffer means a run is just `firstIndex = firstQuad * 6`.
3. Splitting only happens when it removes at least 48 quads and an eighth of the mesh, so small sections don't pay for extra draw calls.

The classification and run merging are covered by a standalone test against vanilla's six face windings.

## Still open

- Compact vertices (smaller vertex format) need Ferrum's own terrain shaders.
- 1.21.11 has no region multi-draw; each run is still a separate draw call there.
