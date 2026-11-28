package net.bestia.zone.faction.novitiate

import net.bestia.zone.faction.persistence.NovitiateRecord
import net.bestia.zone.faction.persistence.NovitiateRecordRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.faction.envoy.EnvoyStand
import net.bestia.zone.faction.envoy.Envoys
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestView
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.mana.ManaEmission
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * What an Envoy does for a master who is not sworn: starts the Novitiate, and takes the offering it asks for.
 * Off the tick; the live bag and the mana follow on the tick once the change has committed.
 */
@Service
class Novitiates(
  private val records: NovitiateRecordRepository,
  private val quests: QuestService,
  private val envoys: Envoys,
  private val inventory: InventoryService,
  private val items: ItemTemplateRegistry,
  private val mana: ManaEmission,
  private val world: WorldView,
  private val out: OutMessageProcessor,
  config: FactionConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val quest = NovitiateQuest(config)

  /** The master the Envoy of [faction] in [settlementIndex] was clicked by. */
  class Visitor(val masterId: Long, val accountId: AccountId, val masterEntityId: EntityId)

  @Transactional
  fun visit(visitor: Visitor, faction: Faction, settlementIndex: Int) {
    val record = runningRecordOf(visitor.masterId)
    when {
      record == null -> begin(visitor, faction, settlementIndex)
      record.faction != faction -> refuse(visitor, OpError.FACTION_NOVITIATE_ELSEWHERE)
      else -> offer(visitor, record, settlementIndex)
    }
  }

  /** A record whose quest ended or went with an old world is let go. */
  private fun runningRecordOf(masterId: Long): NovitiateRecord? {
    val record = records.findByIdOrNull(masterId) ?: return null
    if (quests.viewFor(masterId, record.questId)?.status?.isEnded == false) {
      return record
    }
    records.delete(record)

    return null
  }

  private fun begin(visitor: Visitor, faction: Faction, settlementIndex: Int) {
    val home = envoys.standOf(faction, settlementIndex) ?: return
    val pilgrimage = envoys.nearestElsewhere(faction, settlementIndex) ?: home
    val rivals = Faction.entries.filter { it != faction }.mapNotNull { envoys.standOf(it, settlementIndex) }
    val created = quests.create(quest.draft(faction, home, pilgrimage, rivals, offeringItemId(), clock.instant()))

    val refusal = quests.accept(visitor.masterId, created.id)
    if (refusal != null) {
      quests.end(created.id, QuestStatus.WITHDRAWN)
      refuse(visitor, refusal)
      return
    }
    records.save(NovitiateRecord(visitor.masterId, faction, created.id, settlementIndex))
    LOG.info { "Master ${visitor.masterId} began the Novitiate of $faction in settlement $settlementIndex" }
  }

  /** Only at the Envoy the proof names, and only while the proof is the stage the master is on. */
  private fun offer(visitor: Visitor, record: NovitiateRecord, settlementIndex: Int) {
    val view = quests.viewFor(visitor.masterId, record.questId) ?: return
    val index = view.objectives.indexOfFirst { it.kind == ObjectiveKind.BRING && it.stage == view.stage }
    if (index < 0 || settlementIndex != record.homeSettlement) {
      return
    }

    val wanted = view.objectives[index]
    val itemId = wanted.itemId ?: return
    val carried = world.read { looseAmountOf(this, visitor.masterEntityId, itemId) }
    val given = minOf(wanted.required - wanted.yours, carried)
    if (given <= 0 || !inventory.consumeAll(visitor.masterId, listOf(itemId to given))) {
      refuse(visitor, OpError.FACTION_NOTHING_TO_OFFER)
      return
    }
    quests.advancePersonal(visitor.masterId, record.questId, index, given)

    val stand = envoys.standOf(record.faction, settlementIndex) ?: return
    afterCommit { world.post { settleOnTick(this, visitor, record.faction, stand, itemId, given) } }
  }

  /** Eternity's Envoy binds what it was given; Chaos's shatters it and lets the mana go where it stands. */
  private fun settleOnTick(world: World, visitor: Visitor, faction: Faction, stand: EnvoyStand, itemId: Long, units: Int) {
    world.get(visitor.masterEntityId, Inventory::class)?.takeAcrossPiles(itemId, units)
    if (faction == Faction.CHAOS) {
      mana.onStoreBroken(stand.position, OFFERING, units)
    }
  }

  private fun looseAmountOf(world: World, masterEntityId: EntityId, itemId: Long): Int {
    val held = world.get(masterEntityId, Inventory::class)?.getItems().orEmpty()

    return held.filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
  }

  private fun offeringItemId(): Long {
    return checkNotNull(items.idOf(OFFERING)) { "The Novitiate needs the item $OFFERING" }
  }

  private fun refuse(visitor: Visitor, code: OpError) {
    afterCommit { out.sendToPlayer(visitor.accountId, OperationErrorSMSG(code)) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val OFFERING = "mana_crystal"
  }
}
