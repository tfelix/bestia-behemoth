package net.bestia.worldgen.resource

import net.bestia.worldgen.pipeline.WorldParams

/**
 * World tuning with the guaranteed deposit floor taken off, so a test can see what the geology alone produces.
 *
 * Shared by [GemDepositTest] and [VolcanicResourceTest], which both exist to answer one question: **can the
 * causal sampler reach this resource's ground at all.** On the shipped tuning they cannot answer it. Every
 * mineable ore has a floor under it now - see [MinableOre.guaranteedDeposits] - and `ResourceStage.guarantee`
 * fills a short ore in from the best cells the world has, so a resource whose `suitabilityFor` arm never fires
 * anywhere still turns up in every world. Both files would pass, and the module's habit 6 - *a subsystem that
 * is complete, tested and never reached looks exactly like one that works* - would have claimed another one.
 *
 * So they build their sweeps with this instead. What they then count is what the thinned Poisson sampler found
 * on its own, and a zero across a whole sweep means unreachable rather than rare.
 *
 * The floor's own half of the promise is `OreCoverageTest`'s, which runs on the shipped defaults and asserts
 * per world rather than per sweep. Between the two files every claim about a resource's presence is made
 * exactly once, against the tuning that makes it meaningful.
 */
object RawGeology {

  /** [WorldParams.DEFAULT] with every ore's guaranteed floor set to zero and nothing else touched. */
  val PARAMS: WorldParams = WorldParams.DEFAULT.let { defaults ->
    defaults.copy(
      resource = defaults.resource.copy(
        ore = OreTuning(floor = MinableOre.entries.associateWith { 0 })
      )
    )
  }
}
