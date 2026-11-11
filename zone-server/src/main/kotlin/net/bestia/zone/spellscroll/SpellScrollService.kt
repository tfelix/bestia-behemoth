package net.bestia.zone.spellscroll

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.InscribedSpell
import net.bestia.zone.casting.SpellScrollOdds
import net.bestia.zone.casting.SpellScrolls
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.LooseInstanceDisposal
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.master.bestia.PlayerBestiaSkillService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.spellscroll.persistence.InscribedScroll
import net.bestia.zone.spellscroll.persistence.SpellScrollInscription
import net.bestia.zone.spellscroll.persistence.SpellScrollInscriptionRepository
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Spell scrolls: what each holds, writing one, reading one into a bestia's memory, and the bestia a failed extraction
 * loses. The world-side calls run on the tick; the rows they change are written by an async job keyed by the master.
 */
@Service
class SpellScrollService(
  private val book: SpellScrollBook,
  private val repository: SpellScrollInscriptionRepository,
  private val bestiaCatalogue: BestiaCatalogue,
  private val itemTemplates: ItemTemplateRegistry,
  private val itemRepository: ItemRepository,
  private val inventoryService: InventoryService,
  private val looseInstances: LooseInstanceDisposal,
  private val bestiaSkills: PlayerBestiaSkillService,
  private val bestiaLoss: PlayerBestiaLoss,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : SpellScrolls {

  /** What the scroll [uniqueId] holds, wherever it is; null for one that holds nothing or no bestia attack. */
  fun spellOn(uniqueId: Long): InscribedSpell? {
    val scroll = book.scrollOf(uniqueId) ?: return null
    val attackLevel = bestiaCatalogue.attackLevelOf(scroll.skillId) ?: return null

    return InscribedSpell(scroll.skillId, scroll.level, attackLevel)
  }

  override fun trainingRefusal(world: World, casterId: EntityId, bestiaId: EntityId, scrollId: Long): OpError? {
    val spell = heldSpell(world, casterId, scrollId) ?: return OpError.SKILL_INVALID_CHOICE
    ownBestiaRefusal(world, casterId, bestiaId)?.let { return it }

    val bestiaLevel = world.get(bestiaId, Level::class)?.level ?: 0
    if (bestiaLevel < spell.attackLevel) {
      return OpError.SPELL_LEVEL_TOO_LOW
    }

    val knownLevel = world.get(bestiaId, KnownSkills::class)?.levelOf(spell.skillId) ?: 0
    return if (knownLevel >= spell.level) OpError.SPELL_ALREADY_KNOWN else null
  }

  override fun inscriptionRefusal(world: World, casterId: EntityId, bestiaId: EntityId, skillId: Long): OpError? {
    ownBestiaRefusal(world, casterId, bestiaId)?.let { return it }

    return if (attackOf(world, bestiaId, skillId) == null) OpError.SKILL_INVALID_CHOICE else null
  }

  override fun takeScroll(world: World, casterId: EntityId, scrollId: Long): InscribedSpell? {
    val spell = heldSpell(world, casterId, scrollId) ?: return null
    val owner = ItemOwner.of(world, casterId) ?: return null

    world.get(casterId, Inventory::class)?.removeByUniqueId(scrollId)
    book.forget(listOf(scrollId))
    asyncJobExecutor.submit(key = owner.masterId) { destroyHeld(owner, scrollId) }

    return spell
  }

  /** Destroys a scroll that was used up after its holder's bag already gave it up. Call it from any thread. */
  fun destroyUsed(masterId: Long, scrollId: Long) {
    book.forget(listOf(scrollId))
    asyncJobExecutor.submit(key = masterId) { looseInstances.destroy(scrollId) }
  }

  override fun attackOf(world: World, bestiaId: EntityId, skillId: Long): InscribedSpell? {
    val level = world.get(bestiaId, KnownSkills::class)?.levelOf(skillId) ?: 0
    if (level <= 0) {
      return null
    }
    val attackLevel = bestiaCatalogue.attackLevelOf(skillId) ?: return null

    return InscribedSpell(skillId, level, attackLevel)
  }

  override fun teach(world: World, casterId: EntityId, bestiaId: EntityId, spell: InscribedSpell) {
    val owned = world.get(bestiaId, OwnedBestia::class) ?: return
    val known = world.get(bestiaId, KnownSkills::class) ?: return

    known.learnOrUpdate(spell.skillId, maxOf(known.levelOf(spell.skillId), spell.level))
    // A learned passive feeds the status recalc.
    world.add(bestiaId, IsStatusValueDirty)
    asyncJobExecutor.submit(key = owned.masterId) {
      bestiaSkills.learn(owned.playerBestiaId, spell.skillId, spell.level)
    }

    tell(world, casterId, OperationSuccessSMSG(OpSuccess.SPELL_LEARNED))
  }

  override fun inscribe(world: World, casterId: EntityId, spell: InscribedSpell) {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return
    val accountId = world.get(casterId, Account::class)?.accountId

    asyncJobExecutor.submit(key = masterId) {
      val uniqueId = mint(masterId, spell)
      showInLiveInventory(casterId, uniqueId)
      accountId?.let { out.sendToPlayer(it, OperationSuccessSMSG(OpSuccess.SPELL_INSCRIBED)) }
    }
  }

  override fun breakMind(world: World, casterId: EntityId, bestiaId: EntityId) {
    if (ownBestiaRefusal(world, casterId, bestiaId) != null) {
      return
    }

    LOG.info { "Spell Inscription by $casterId broke the mind of bestia $bestiaId" }
    bestiaLoss.lose(world, bestiaId)
    tell(world, casterId, OperationErrorSMSG(OpError.SPELL_MIND_BROKEN))
  }

  /** What Spell Inscription at the master's level could copy off each of its bestias in the world, with the odds. */
  fun inscriptionOptions(world: World, masterEntityId: EntityId, inscriptionSkillId: Long): List<InscriptionOptionsSMSG.Option> {
    val masterId = world.get(masterEntityId, Master::class)?.masterId ?: return emptyList()
    val mind = world.get(masterEntityId, CombatStats::class)?.values?.attributes ?: return emptyList()
    val skillLevel = world.get(masterEntityId, KnownSkills::class)?.levelOf(inscriptionSkillId) ?: 0

    return OwnedBestia.ownedBy(world, masterId).flatMap { bestia ->
      val known = world.get(bestia.entityId, KnownSkills::class)?.levels().orEmpty()
      known.keys.mapNotNull { skillId -> attackOf(world, bestia.entityId, skillId) }.map { spell ->
        val success = SpellScrollOdds.inscriptionPercent(spell.attackLevel, skillLevel, mind.intelligence, mind.willpower)
        InscriptionOptionsSMSG.Option(bestia.entityId, spell.skillId, spell.level, success, SpellScrollOdds.mindBreakPercent(success))
      }
    }
  }

  /** The spell on the scroll [scrollId] in [casterId]'s bag, or null when the bag holds no such scroll. */
  private fun heldSpell(world: World, casterId: EntityId, scrollId: Long): InscribedSpell? {
    val held = world.get(casterId, Inventory::class)?.getItems()
      ?.any { it.uniqueId == scrollId && it.itemId == SpellScrollScript.ITEM_ID && !it.equipped } == true

    return if (held) spellOn(scrollId) else null
  }

  private fun ownBestiaRefusal(world: World, casterId: EntityId, bestiaId: EntityId): OpError? {
    val masterId = world.get(casterId, Master::class)?.masterId
    val ownerId = world.get(bestiaId, OwnedBestia::class)?.masterId

    return if (masterId != null && masterId == ownerId) null else OpError.SPELL_NOT_OWN_BESTIA
  }

  private fun destroyHeld(owner: ItemOwner, scrollId: Long) {
    transactions.executeWithoutResult {
      val removed = inventoryService.removeOneFromOwner(owner.masterId, owner.playerBestiaId, SpellScrollScript.ITEM_ID, 1, scrollId)
      if (removed == null) {
        // Already spent in the world; the database only disagrees when something raced it, which the log shows.
        LOG.warn { "Master ${owner.masterId} held no spell scroll $scrollId in the database" }
        return@executeWithoutResult
      }
      looseInstances.destroy(scrollId)
    }
  }

  /** @return the new scroll's unique id */
  private fun mint(masterId: Long, spell: InscribedSpell): Long {
    val uniqueId = checkNotNull(
      transactions.execute {
        val instance = inventoryService.mintInstanceForMaster(masterId, itemRepository.getReferenceById(SpellScrollScript.ITEM_ID))
        repository.save(SpellScrollInscription(instance, spell.skillId, spell.level))
        instance.id
      }
    )
    book.remember(InscribedScroll(uniqueId, spell.skillId, spell.level))

    return uniqueId
  }

  /** The row is written; the live bag is what the client sees and what weight is counted from. */
  private fun showInLiveInventory(entityId: EntityId, uniqueId: Long) {
    world.modify(entityId) { id ->
      get(id, Inventory::class)?.addItem(
        Inventory.Item(
          itemId = SpellScrollScript.ITEM_ID,
          amount = 1,
          weight = itemTemplates.weightOf(SpellScrollScript.ITEM_ID) ?: 0,
          uniqueId = uniqueId,
          stackable = false,
        )
      )
    }
  }

  private fun tell(world: World, casterId: EntityId, message: SMSG) {
    world.get(casterId, Account::class)?.accountId?.let { out.sendToPlayer(it, message) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
