package net.bestia.zone.questgen.ending

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.DeedObserver
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.questgen.plan.GeneratedQuestWorld
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Writes into the history of the world who dug up an artifact. It does not ask whether the finder had a quest:
 * the find is a fact either way, and what it means for a quest follows from the fact.
 */
@Component
// Before the quest's own counting of the dig: the others must hear "somebody was faster", not "quest complete".
@Order(Ordered.HIGHEST_PRECEDENCE)
class RelicFinds(
  private val questWorld: GeneratedQuestWorld,
  private val chronicle: LivingChronicle,
  private val jobs: AsyncJobExecutor,
  sessions: ConnectionInfoService,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
) : DeedObserver {

  override fun onDeed(world: World, deed: DeedFact) {
    if (deed.target.kind != WorldTarget.Kind.CACHE || deed.deed != DeedFact.Deed.EXAMINED) {
      return
    }
    val artifact = questWorld.artifactIn(deed.target.key.toLong()) ?: return
    val finder = masters.masterEntityOf(world, deed.actorId)?.let { world.get(it, Master::class) } ?: return
    val doer = LivingChronicle.Doer(finder.masterId, finder.name)

    jobs.submit(finder.masterId) {
      chronicle.record(EventKind.ARTIFACT_TAKEN, listOf(Actor(ActorType.ARTIFACT, artifact)), by = doer)
    }
  }
}
