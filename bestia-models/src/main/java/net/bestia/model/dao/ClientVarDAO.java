package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.domain.ClientVar;

@Repository("clientvarDao")
@Transactional(readOnly = true)
public interface ClientVarDAO extends CrudRepository<ClientVar, Long> {


	ClientVar findByKeyAndAccountId(String key, long accountId);
	
	ClientVar deleteByKeyAndAccountId(String key, long accountId);
}
