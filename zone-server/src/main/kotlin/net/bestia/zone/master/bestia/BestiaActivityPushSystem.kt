package net.bestia.zone.master.bestia

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiActSystem
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Sends an owned bestia's status line to its owner whenever it changes while they play. At most every
 * [MIN_GAP_MS] per bestia, so one flicking between goals does not flood the client; the last change still arrives.
 */
@SpringComponent
class BestiaActivityPushSystem(
  private val connections: ConnectionInfoService,
  private val activities: BestiaActivities,
  private val out: OutMessageProcessor,
  private val now: () -> Long = java.lang.System::currentTimeMillis,
) : System {
  // After the AI has taken up this tick's goals.
  override val phase = Phase.AI
  override val after = setOf(AiActSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(OwnedBestia::class) + BestiaActivities.READS
  override val writes: ComponentClassSet = emptySet()

  private class Sent(val activity: BestiaActivity, val at: Long)

  private val sent = HashMap<EntityId, Sent>()

  override fun update(world: World, deltaTime: Float) {
    val time = now()
    val accountOfMaster = connections.activeMasters().entries.associate { (accountId, masterId) -> masterId to accountId }
    val watched = HashSet<EntityId>()

    world.query(OwnedBestia::class, AiAgent::class).each { id ->
      val owned = get<OwnedBestia>()
      val accountId = accountOfMaster[owned.masterId] ?: return@each
      watched += id
      val job = get<AiAgent>().memory.get(BestiaDomain.MAIN_JOB) ?: BestiaJob.REST
      val activity = activities.of(world, id, job) ?: return@each

      val last = sent[id]
      if (last?.activity == activity || (last != null && time - last.at < MIN_GAP_MS)) return@each
      sent[id] = Sent(activity, time)
      out.sendToPlayer(accountId, BestiaActivitySMSG(id, owned.playerBestiaId, activity))
    }

    // Forgotten while its owner is away, so the first sweep after they come back sends it afresh.
    sent.keys.retainAll(watched)
  }

  companion object {
    const val MIN_GAP_MS = 2_000L
  }
}
