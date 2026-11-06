package net.bestia.zone.postal.seal

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
import net.bestia.zone.postal.courier.CourierService
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/** A fallen mail sack nobody picked up goes back to the post when its seal's time on the ground is up. */
@SpringComponent
class SealReturnSystem(
  private val groundStackRemoval: GroundStackRemoval,
  private val couriers: CourierService,
  private val jobs: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(GroundItemStack::class)
  override val writes: ComponentClassSet = setOf(ItemEnchantments::class)

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()

    world.query(ItemEnchantments::class).each { id ->
      val seal = get<ItemEnchantments>().find(ItemEnchantmentId.POSTAL_SEAL) ?: return@each
      if (!seal.hasEnded(now)) return@each
      val sackId = world.get(id, GroundItemStack::class)?.uniqueId ?: return@each

      groundStackRemoval.remove(world, id)
      jobs.submit(sackId) { couriers.returnToBoard(sackId) }
    }
  }
}
