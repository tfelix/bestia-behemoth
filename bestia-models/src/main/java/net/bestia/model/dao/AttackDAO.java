package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.domain.Attack;

@Repository("attackDao")
@Transactional(readOnly = true)
public interface AttackDAO extends CrudRepository<Attack, Integer> {

}
