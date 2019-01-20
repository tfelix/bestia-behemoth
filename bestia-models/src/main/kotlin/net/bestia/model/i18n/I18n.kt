package net.bestia.model.i18n

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable
import javax.persistence.*

/**
 * Saves translations for the bestia game into the database. This table is used
 * to fetch all kinds of hot-loaded, dynamic translations for the bestia game
 * system like quests, attack descriptions and item names etc.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "i18ns")
data class I18n(
    @EmbeddedId
    val id: I18nPK,
    /**
     * The translation text.
     *
     * @return The text for the translation.
     */
    val value: String
) : Serializable {

  @Embeddable
  data class I18nPK(
      @Column(name = "translation_key", nullable = false)
      val key: String,

      @Enumerated(EnumType.STRING)
      @Column(nullable = false)
      val category: TranslationCategory,

      @JsonIgnore
      @Column(length = 5, nullable = false)
      val lang: String
  ) : Serializable

  constructor(
      key: String,
      category: TranslationCategory,
      lang: String,
      value: String
  ) : this(I18nPK(key, category, lang), value)
}
