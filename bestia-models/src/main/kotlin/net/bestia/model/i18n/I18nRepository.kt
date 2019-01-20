package net.bestia.model.i18n

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

@org.springframework.stereotype.Repository
interface I18nRepository : Repository<I18n, I18n.I18nPK> {

  @Query("SELECT i FROM I18n i where i.id.category = :cat AND i.id.lang = :lang AND i.id.key = :key")
  fun findOne(@Param("cat") category: TranslationCategory,
              @Param("key") key: String, @Param("lang") lang: String): I18n
}
