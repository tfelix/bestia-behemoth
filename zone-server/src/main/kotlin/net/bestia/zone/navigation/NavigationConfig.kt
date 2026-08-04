package net.bestia.zone.navigation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

/**
 * Runtime settings for NPC navigation.
 *
 * Like `ChunkStreamConfig`, none of these decides what the world *is* - the macro graph is generated with the
 * world and these only govern how it is used - so all of them can change on a restart with no consequence.
 *
 * @property localPathfindsPerTick how many local searches one tick may contain. The tick has a fifty
 *   millisecond budget at the default rate and one bounded search is well under a millisecond, so this is
 *   deliberately generous; what it exists to stop is the pathological case of a whole pack running out of
 *   waypoints on the same tick and each starting a search. A refused search costs nothing - the NPC keeps
 *   walking what it had and asks again next tick.
 * @property localSearchSpan longest straight-line distance, in position units, that the *local* tier will
 *   accept at all. Beyond this the caller wants the macro graph: a search that expands a few thousand columns
 *   to conclude the target is a kilometre away has already wasted the tick. At 32 this is one chunk, which is
 *   comfortably more than any wander or chase needs.
 * @property localSearchMargin slack around the two endpoints' bounding box, in position units, so a detour
 *   round an obstacle still fits inside the search. Too tight and an NPC refuses to walk round a building;
 *   too loose and a hopeless search expands the whole neighbourhood before giving up.
 * @property localExpansionLimit hard ceiling on columns one local search may expand. The safety valve for an
 *   unreachable goal - a target inside a sealed room would otherwise expand every column in the box.
 * @property macroRevalidateSeconds how often blocked-edge revalidation runs. Deliberately seconds rather than
 *   ticks: a bridge being destroyed is a rare, discrete event, unlike the voxel edits `DerivedStore` reacts
 *   to, so checking every tick would be checking a hundred times more often than anything changes. This is
 *   also the propagation lag the requirements ask for - NPCs learn that a crossing is gone within a few
 *   seconds, not instantly.
 * @property macroRevalidationsPerPass how many stale edges one pass re-tests. Bounded for the same reason
 *   `derived-rebuilds-per-tick` is: a player mining along a riverbank can invalidate a batch of edges at once,
 *   and draining them over two or three passes is invisible while a spike on the tick thread is not.
 * @property macroReplanJitterSeconds how long after the graph changes an individual NPC may keep planning
 *   against the old version. The staggering the requirements ask for - without it, every traveller in the
 *   world replans on the same tick the graph moves. Each NPC picks its own delay in `0..this` once.
 */
@ConfigurationProperties(prefix = "navigation")
@ConfigurationPropertiesScan
data class NavigationConfig(
  val localPathfindsPerTick: Int = 8,
  val localSearchSpan: Long = 32,
  val localSearchMargin: Long = 12,
  val localExpansionLimit: Int = 2_048,
  val macroRevalidateSeconds: Float = 5f,
  val macroRevalidationsPerPass: Int = 8,
  val macroReplanJitterSeconds: Float = 60f
) {

  init {
    require(localPathfindsPerTick >= 0) { "localPathfindsPerTick must not be negative" }
    require(localSearchSpan > 0) { "localSearchSpan must be positive" }
    require(localSearchMargin >= 0) { "localSearchMargin must not be negative" }
    require(localExpansionLimit > 0) { "localExpansionLimit must be positive" }
    require(macroRevalidateSeconds > 0f) { "macroRevalidateSeconds must be positive" }
    require(macroRevalidationsPerPass >= 0) { "macroRevalidationsPerPass must not be negative" }
    require(macroReplanJitterSeconds >= 0f) { "macroReplanJitterSeconds must not be negative" }
  }
}
