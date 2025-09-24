package net.bestia.zone.scenarios

import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.message.Kill
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.TakenDamage
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Scenario: Spawns a blob bestia entity, connects, sends a kill message, and provides a placeholder for post-kill checks.
 */
class KillScenario : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var ecsManipulator: EcsTestManipulator

  @Autowired
  private lateinit var bestiaEntityFactory: BestiaEntityFactory

  @Autowired
  private lateinit var masterResolver: MasterResolver

  private var spawnedMobEntityId: EntityId = 0

  @Test
  @Order(1)
  fun `setup blob bestia entity and ensure it is alive`() {
    spawnedMobEntityId = bestiaEntityFactory.createMobEntity(testData.bestia1.id, Vec3L.ZERO)

    // TODO add damage distribution for the other accounts.
    ecsManipulator.executeAsyncOnEntity(
      spawnedMobEntityId
    ) { world, entity ->
      with(world) {
        entity.configure { it += TakenDamage() }

        val master2Entity = masterResolver.getEntityByMasterId(testData.account1.masterIds.first())!!
        entity[TakenDamage].addDamage(master2Entity, 135)

        //val master3Entity = masterResolver.getEntityByMasterId(clientPlayer3.masterId)!!
        //entity[TakenDamage].addDamage(master3Entity, 46)
      }
    }
  }

  @Test
  @Order(2)
  fun `connect and send kill message for blob bestia`() {
    clientPlayer1.sendMessage(Kill(clientPlayer1.connectedPlayerId, spawnedMobEntityId))

    await {
      val vanishMsg = clientPlayer1.tryGetLastReceived(VanishEntitySMSG::class)

      assertNotNull(vanishMsg)
      assertEquals(spawnedMobEntityId, vanishMsg.entityId)
      assertEquals(VanishEntitySMSG.VanishKind.KILLED, vanishMsg.kind)
    }
  }

  @Test
  @Order(3)
  fun `post-kill spawned loot items`() {
    // TODO: Add assertions to verify blob bestia is dead/removed
  }

  @Test
  @Order(4)
  fun `post-kill after acc 2 disconnected its master has EXP gain persisted`() {
    // TODO: Add assertions to verify blob bestia is dead/removed
  }
}

