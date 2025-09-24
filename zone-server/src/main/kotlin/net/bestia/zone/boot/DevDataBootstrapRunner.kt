package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterFactory
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.bestia.findByIdentifierOrThrow
import net.bestia.zone.ecs.ZoneInjectable
import net.bestia.zone.geometry.Vec3L
import org.springframework.boot.CommandLineRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.awt.Color
import kotlin.String
import kotlin.random.Random

/**
 * DEV: Populates the game and database with initial data for testing.
 * TODO Later should be moved to the test when we establish a proer initial data setup.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class DevDataBootstrapRunner(
  private val accountFactory: AccountFactory,
  private val masterFactory: MasterFactory,
  private val bestiaEntityFactory: BestiaEntityFactory,
  private val bestiaRepository: BestiaRepository,
  private val spawnManager: SpawnerManager
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

    val blobBestia = bestiaRepository.findByIdentifierOrThrow("blob")
    val doommasterBestia = bestiaRepository.findByIdentifierOrThrow("doom_master_of_doom")

    // master.addBestia(blobBestia, bestiaOwnerPolicy)
    // master.addBestia(doommasterBestia, bestiaOwnerPolicy)
  }

  private fun spawnMobs() {
    val spawner = Spawner(
      id = 1L,
      position = Vec3L.ZERO,
      maxSpawnCount = 5,
      range = 10,
      bestiaEntityFactory = bestiaEntityFactory
    )
    spawnManager.addSpawner(spawner)
  }
}

class Spawner(
  val id: Long,
  val maxSpawnCount: Int = 1,
  private val position: Vec3L,
  private val range: Int,
  private val bestiaEntityFactory: BestiaEntityFactory
) {

  private var spawnedEntities: Int = 0

  init {
    handleSpawn()
  }

  fun destroy() {
    // perform cleanup work e.g. cancel existing timer.
  }

  fun handleEntityRemoved() {
    spawnedEntities -= 1

    // check if we manage this entity if not throw

    handleSpawn()
  }

  private fun handleSpawn() {
    // check if an action is required? Spawn a new one?
    while (spawnedEntities < maxSpawnCount) {
      // queue a delay...
      spawnEntity()
    }
  }

  private fun spawnEntity() {
    val x = randomBetween(position.x - range / 2, position.x + range / 2)
    val y = randomBetween(position.y - range / 2, position.y + range / 2)
    bestiaEntityFactory.createMobEntity("blob", Vec3L(x, y, 0L), this)
    spawnedEntities++
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }
}

@Component
@ZoneInjectable
class SpawnerManager {

  private val spawnerById = mutableMapOf<Long, Spawner>()

  fun spawnedEntityRemoved(spawnerId: Long) {
    spawnerById[spawnerId]?.handleEntityRemoved()
  }

  fun addSpawner(spawner: Spawner) {
    spawnerById[spawner.id] = spawner
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}