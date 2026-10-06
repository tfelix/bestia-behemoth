package net.bestia.zone.account

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.battle.attack.AttackCancelService
import net.bestia.zone.ecs.battle.status.InCombat
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.logout.DisconnectProtection
import net.bestia.zone.ecs.persistence.PersistAndRemove
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bestia.zone.session.AccountDisconnectedEvent

/**
 * The logout button makes a player wait, so dropping the connection must not be the quicker way out of a fight.
 */
class AccountEntityControlServiceTest {

  private val world = testWorld()
  private val connections = ConnectionInfoService()
  private val master: EntityId = world.createEntity { }

  private val service = AccountEntityControlService(
    connectionInfoService = connections,
    masterResolver = mockk<MasterResolver> { every { getSelectedMasterEntityIdByAccountId(ACCOUNT) } returns master },
    savePointService = mockk(relaxed = true),
    attackCancelService = AttackCancelService(),
    playerAOIService = mockk(relaxed = true),
    world = world,
    zoneConfig = WorldRulesConfig(tickRate = 20)
  )

  init {
    connections.activateSession(ACCOUNT, masterId = 1L, masterEntityId = master)
  }

  @Test
  fun `a master disconnected mid-fight stays in the world for the protection time`() {
    world.add(master, InCombat())

    service.handleAccountDisconnected(AccountDisconnectedEvent(this, ACCOUNT))

    assertTrue(world.has(master, DisconnectProtection::class))
    assertFalse(world.has(master, PersistAndRemove::class))
  }

  @Test
  fun `a master disconnected out of combat leaves at once`() {
    service.handleAccountDisconnected(AccountDisconnectedEvent(this, ACCOUNT))

    assertTrue(world.has(master, PersistAndRemove::class))
    assertFalse(world.has(master, DisconnectProtection::class))
  }

  private companion object {
    const val ACCOUNT = 1L
  }
}
