package net.bestia.zone.casting.sense

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Sense: reads a monster's values off it, as the skill's popup shows them. */
@Component
class MonsterSensing(
  private val messages: OutMessageProcessor,
) {

  /** False when [targetId] is no creature with values to read. */
  fun sense(world: World, casterId: EntityId, targetId: EntityId): Boolean {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return false
    val combat = world.get(targetId, CombatStats::class)?.values ?: return false
    val health = world.get(targetId, Health::class) ?: return false
    val nature = world.get(targetId, Nature::class)
    val mana = world.get(targetId, Mana::class)

    messages.sendToPlayer(
      accountId,
      MonsterSenseSMSG(
        entityId = targetId,
        level = world.get(targetId, Level::class)?.level ?: 0,
        // A freeze or an endowed armor is what the body is right now, so that is what Sense reads.
        element = (combat.elementOverride ?: nature?.element ?: Element.NORMAL).name,
        kind = nature?.kind?.name.orEmpty(),
        size = nature?.size?.name.orEmpty(),
        hp = health.current,
        maxHp = health.max,
        mana = mana?.current ?: 0,
        maxMana = mana?.max ?: 0,
        attributes = combat.attributes,
        defense = combat.defense.defense,
        magicDefense = combat.defense.magicDefense,
      )
    )

    return true
  }
}
