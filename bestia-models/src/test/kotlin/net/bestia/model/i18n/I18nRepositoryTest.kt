package net.bestia.model.i18n

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
class I18nRepositoryTest {
  @Autowired
  private lateinit var i18NRepository: I18nRepository

  @Test
  fun findById_existingId_dataset() {
    val result = i18NRepository.findOne(TranslationCategory.ITEM, "apple.name", "de")
    Assert.assertNotNull(result)
  }

  @Test
  fun findById_noExistingId_null() {
    val result = i18NRepository.findOne(TranslationCategory.ITEM, "blabla.name", "de")
    Assert.assertNull(result)
  }
}
