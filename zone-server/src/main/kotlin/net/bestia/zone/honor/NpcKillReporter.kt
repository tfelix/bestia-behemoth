package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.ecs.DeathSystem
import net.bestia.zone.townsfolk.ecs.Townsfolk
import net.bestia.zone.townsfolk.ecs.TownsfolkDeaths
import org.springframework.stereotype.Component

/**
 * Hears of every townsperson killed: keeps them away until they are replaced, and charges whoever did it.
 *
 * Before [DeathSystem], which removes the body and with it who it was.
 */
@Component
class NpcKillReporter(
  private val honor: HonorService,
  private val masters: MasterEntityResolver,
  private val deaths: TownsfolkDeaths,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(PlayerDeathSystem::class)
  override val before = setOf(DeathSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class, TakenDamage::class) + HonorService.READS
  override val writes: ComponentClassSet = HonorService.WRITES

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Townsfolk::class).each { victim ->
      deaths.died(get<Townsfolk>().identity)

      val killerAccount = world.get(victim, TakenDamage::class)?.topAccount(world) ?: return@each
      val killer = masters.selectedMasterEntityOf(killerAccount) ?: return@each
      val at = world.get(killer, Position::class)?.toVec3L() ?: return@each

      honor.record(world, HonorAct.KILL_NPC, killer, at)
    }
  }
}
