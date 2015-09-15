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

@Entity
@Table(name = "i18ns")
public class I18n implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Embeddable
	public static class I18nPK implements Serializable {

		@Transient
		private static final long serialVersionUID = 1L;
		protected String key;
		
		@Enumerated(EnumType.STRING)
		protected TranslationCategory category;

		@JsonIgnore
		@Column(length = 5)
		protected String lang;

		public I18nPK() {

		}

		public I18nPK(TranslationCategory category, String key, String lang) {
			this.category = category;
			this.key = key;
			this.lang = lang;
		}

		@Override
		public boolean equals(Object obj) {
			// TODO Auto-generated method stub
			return super.equals(obj);
		}

		@Override
		public int hashCode() {
			return Objects.hash(key, category, lang);
		}
	}

	@EmbeddedId
	private I18nPK id;

	private String value;
	
	public I18n() {
		
	}
	
	public I18n(String key, TranslationCategory category, String lang, String value) {
		id = new I18nPK(category, key, lang);
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
