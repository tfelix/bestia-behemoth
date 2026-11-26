package net.bestia.zone.worldevent

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Vec2d

/** The point of a road or street nearest [at], within [reach] metres, or null out in the wilds. */
fun nearestRoad(generated: GeneratedWorld, at: Vec2d, reach: Double): Vec2d? {
  val area = Aabb(at.x - reach, at.y - reach, at.x + reach, at.y + reach)
  return generated.world.features.query(area)
    .asSequence()
    .filter { it.kind == FeatureKind.ROAD || it.kind == FeatureKind.STREET }
    .filterIsInstance<PolylineFeature>()
    .map { it.centerline.project(at) }
    .filter { it.distance <= reach }
    .minByOrNull { it.distance }
    ?.point
}
