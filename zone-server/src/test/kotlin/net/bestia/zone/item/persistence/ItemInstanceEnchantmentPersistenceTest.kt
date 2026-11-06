package net.bestia.zone.item.persistence

import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ItemInstanceEnchantmentPersistenceTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var enchantmentRepository: ItemInstanceEnchantmentRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx by lazy { TransactionTemplate(transactionManager) }

  private val now = Instant.parse("2026-11-06T12:00:00Z")

  private val seal = ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, strength = 70)

  @Test
  fun `a copy keeps its enchantment through a save`() {
    val id = enchantedCopy(seal)

    tx.execute {
      assertEquals(listOf(seal), itemInstanceRepository.findByIdOrNull(id)!!.enchantments(now))
    }
  }

  @Test
  fun `an ended enchantment is ignored on read and deleted by the sweep`() {
    val ended = seal.copy(endsAt = now.minusSeconds(1))
    val id = enchantedCopy(ended)

    tx.execute { assertEquals(emptyList<ActiveItemEnchantment>(), itemInstanceRepository.findByIdOrNull(id)!!.enchantments(now)) }
    tx.execute { enchantmentRepository.deleteEnded(now) }

    tx.execute {
      val instance = itemInstanceRepository.findByIdOrNull(id)!!
      assertEquals(emptyList<ActiveItemEnchantment>(), instance.enchantments(Instant.MIN))
    }
  }

  @Test
  fun `enchanting again renews the enchantment instead of adding a second`() {
    val renewed = seal.copy(endsAt = now.plusSeconds(60))
    val id = enchantedCopy(seal)

    tx.execute { itemInstanceRepository.findByIdOrNull(id)!!.enchant(renewed) }

    tx.execute { assertEquals(listOf(renewed), itemInstanceRepository.findByIdOrNull(id)!!.enchantments(now)) }
  }

  private fun enchantedCopy(enchantment: ActiveItemEnchantment): Long {
    return tx.execute {
      val instance = ItemInstance(item = itemRepository.findByIdentifierOrThrow("iron_sword"))
      instance.enchant(enchantment)
      itemInstanceRepository.save(instance).id
    }!!
  }
}
