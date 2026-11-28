package net.bestia.zone.scenarios

import net.bestia.worldgen.core.Faction
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.net.ChatCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.item.net.UseItemCMSG
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A sworn master reads the rite of Banished From The Citadel while it wears the Sigil of Redemption. */
class BanishmentScenario : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connections: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var masters: MasterRepository

  @Autowired
  private lateinit var items: ItemTemplateRegistry

  @Autowired
  private lateinit var itemInstances: ItemInstanceRepository

  private var levelBefore: Int? = null

  private val masterId: Long
    get() {
      return testData.account1.masterIds.first()
    }

  @AfterAll
  fun leaveEverythingAsItWas() {
    // The Spring context is shared with every other scenario: the master must be free to swear again.
    runCatching {
      world.modify(masterEntity()) { id ->
        add(id, FactionMembership())
        levelBefore?.let { add(id, Level(it)) }
        get(id, StatusEffects::class)?.removeEffect(StatusEffectId.UNSWORN.id)
      }
    }
    masters.findByIdOrNull(masterId)?.let {
      it.faction = null
      it.factionSwornAt = null
      it.unswornUntil = null
      masters.save(it)
    }
  }

  @Test
  fun `the rite frees the master and spends its sigil`() {
    raiseToSigilLevel()
    command("/faction swear chaos")
    await { assertEquals(Faction.CHAOS, membership()?.faction) }

    // One at a time: a second /item replaces the first one's pending intent.
    command("/item sigil_of_redemption 1")
    val sigil = awaitCarried("sigil_of_redemption")
    command("/item citadel_banishment_scroll 1")
    val scroll = awaitCarried("citadel_banishment_scroll")

    clientPlayer1.sendMessage(EquipItemCMSG(clientPlayer1.connectedPlayerId, sigil.playerItemId, EquipmentSlot.ACCESSORY_1))
    await { assertEquals(sigil.itemId, world.read { get(masterEntity(), Equipment::class)?.get(EquipmentSlot.ACCESSORY_1)?.itemId }) }

    clientPlayer1.sendMessage(UseItemCMSG(clientPlayer1.connectedPlayerId, scroll.playerItemId, ScriptArgs.EMPTY))

    await {
      val membership = membership()
      assertNull(membership?.faction)
      assertNotNull(membership?.unswornUntil)
    }
    assertTrue(world.read { get(masterEntity(), StatusEffects::class)!!.hasEffect(StatusEffectId.UNSWORN.id) })
    await { assertFalse(itemInstances.existsById(sigil.uniqueId)) }
    assertNull(carried("citadel_banishment_scroll"))
  }

  private fun raiseToSigilLevel() {
    world.modify(masterEntity()) { id ->
      val level = get(id, Level::class)!!
      levelBefore = level.level
      repeat(SIGIL_MIN_LEVEL - level.level) { level.inc() }
    }
  }

  private fun awaitCarried(identifier: String): Inventory.Item {
    var found: Inventory.Item? = null
    await {
      found = carried(identifier)
      assertNotNull(found)
    }

    return found!!
  }

  private fun carried(identifier: String): Inventory.Item? {
    val itemId = items.idOf(identifier)

    return world.read { get(masterEntity(), Inventory::class)!!.getItems().firstOrNull { it.itemId == itemId && !it.equipped } }
  }

  private fun membership(): FactionMembership? {
    return world.read { get(masterEntity(), FactionMembership::class) }
  }

  private fun masterEntity(): EntityId {
    return connections.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
  }

  private fun command(text: String) {
    clientPlayer1.sendMessage(ChatCMSG(playerId = clientPlayer1.connectedPlayerId, type = ChatType.COMMAND, text = text))
  }

  private companion object {
    /** `min-level` of the sigil in items.yml. */
    const val SIGIL_MIN_LEVEL = 10
  }
}
