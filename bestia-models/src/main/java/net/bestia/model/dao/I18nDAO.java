package net.bestia.model.dao;

import net.bestia.model.domain.I18n;

import org.springframework.data.repository.Repository;


public interface I18nDAO extends Repository<I18n, I18n.I18nPK> {

	public I18n findOne(I18n.I18nPK key);
	
}
