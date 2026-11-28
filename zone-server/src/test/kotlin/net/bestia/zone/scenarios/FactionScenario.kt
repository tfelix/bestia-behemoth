package net.bestia.zone.scenarios

import net.bestia.worldgen.core.Faction
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.net.ChatCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.logout.net.RequestLogoutCMSG
import net.bestia.zone.master.net.SelectMasterCMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.assertEquals

/** A sworn master leaves the world and comes back still sworn. */
class FactionScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var world: WorldView

  private val masterId: Long
    get() {
      return testData.account1.masterIds.first()
    }

  @AfterAll
  fun leaveTheOrder() {
    // The Spring context is shared with every other scenario: no master may stay sworn, in the world or the row.
    runCatching { command("/faction leave") }
    masterRepository.findByIdOrNull(masterId)?.let {
      it.faction = null
      it.factionSwornAt = null
      masterRepository.save(it)
    }
  }

  @Test
  fun `the Order a master swore to survives logging out and back in`() {
    clientPlayer1.connect(masterId)
    await { connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId) }

    command("/faction swear eternity")
    await {
      val answer = clientPlayer1.tryGetLastReceived(ChatSMSG::class)?.text.orEmpty()
      assert(answer.startsWith("Sworn to the Order of Eternity")) { "unexpected answer: $answer" }
    }

    clientPlayer1.sendMessage(RequestLogoutCMSG(clientPlayer1.connectedPlayerId))
    await {
      assertThrows<NoActiveSessionException> {
        connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
      }
    }
    await { assertEquals(Faction.ETERNITY, masterRepository.findByIdOrNull(masterId)?.faction) }

    clientPlayer1.sendMessage(SelectMasterCMSG(clientPlayer1.connectedPlayerId, masterId))
    await {
      val entity = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
      val membership = world.modify(entity) { id -> get(id, FactionMembership::class) }
      assertEquals(Faction.ETERNITY, membership?.faction)
    }
  }

  private fun command(text: String) {
    clientPlayer1.sendMessage(ChatCMSG(playerId = clientPlayer1.connectedPlayerId, type = ChatType.COMMAND, text = text))
  }
}
