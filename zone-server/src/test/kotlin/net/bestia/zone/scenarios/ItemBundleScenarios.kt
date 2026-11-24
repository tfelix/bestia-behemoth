package net.bestia.zone.scenarios

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.item.persistence.ItemBundleRepository
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemBundleScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var bundles: ItemBundles

  @Autowired
  private lateinit var bundleRepository: ItemBundleRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private var masterId = 0L

  @BeforeAll
  fun createMaster() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "packer",
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

  @Test
  fun `a bag packs into one bundle, worn gear too, and unpacks back`() {
    clearBag()
    inventoryService.grantToMaster(masterId, APPLE, 3)
    val shoes = inventoryService.mintInstanceForMaster(masterId, itemRepository.findByIdOrNull(SHOES)!!)
    assertTrue(inventoryService.equip(masterId, null, SHOES, shoes.id, EquipmentSlot.FOOTGEAR))

    val bundle = transaction { bundles.packAll(masterRepository.findByIdOrThrow(masterId).container) }

    assertNotNull(bundle)
    assertEquals(3 * APPLE_WEIGHT + SHOES_WEIGHT, bundle.heldWeight, "a bundle weighs what it holds")
    assertTrue(bagItemIds().isEmpty(), "the bag gave everything up")

    val arrived = bundles.unpack(bundle.id, masterId, null)

    assertEquals(setOf(APPLE, SHOES), arrived.map { it.itemId }.toSet())
    assertEquals(setOf(APPLE, SHOES), bagItemIds())
    assertNull(bundleRepository.findByItemInstanceId(bundle.id), "an unpacked bundle is gone")
  }

  @Test
  fun `a bundle packed into another gives up its items instead`() {
    clearBag()
    inventoryService.grantToMaster(masterId, APPLE, 2)
    val inner = assertNotNull(transaction { bundles.packAll(masterRepository.findByIdOrThrow(masterId).container) })
    inventoryService.grantToMaster(masterId, SHOES, 1)
    transaction { masterRepository.findByIdOrThrow(masterId).container.addInstance(inner) }

    val outer = assertNotNull(transaction { bundles.packAll(masterRepository.findByIdOrThrow(masterId).container) })
    val arrived = bundles.unpack(outer.id, masterId, null)

    assertEquals(setOf(APPLE, SHOES), arrived.map { it.itemId }.toSet())
    assertNull(bundleRepository.findByItemInstanceId(inner.id), "the nested bundle is gone")
  }

  /** A new master starts with a kit, some of it worn. */
  private fun clearBag() {
    transaction {
      val bag = masterRepository.findByIdOrThrow(masterId).container
      bag.equipped().keys.forEach { bag.unequip(it) }
      bag.removeAllFree()
    }
  }

  private fun bagItemIds(): Set<Long> {
    return transaction { masterRepository.findByIdOrThrow(masterId).container.slots.map { it.template.id }.toSet() }.orEmpty()
  }

  private fun <T> transaction(block: () -> T): T? {
    return TransactionTemplate(transactionManager).execute { block() }
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 49L
    const val APPLE = 1L
    const val APPLE_WEIGHT = 15
    const val SHOES = 4L
    const val SHOES_WEIGHT = 80
  }
}
