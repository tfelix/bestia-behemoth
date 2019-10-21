package net.bestia.model.item

import net.bestia.model.IntegrationTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class ItemRepositoryTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository
  @Test
  fun findItemByName_existingName_item() {
    val item = itemRepository.findItemByName("apple")
    assertNotNull(item)
  }

  @Test
  fun findItemByName_nonExistingName_null() {
    val item = itemRepository.findItemByName("bla_bli_blub")
    assertNull(item)
  }
}
