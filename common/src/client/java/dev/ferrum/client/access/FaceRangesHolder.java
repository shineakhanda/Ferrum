package dev.ferrum.client.access;

import dev.ferrum.client.render.terrain.FaceRanges;

/**
 * Added to SectionCompiler.Results and CompiledSectionMesh: face ranges per chunk layer, indexed by
 * the layer's ordinal. Null entries mean the layer was not face-sorted.
 */
public interface FaceRangesHolder {
    FaceRanges[] ferrum$faceRanges();

    void ferrum$setFaceRanges(FaceRanges[] ranges);
}
