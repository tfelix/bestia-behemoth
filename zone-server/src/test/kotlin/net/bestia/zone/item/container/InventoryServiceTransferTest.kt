package net.bestia.zone.item.container

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.prop.chest.ChestStorages
import net.bestia.zone.prop.persistence.PlayerStructure
import net.bestia.zone.prop.persistence.PlayerStructureRepository
import net.bestia.zone.scenarios.ScenarioDataSetup
import net.bestia.zone.util.PlayerBestiaId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Goods handed between a master, its own bestias and its chests, against a real database: they are in exactly one
 * container afterwards, arrive as the same goods they left as, and never reach a bestia or chest of another master.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceTransferTest {

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var playerBestiaRepository: PlayerBestiaRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var playerStructureRepository: PlayerStructureRepository

  @Autowired
  private lateinit var chestStorages: ChestStorages

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx: TransactionTemplate get() = TransactionTemplate(transactionManager)

  private val created = mutableListOf<PlayerBestiaId>()

  private val createdChests = mutableListOf<Long>()

  private fun masterA() = testFixture.itemTestMasterIds[0]
  private fun masterB() = testFixture.itemTestMasterIds[1]

  private fun bestiaOf(masterId: Long): PlayerBestiaId = tx.execute {
    val master = masterRepository.findByIdOrThrow(masterId)
    playerBestiaRepository.save(PlayerBestia(master = master, bestia = testFixture.bestia1)).id
  }!!.also { created += it }

  private fun chestOf(masterId: Long): Long = tx.execute {
    val chest = PlayerStructure(
      kind = StaticEntityKind.CHEST, ownerMasterId = masterId, ownerAccountId = null, x = 0, y = 0, z = 0, yaw = 0f,
      chunkX = 0, chunkY = 0, storage = ItemContainer(ItemContainer.Type.STORAGE)
    )
    playerStructureRepository.save(chest).id
  }!!.also { createdChests += it }

  @AfterEach
  fun removeBestias() {
    tx.executeWithoutResult {
      created.forEach { playerBestiaRepository.deleteById(it) }
      createdChests.forEach { playerStructureRepository.deleteById(it) }
    }
    created.clear()
    createdChests.clear()
  }

  private fun heldInChest(structureId: Long, identifier: String) = tx.execute {
    val itemId = itemRepository.findByIdentifierOrThrow(identifier).id
    playerStructureRepository.findById(structureId).orElseThrow().container.slots
      .filter { it.template.id == itemId }
      .sumOf { it.amount }
  }!!

  private fun container(masterId: Long, playerBestiaId: PlayerBestiaId?): ItemContainer =
    if (playerBestiaId == null) {
      masterRepository.findByIdOrThrow(masterId).container
    } else {
      playerBestiaRepository.findById(playerBestiaId).orElseThrow().container
    }

  private fun held(masterId: Long, playerBestiaId: PlayerBestiaId?, identifier: String, maker: ItemProvenance? = null) =
    tx.execute {
      val itemId = itemRepository.findByIdentifierOrThrow(identifier).id
      container(masterId, playerBestiaId).slots
        .filter { it.isFree && it.template.id == itemId && it.provenance == maker }
        .sumOf { it.amount }
    }!!

  private fun heldInstanceIds(masterId: Long, playerBestiaId: PlayerBestiaId?) = tx.execute {
    container(masterId, playerBestiaId).slots.filter { !it.isStackable }.map { it.uniqueId }
  }!!

  private fun appleId(): Long = tx.execute { itemRepository.findByIdentifierOrThrow("apple").id }!!

  @Test
  fun `part of a pile goes from the master to its bestia and nowhere else`() {
    val master = masterA()
    val bestia = bestiaOf(master)
    inventoryService.addItem(master, "apple", 10)
    val before = held(master, null, "apple")

    val moved = inventoryService.transferBetweenOwners(master, ItemHolder.Master, ItemHolder.Bestia(bestia), appleId(), 0L, 4)

    assertNotNull(moved)
    assertEquals(4, moved!!.amount)
    assertEquals(before - 4, held(master, null, "apple"))
    assertEquals(4, held(master, bestia, "apple"))
  }

  @Test
  fun `goods go back from a bestia to its master, and between two bestias`() {
    val master = masterA()
    val first = bestiaOf(master)
    val second = bestiaOf(master)
    inventoryService.grantToOwner(master, first, appleId(), 6)
    val masterBefore = held(master, null, "apple")

    inventoryService.transferBetweenOwners(master, ItemHolder.Bestia(first), ItemHolder.Bestia(second), appleId(), 0L, 5)
    inventoryService.transferBetweenOwners(master, ItemHolder.Bestia(second), ItemHolder.Master, appleId(), 0L, 2)

    assertEquals(1, held(master, first, "apple"))
    assertEquals(3, held(master, second, "apple"))
    assertEquals(masterBefore + 2, held(master, null, "apple"))
  }

  @Test
  fun `a forged sword arrives as the same row`() {
    val master = masterA()
    val bestia = bestiaOf(master)
    val sword = inventoryService.mintInstanceForMaster(master, itemRepository.findByIdentifierOrThrow("iron_sword"))
    tx.execute {
      val instance = itemInstanceRepository.findById(sword.id).orElseThrow()
      instance.upgradeLevel = 5
      instance.durability = 21
      itemInstanceRepository.save(instance)
    }

    val moved = inventoryService.transferBetweenOwners(master, ItemHolder.Master, ItemHolder.Bestia(bestia), sword.item.id, sword.id, 1)

    assertEquals(sword.id, moved!!.uniqueId)
    assertTrue(heldInstanceIds(master, null).none { it == sword.id }, "the sword left the master")
    assertEquals(listOf(sword.id), heldInstanceIds(master, bestia), "and the bestia holds that very row")
    val after = tx.execute { itemInstanceRepository.findById(sword.id).orElseThrow() }!!
    assertEquals(5, after.upgradeLevel)
    assertEquals(21, after.durability)
  }

  @Test
  fun `a crafted pile keeps its maker and stays apart from a plain one`() {
    val master = masterA()
    val bestia = bestiaOf(master)
    val alice = ItemProvenance(crafterName = "Alice", worldName = "Genesis")
    inventoryService.grantToOwner(master, null, appleId(), 3, provenance = alice)
    inventoryService.grantToOwner(master, bestia, appleId(), 2)

    val moved = inventoryService.transferBetweenOwners(master, ItemHolder.Master, ItemHolder.Bestia(bestia), appleId(), 0L, 3, alice)

    assertEquals(alice, moved!!.provenance)
    assertEquals(3, held(master, bestia, "apple", alice))
    assertEquals(2, held(master, bestia, "apple"))
  }

  @Test
  fun `more than is held moves nothing`() {
    val master = masterA()
    val bestia = bestiaOf(master)
    inventoryService.grantToOwner(master, bestia, appleId(), 2)
    val masterBefore = held(master, null, "apple")

    assertNull(inventoryService.transferBetweenOwners(master, ItemHolder.Bestia(bestia), ItemHolder.Master, appleId(), 0L, 3))

    assertEquals(2, held(master, bestia, "apple"))
    assertEquals(masterBefore, held(master, null, "apple"))
  }

  @Test
  fun `a bestia of another master neither gives nor receives`() {
    val owner = masterA()
    val stranger = masterB()
    val strangersBestia = bestiaOf(stranger)
    inventoryService.addItem(owner, "apple", 5)
    inventoryService.grantToOwner(stranger, strangersBestia, appleId(), 5)
    val ownerBefore = held(owner, null, "apple")

    assertNull(inventoryService.transferBetweenOwners(owner, ItemHolder.Master, ItemHolder.Bestia(strangersBestia), appleId(), 0L, 1))
    assertNull(inventoryService.transferBetweenOwners(owner, ItemHolder.Bestia(strangersBestia), ItemHolder.Master, appleId(), 0L, 1))

    assertEquals(ownerBefore, held(owner, null, "apple"))
    assertEquals(5, held(stranger, strangersBestia, "apple"))
  }

  @Test
  fun `a slot promised to a trade stays put`() {
    val master = masterA()
    val bestia = bestiaOf(master)
    val sword = inventoryService.mintInstanceForMaster(master, itemRepository.findByIdentifierOrThrow("iron_sword"))
    val tradeId = 910_001L
    inventoryService.reserveForTrade(master, tradeId, sword.item.id, sword.id, 1)

    assertNull(inventoryService.transferBetweenOwners(master, ItemHolder.Master, ItemHolder.Bestia(bestia), sword.item.id, sword.id, 1))
    assertTrue(heldInstanceIds(master, bestia).isEmpty())

    inventoryService.releaseAllTradeReservations(master, tradeId)
  }

  @Test
  fun `goods go into the master's chest and back out`() {
    val master = masterA()
    val chest = ItemHolder.Chest(chestOf(master))
    inventoryService.addItem(master, "apple", 10)
    val before = held(master, null, "apple")

    assertNotNull(inventoryService.transferBetweenOwners(master, ItemHolder.Master, chest, appleId(), 0L, 4))
    assertNotNull(inventoryService.transferBetweenOwners(master, chest, ItemHolder.Master, appleId(), 0L, 1))

    assertEquals(3, heldInChest(chest.structureId, "apple"))
    assertEquals(before - 3, held(master, null, "apple"))
  }

  @Test
  fun `a chest of another master neither gives nor receives`() {
    val owner = masterA()
    val strangersChest = ItemHolder.Chest(chestOf(masterB()))
    inventoryService.addItem(owner, "apple", 5)
    val ownerBefore = held(owner, null, "apple")

    assertNull(inventoryService.transferBetweenOwners(owner, ItemHolder.Master, strangersChest, appleId(), 0L, 1))

    assertEquals(ownerBefore, held(owner, null, "apple"))
    assertEquals(0, heldInChest(strangersChest.structureId, "apple"))
  }

  @Test
  fun `a station's ingredients leave the master and its chest together, or not at all`() {
    val master = masterA()
    val chest = ItemHolder.Chest(chestOf(master))
    inventoryService.addItem(master, "apple", 5)
    inventoryService.transferBetweenOwners(master, ItemHolder.Master, chest, appleId(), 0L, 3)
    val before = held(master, null, "apple")

    val chestShort = mapOf(ItemHolder.Master to mapOf(appleId() to 2), chest to mapOf(appleId() to 4))
    assertFalse(inventoryService.consumeAllFrom(master, chestShort))
    assertEquals(before, held(master, null, "apple"))
    assertEquals(3, heldInChest(chest.structureId, "apple"))

    val enough = mapOf(ItemHolder.Master to mapOf(appleId() to 2), chest to mapOf(appleId() to 3))
    assertTrue(inventoryService.consumeAllFrom(master, enough))
    assertEquals(before - 2, held(master, null, "apple"))
    assertEquals(0, heldInChest(chest.structureId, "apple"))
  }

  @Test
  fun `a station pays nothing from another master's chest`() {
    val strangersChest = ItemHolder.Chest(chestOf(masterB()))
    inventoryService.addItem(masterB(), "apple", 2)
    inventoryService.transferBetweenOwners(masterB(), ItemHolder.Master, strangersChest, appleId(), 0L, 2)

    assertFalse(inventoryService.consumeAllFrom(masterA(), mapOf(strangersChest to mapOf(appleId() to 1))))
    assertEquals(2, heldInChest(strangersChest.structureId, "apple"))
  }

  @Test
  fun `a chest still holds a sword after a reload`() {
    val master = masterA()
    val chest = ItemHolder.Chest(chestOf(master))
    val sword = inventoryService.mintInstanceForMaster(master, itemRepository.findByIdentifierOrThrow("iron_sword"))

    inventoryService.transferBetweenOwners(master, ItemHolder.Master, chest, sword.item.id, sword.id, 1)
    tx.executeWithoutResult { chestStorages.loadAll() }

    assertEquals(listOf(sword.id), chestStorages.contentsOf(chest.structureId).getItems().map { it.uniqueId })
  }
}
