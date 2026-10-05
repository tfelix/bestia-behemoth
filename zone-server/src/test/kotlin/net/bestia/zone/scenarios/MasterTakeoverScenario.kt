package net.bestia.zone.scenarios

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The world half of an account takeover; [net.bestia.zone.socket.DualConnectionTakeoverTest] covers the socket
 * half. The master can still be in the world when it is selected again, holding state that was never saved.
 */
class MasterTakeoverScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Test
  fun `selecting a master that is still in the world keeps its unsaved state`() {
    // The alt master, so no other scenario sharing this context sees its exp change.
    val masterId = testData.account1.masterIds.last()
    clientPlayer1.connect(masterId)
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    val liveExp = world.modifyOrThrow(masterEntityId) { id ->
      val exp = getOrThrow<Exp>(id)
      exp.value += 7
      exp.value
    }

    clientPlayer1.sendMessage(SelectMasterCMSG(clientPlayer1.connectedPlayerId, masterId))

    assertEquals(
      liveExp, masterRepository.findByIdOrThrow(masterId).exp,
      "the incumbent must be saved before it is replaced"
    )
    assertEquals(
      liveExp, world.read { get(masterEntityId, Exp::class)?.value },
      "the rebuilt master must start from the incumbent's state, not the last periodic save"
    )
  }
}
