package net.bestia.model.dao;

import net.bestia.model.domain.I18n;
import net.bestia.model.domain.TranslationCategory;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface I18nDAO extends Repository<I18n, I18n.I18nPK> {

	@Query("SELECT i FROM I18n i where i.id.category = :cat AND i.id.lang = :lang AND i.id.key = :key")
	public I18n findOne(@Param("cat") TranslationCategory category,
			@Param("key") String key, @Param("lang") String lang);

}
