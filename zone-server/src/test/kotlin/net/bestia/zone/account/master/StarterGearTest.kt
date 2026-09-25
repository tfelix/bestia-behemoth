package net.bestia.zone.account.master

import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.scenarios.ScenarioDataSetup
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A new master is created *wearing* the novice kit, not merely carrying it.
 *
 * Worth an integration test rather than a unit one because the grant is only half the story: minting an
 * instance and marking a container slot as worn are separate writes, and `MasterEntitySpawner` builds the
 * live `Equipment` component from the second of them. A kit that landed in the pack instead of on the body
 * would look completely fine from `MasterFactory` and be invisible until someone logged in.
 *
 * Each test cleans up after itself - the fixture account only has so many master slots, and
 * [ScenarioDataSetup] is shared across the whole Spring context.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class StarterGearTest {

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var masterDeletionService: MasterDeletionService

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

  @Test
  fun `a new master wears the whole novice kit`() {
    withFreshMaster { master ->
      val worn = wornBy(master)

      assertEquals(
        setOf(EquipmentSlot.RIGHT_HAND, EquipmentSlot.ARMOR, EquipmentSlot.FOOTGEAR),
        worn.keys,
        "a new master should start with all three pieces on"
      )
      assertEquals("novice_knife", worn.getValue(EquipmentSlot.RIGHT_HAND).identifier)
      assertEquals("novice_shirt", worn.getValue(EquipmentSlot.ARMOR).identifier)
      assertEquals("novice_boots", worn.getValue(EquipmentSlot.FOOTGEAR).identifier)
    }
  }

  /**
   * Each piece is a real instance, which is what the equip marker points at and what carries its durability.
   * A stacked grant would have no id to wear.
   */
  @Test
  fun `each worn piece is backed by its own instance`() {
    withFreshMaster { master ->
      val worn = wornBy(master)

      worn.forEach { (slot, piece) ->
        assertTrue(piece.hasInstance, "$slot should be worn as an instance")
        assertTrue(piece.uniqueId != 0L, "$slot should carry a real instance id")
      }
    }
  }

  /** The gate has to let the kit through for the player it was written for. */
  @Test
  fun `the kit is novice-only, and a fresh master qualifies`() {
    withFreshMaster { master ->
      val worn = wornBy(master)

      assertTrue(
        worn.values.all { it.noviceOnly },
        "every piece of the starter kit should be flagged novice-only"
      )
    }
  }

  /**
   * The container's slots are a lazy collection, so reading them needs a session - the same reason
   * `MasterDeletionServiceTest` reaches for a [TransactionTemplate]. Resolved into plain values inside the
   * transaction so the assertions cannot trip over a detached proxy.
   */
  private fun wornBy(master: Master): Map<EquipmentSlot, WornPiece> {
    return transactionTemplate.execute {
      masterRepository.findByIdOrThrow(master.id).container.equipped().mapValues { (_, slot) ->
        WornPiece(
          identifier = slot.template.identifier,
          noviceOnly = slot.template.noviceOnly,
          uniqueId = slot.uniqueId,
          hasInstance = slot.itemInstance != null
        )
      }
    }!!
  }

  private data class WornPiece(
    val identifier: String,
    val noviceOnly: Boolean,
    val uniqueId: Long,
    val hasInstance: Boolean
  )

  private fun withFreshMaster(block: (Master) -> Unit) {
    val master = masterFactory.create(
      ACCOUNT_ID,
      MasterFactory.CreateMasterData(
        name = "novice${NEXT_NAME.getAndIncrement()}",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt()
      )
    )

    try {
      block(master)
    } finally {
      masterDeletionService.delete(ACCOUNT_ID, master.id, master.name)
    }
  }

  private companion object {
    /** Account 3 of the fixture: it starts with a single master, so there are free slots to create into. */
    const val ACCOUNT_ID = 3L

    val NEXT_NAME = AtomicInteger(1)
  }
}
