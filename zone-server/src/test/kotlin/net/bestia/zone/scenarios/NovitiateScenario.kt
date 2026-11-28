package net.bestia.zone.scenarios

import net.bestia.worldgen.core.Faction
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.net.ChatCMSG
import net.bestia.zone.control.net.InteractEntityCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.faction.envoy.Envoys
import net.bestia.zone.faction.envoy.FactionEnvoy
import net.bestia.zone.faction.novitiate.NovitiateQuest
import net.bestia.zone.faction.persistence.NovitiateRecordRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.net.DecideQuestCMSG
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A master walks the Novitiate of Eternity from the first word with its Envoy to the oath. */
class NovitiateScenario : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connections: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var envoys: Envoys

  @Autowired
  private lateinit var records: NovitiateRecordRepository

  @Autowired
  private lateinit var quests: QuestService

  @Autowired
  private lateinit var masters: MasterRepository

  @Autowired
  private lateinit var items: ItemTemplateRegistry

  private var home: Vec3L? = null

  private val masterId: Long
    get() {
      return testData.account2.masterIds.first()
    }

  @AfterAll
  fun leaveEverythingAsItWas() {
    // The Spring context is shared with every other scenario.
    runCatching { command("/faction leave") }
    home?.let(::standAt)
    records.findByIdOrNull(masterId)?.let(records::delete)
    masters.findByIdOrNull(masterId)?.let {
      it.faction = null
      it.factionSwornAt = null
      masters.save(it)
    }
  }

  @Test
  fun `the Novitiate of Eternity ends in the oath`() {
    val start = envoys.stands.first { it.faction == Faction.ETERNITY }
    val pilgrimage = envoys.nearestElsewhere(Faction.ETERNITY, start.settlementIndex) ?: start
    home = world.read { get(masterEntity(), Position::class)!!.toVec3L() }

    standAt(start.position)
    talkTo(envoyAt(Faction.ETERNITY, start.settlementIndex))
    val record = awaitRecord()

    command("/item mana_crystal 3")
    await { assertTrue(crystalsCarried() >= 3) }
    talkTo(envoyAt(Faction.ETERNITY, start.settlementIndex))
    await { assertEquals(NovitiateQuest.PILGRIMAGE, quests.viewFor(masterId, record)?.stage) }

    standAt(pilgrimage.position)
    talkTo(envoyAt(Faction.ETERNITY, pilgrimage.settlementIndex))
    await { assertEquals(NovitiateQuest.OATH, quests.viewFor(masterId, record)?.stage) }

    clientPlayer2.sendMessage(DecideQuestCMSG(clientPlayer2.connectedPlayerId, record, NovitiateQuest.SWEAR))
    await {
      val membership = world.read { get(masterEntity(), FactionMembership::class) }
      assertEquals(Faction.ETERNITY, membership?.faction)
    }
  }

  private fun crystalsCarried(): Int {
    val crystal = items.idOf("mana_crystal")

    return world.read { get(masterEntity(), Inventory::class)!!.getItems().filter { it.itemId == crystal }.sumOf { it.amount } }
  }

  private fun awaitRecord(): Long {
    var questId: Long? = null
    await {
      questId = records.findByIdOrNull(masterId)?.questId
      assertNotNull(questId)
    }

    return questId!!
  }

  private fun envoyAt(faction: Faction, settlementIndex: Int): EntityId {
    var found: EntityId? = null
    await {
      found = world.read {
        var id: EntityId? = null
        each(FactionEnvoy::class) { entity, envoy ->
          if (envoy.faction == faction && envoy.settlementIndex == settlementIndex) id = entity
        }
        id
      }
      assertNotNull(found)
    }

    return found!!
  }

  private fun talkTo(envoy: EntityId) {
    clientPlayer2.sendMessage(InteractEntityCMSG(clientPlayer2.connectedPlayerId, envoy, ScriptArgs.EMPTY))
  }

  private fun standAt(at: Vec3L) {
    world.modify(masterEntity()) { id -> get(id, Position::class)!!.apply { x = at.x; y = at.y; z = at.z } }
  }

  private fun masterEntity(): EntityId {
    return connections.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)
  }

  private fun command(text: String) {
    clientPlayer2.sendMessage(ChatCMSG(playerId = clientPlayer2.connectedPlayerId, type = ChatType.COMMAND, text = text))
  }
}
