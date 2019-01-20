package net.bestia.model.item

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener

import com.github.springtestdbunit.DbUnitTestExecutionListener
import com.github.springtestdbunit.annotation.DatabaseSetup

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
@TestExecutionListeners(DependencyInjectionTestExecutionListener::class, DbUnitTestExecutionListener::class)
@DatabaseSetup("/db/items.xml")
class ItemRepositoryTest {

  @Autowired
  private lateinit var itemRepository: ItemRepository
  @Test
  fun findItemByName_existingName_item() {
    val item = itemRepository.findItemByName("apple")
    Assert.assertNotNull(item)
  }

  @Test
  fun findItemByName_nonExistingName_null() {
    val item = itemRepository.findItemByName("bla_bli_blub")
    Assert.assertNull(item)
  }
}
