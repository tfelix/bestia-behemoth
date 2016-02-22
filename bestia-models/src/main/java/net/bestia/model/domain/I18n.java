package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Saves translations for the bestia game into the database. This table is used
 * to fetch all kinds of hot-loaded, dynamic translations for the bestia game
 * system like quests, attack descriptions and item names etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "i18ns")
public class I18n implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Embeddable
	public static class I18nPK implements Serializable {

		@Transient
		private static final long serialVersionUID = 1L;
		
		@Column(name="translation_key", nullable = false)
		protected String key;

		@Enumerated(EnumType.STRING)
		@Column(nullable = false)
		protected TranslationCategory category;

		@JsonIgnore
		@Column(length = 5, nullable = false)
		protected String lang;

		/**
		 * Std. Ctor for Hibernate.
		 */
		public I18nPK() {
			// no op.
		}

		public I18nPK(TranslationCategory category, String key, String lang) {
			this.category = category;
			this.key = key;
			this.lang = lang;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			I18nPK other = (I18nPK) obj;
			if (category != other.category)
				return false;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (lang == null) {
				if (other.lang != null)
					return false;
			} else if (!lang.equals(other.lang))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(key, category, lang);
		}
	}

	@EmbeddedId
	private I18nPK id;

	private String value;

	/**
	 * Std. Ctor for Hibernate.
	 */
	public I18n() {
		// no op.
	}

	/**
	 * Ctor for setting up an translation entry.
	 * 
	 * @param key
	 *            The identifier key of the translation.
	 * @param category
	 *            The category to break translation further down.
	 * @param lang
	 *            The language code of the translation.
	 * @param value
	 *            The actual translation.
	 */
	public I18n(String key, TranslationCategory category, String lang, String value) {
		id = new I18nPK(category, key, lang);
		this.value = value;
	}

	/**
	 * The translation text.
	 * 
	 * @return The text for the translation.
	 */
	public String getValue() {
		return value;
	}
}
