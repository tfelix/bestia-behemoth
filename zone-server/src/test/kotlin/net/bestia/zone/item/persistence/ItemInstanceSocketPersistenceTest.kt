package net.bestia.zone.item.persistence

import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.essence.GemTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ItemInstanceSocketPersistenceTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx by lazy { TransactionTemplate(transactionManager) }

  private val boar = Essence(bestiaId = 6, tier = GemTier.RUBY)
  private val slime = Essence(bestiaId = 1, tier = GemTier.AMETHYST)

  @Test
  fun `filled sockets keep their essences in order`() {
    val id = tx.execute {
      val sword = ItemInstance(item = itemRepository.findByIdentifierOrThrow("iron_sword"), slots = 3)
      sword.socket(boar)
      sword.socket(slime)
      itemInstanceRepository.save(sword).id
    }!!

    tx.execute {
      val loaded = itemInstanceRepository.findByIdOrNull(id)!!

      assertEquals(listOf(boar, slime), loaded.essences)
      assertEquals(true, loaded.hasFreeSocket)
      assertNull(loaded.heldEssence)
    }
  }

  @Test
  fun `an essence gem keeps what it holds`() {
    val id = tx.execute {
      val gem = ItemInstance(item = itemRepository.findByIdentifierOrThrow("essence_gem"), heldEssence = boar)
      itemInstanceRepository.save(gem).id
    }!!

    tx.execute {
      assertEquals(boar, itemInstanceRepository.findByIdOrNull(id)!!.heldEssence)
    }
  }

  @Test
  fun `a full item takes no further essence`() {
    val sword = ItemInstance(item = tx.execute { itemRepository.findByIdentifierOrThrow("iron_sword") }!!, slots = 1)
    sword.socket(boar)

    assertFalse(sword.hasFreeSocket)
    assertThrows<IllegalStateException> { sword.socket(slime) }
  }
}
