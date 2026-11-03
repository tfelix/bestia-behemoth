package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.damage.ReceivedDamageSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.honor.ecs.ChargedVandals
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Charges [HonorAct.DAMAGE_BUILDING] to the master whose side has taken more than a tenth of a building's health:
 * a house of a town, or a station somebody built. Once per attacker and building.
 */
@Component
class BuildingDamageHonorSystem(
  private val honor: HonorService,
  private val masters: MasterEntityResolver,
) : System {
  override val phase = Phase.COMBAT
  override val after = setOf(ReceivedDamageSystem::class)

  override val reads: ComponentClassSet =
    setOf(TakenDamage::class, Health::class, StaticVisual::class, PlayerStructureIdentity::class) + HonorService.READS
  override val writes: ComponentClassSet = setOf(ChargedVandals::class) + HonorService.WRITES

  override fun update(world: World, deltaTime: Float) {
    world.query(TakenDamage::class, Health::class, StaticVisual::class).each { building ->
      if (get<StaticVisual>().kind.isBuilding) {
        chargeVandals(world, building, get<TakenDamage>(), get<Health>())
      }
    }
    world.query(TakenDamage::class, Health::class, PlayerStructureIdentity::class).each { structure ->
      chargeVandals(world, structure, get<TakenDamage>(), get<Health>())
    }
  }

  private fun chargeVandals(world: World, building: EntityId, damage: TakenDamage, health: Health) {
    val threshold = health.max * DAMAGE_SHARE
    val vandals = damage.damageByAccount(world).filterValues { it > threshold }.keys
    if (vandals.isEmpty()) {
      return
    }

    val charged = world.get(building, ChargedVandals::class) ?: world.add(building, ChargedVandals())
    for (account in vandals - charged.accounts) {
      charged.accounts.add(account)

      val vandal = masters.selectedMasterEntityOf(account) ?: continue
      val at = world.get(vandal, Position::class)?.toVec3L() ?: continue
      honor.record(world, HonorAct.DAMAGE_BUILDING, vandal, at)
    }
  }

  private companion object {
    const val DAMAGE_SHARE = 0.1
  }
}
