package net.bestia.model.item

import net.bestia.model.IntegrationTest
import net.bestia.model.test.ItemFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class ItemRepositoryTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @BeforeEach
  fun setup() {
    ItemFixture.createItem(
        "apple",
        itemRepository
    )
  }

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
