package net.bestia.zone.ecs.spawn

import net.bestia.zone.ecs.core.Component

/**
 * The durable name of a den, and the world it is a den of.
 *
 * The direct counterpart of [net.bestia.zone.ecs.prop.WorldObjectIdentity], and it exists for the same
 * reason: a den's **entity id is not its name**. `WildSpawnerBootRunner` recreates every den from the
 * generator's markers on every boot, so a den's id is a fresh snowflake each time and nothing that has to
 * outlive a restart can be keyed on it. [featureId] - worldgen's own deterministic name for the marker,
 * already used to seed the species draw - is the only stable one there is.
 *
 * ### Why two version fields rather than one
 *
 * [worldVersion] is the pipeline version of the world **as actually generated this boot**, exactly as
 * `WorldObjectIdentity.latticeVersion` is a lattice version, and it catches a den set *renamed*. A feature
 * id is a hash of a **sequential ordinal** over the Poisson sample, so moving
 * `SpawnerParams.candidateSpacing` by a metre renames every den on the world - and every one of those names
 * is a name some other den used to have.
 *
 * Taken from the generated world rather than from the stored `world` row on purpose, and it is the one place
 * this differs from `WorldObjectDivergence`. The two agree on every ordinary boot. They part company under
 * `worldgen.on-mismatch: IGNORE`, which keeps a stored row whose `pipelineVersion` column is not updatable
 * while generating terrain from current code - and there the *generated* version is the true one, so reading
 * it is what keeps the guard working in the case the stored value would have quietly broken it.
 *
 * [worldId] catches what a pipeline version cannot. `pipelineVersion` is a digest of code and params with
 * **no seed in it**, so a world thrown away and regenerated on a different seed - or a `world` row deleted
 * by hand, which is the documented world-only reset - produces an entirely different den set carrying the
 * identical version. Without this field those rows would pass the guard and be adopted by whichever den
 * inherited their ordinal: wrong, and silently so, which is the one failure mode a guard exists to prevent.
 *
 * ### What it deliberately does not catch
 *
 * The bestia catalogue is not in either version, so editing `resources/mob/` can make
 * `WildSpawnerService.pick` choose a different species for the same feature id while both versions sit
 * still. A re-attached blob then stands in a den that now says "wolf" and gets topped up with wolves. That
 * is cosmetic, self-heals on the den's next dormancy cycle, and is not the guard failing.
 */
data class DenIdentity(
  val featureId: Long,
  val worldId: Long,
  val worldVersion: Long,
)

/**
 * On a creature: which den made it, and is therefore responsible for despawning it.
 *
 * **Absent**, not nullable, on anything a den did not make - a `/spawn`ed mob, and every mob row written
 * before this component existed. Absence is the honest answer to "which den owns this", and it keeps
 * `world.has(id, DenMember::class)` the single question rather than splitting it into two.
 *
 * Not `Dirtyable`, like [net.bestia.zone.ecs.persistence.Persistent] and `AiAgent`: which den a creature
 * came from is server bookkeeping and has no business on the wire.
 */
data class DenMember(val den: DenIdentity) : Component
