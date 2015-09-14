package net.bestia.model.dao;

import net.bestia.model.domain.Attack;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("attackDao")
@Transactional(readOnly = true)
public class AttackDAOHibernate extends GenericDAOHibernate<Attack, Integer> implements AttackDAO {

}
