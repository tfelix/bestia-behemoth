package net.bestia.model.dao;

import net.bestia.model.domain.Bestia;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("bestiaDao")
@Transactional(readOnly = true)
public class BestiaDAOHibernate extends GenericDAOHibernate<Bestia, Integer> implements BestiaDAO {

}
