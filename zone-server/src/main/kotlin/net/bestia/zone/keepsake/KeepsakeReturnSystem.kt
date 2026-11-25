package net.bestia.zone.keepsake

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundStackRemoval
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.seal.SealReturnSystem
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/** A warded bag nobody broke the ward on goes to its master by post when the ward's time on the ground is up. */
@SpringComponent
class KeepsakeReturnSystem(
  private val groundStackRemoval: GroundStackRemoval,
  private val post: KeepsakePost,
  private val jobs: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.UPKEEP
  override val after = setOf(SealReturnSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(GroundItemStack::class)
  override val writes: ComponentClassSet = setOf(ItemEnchantments::class)

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()

    world.query(ItemEnchantments::class).each { id ->
      val ward = get<ItemEnchantments>().find(ItemEnchantmentId.KEEPSAKE_WARD) ?: return@each
      if (!ward.hasEnded(now)) return@each
      val bundleId = world.get(id, GroundItemStack::class)?.uniqueId ?: return@each
      val masterId = ward.value.toLong()

      groundStackRemoval.remove(world, id)
      jobs.submit(masterId) { post.sendHome(bundleId, masterId) }
    }
  }
}
