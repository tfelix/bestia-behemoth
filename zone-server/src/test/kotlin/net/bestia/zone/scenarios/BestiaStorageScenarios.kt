package net.bestia.zone.scenarios

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BestiaStorageScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var world: WorldView

  private var masterId = 0L

  @BeforeAll
  fun createMaster() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "keeper",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
  }

  /** Every Spring context shares one H2 database; see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNoBestiaBehind() {
    bestiasInWorld().forEach { bestia -> world.modify(bestia.entityId) { id -> destroy(id) } }
  }

  @Test
  @Order(1)
  fun `a new bestia goes into storage once every active slot is taken`() {
    repeat(ACTIVE_SLOTS) {
      assertFalse(create().isStored)
    }

    val overflow = create()

    assertTrue(overflow.isStored)
    assertEquals(ACTIVE_SLOTS, bestiasInWorld().size, "a stored bestia is not in the world")
  }

  private fun create(): PlayerBestia {
    return playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
  }

  private fun bestiasInWorld(): Set<PlayerEntity> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 48L
    const val ACTIVE_SLOTS = 4
  }
}
