package net.bestia.zone.faction.banishment

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.casting.GearOperations
import net.bestia.zone.ecs.core.World
import net.bestia.zone.faction.FactionService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaTimeConfig
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * The rite of Banished From The Citadel, read from its scroll: the master leaves its Order, its worn Sigil of
 * Redemption is destroyed, and it stays unsworn for half its level in Bestia days.
 */
@Component
class CitadelBanishmentScript(
  private val factions: FactionService,
  private val gear: GearOperations,
  private val unswornMark: UnswornMark,
  private val time: BestiaTimeConfig,
  private val out: OutMessageProcessor,
) : ItemScript {

  override val itemId: Long = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    if (factions.factionOf(world, userId) == null) {
      return refuse(world, userId, OpError.FACTION_NOT_SWORN)
    }
    val sigilSlot = sigilSlotOf(world, userId) ?: return refuse(world, userId, OpError.FACTION_SIGIL_NOT_WORN)

    gear.destroy(world, userId, sigilSlot)
    factions.leave(world, userId, unswornFor(world.get(userId, Level::class)?.level ?: 1))
    unswornMark.renew(world, userId)

    return true
  }

  private fun sigilSlotOf(world: World, userId: EntityId): EquipmentSlot? {
    val worn = world.get(userId, Equipment::class)?.getWorn() ?: return null

    return worn.entries.firstOrNull { it.value.itemId == SIGIL_ITEM_ID }?.key
  }

  /** Half the level in Bestia days, rounded up. */
  private fun unswornFor(level: Int): Duration {
    val bestiaDays = (level + 1) / 2
    val realSeconds = bestiaDays * SECONDS_PER_BESTIA_DAY / time.speedFactor

    return Duration.ofSeconds(realSeconds.toLong())
  }

  private fun refuse(world: World, userId: EntityId, code: OpError): Boolean {
    world.get(userId, Account::class)?.let { out.sendToPlayer(it.accountId, OperationErrorSMSG(code)) }

    return false
  }

  companion object {
    /** `citadel_banishment_scroll` in items.yml. */
    const val ITEM_ID = 776L

    /** `sigil_of_redemption` in items.yml. */
    const val SIGIL_ITEM_ID = 625L

    private const val SECONDS_PER_BESTIA_DAY = 86_400.0
  }
}
