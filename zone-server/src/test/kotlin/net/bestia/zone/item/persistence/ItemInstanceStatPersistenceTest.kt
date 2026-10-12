package net.bestia.zone.item.persistence

import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ItemInstanceStatPersistenceTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx by lazy { TransactionTemplate(transactionManager) }

  @Test
  fun `a forged copy keeps its level and rolled stats in order`() {
    val rolled = listOf(StatValue(ItemStat.STR, 3), StatValue(ItemStat.HIT, 9), StatValue(ItemStat.STR, 1))
    val id = tx.execute {
      val instance = ItemInstance(item = itemRepository.findByIdentifierOrThrow("iron_sword"), itemLevel = 34)
      instance.assignRolledStats(rolled)
      itemInstanceRepository.save(instance).id
    }!!

    tx.execute {
      val loaded = itemInstanceRepository.findByIdOrNull(id)!!

      assertEquals(34, loaded.itemLevel)
      assertEquals(rolled, loaded.rolledStats)
    }
  }

  @Test
  fun `deleting a copy deletes its rolled stats`() {
    val id = tx.execute {
      val instance = ItemInstance(item = itemRepository.findByIdentifierOrThrow("iron_sword"))
      instance.assignRolledStats(listOf(StatValue(ItemStat.DEX, 2)))
      itemInstanceRepository.save(instance).id
    }!!

    tx.execute { itemInstanceRepository.deleteById(id) }

    assertTrue(tx.execute { itemInstanceRepository.findByIdOrNull(id) } == null)
  }
}
