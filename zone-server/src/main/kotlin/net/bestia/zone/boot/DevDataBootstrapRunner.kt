package net.bestia.zone.boot

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterFactory
import net.bestia.zone.ecs.spawn.Spawner
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.geometry.Vec3L
import org.springframework.boot.CommandLineRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.awt.Color
import kotlin.String

/**
 * DEV: Populates the game and database with initial data for testing.
 * TODO Later should be moved to the test when we establish a proer initial data setup.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class DevDataBootstrapRunner(
  private val accountFactory: AccountFactory,
  private val masterFactory: MasterFactory,
  private val zoneServer: ZoneServer
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    setupAccounts()
    spawnMobs()
  }

  private fun setupAccounts() {
    val account = accountFactory.createAccount(1L)

    val createMasterData = MasterFactory.CreateMasterData(
      name = "rocket",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )

    masterFactory.create(account, createMasterData)

    // val blobBestia = bestiaRepository.findByIdentifierOrThrow("blob")
    // val doommasterBestia = bestiaRepository.findByIdentifierOrThrow("doom_master_of_doom")
    // master.addBestia(blobBestia, bestiaOwnerPolicy)
    // master.addBestia(doommasterBestia, bestiaOwnerPolicy)
  }

  private fun spawnMobs() {
    zoneServer.addEntityWithWriteLock {
      it.add(
        Spawner(
          position = Vec3L.ZERO,
          bestiaId = 1,
          maxSpawnCount = 3,
          range = 10,
        )
      )
    }
  }
}