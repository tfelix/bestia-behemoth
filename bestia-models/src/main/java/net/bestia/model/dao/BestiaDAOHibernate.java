package net.bestia.model.dao;

import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Bestia;

@Repository("bestiaDao")
public class BestiaDAOHibernate extends GenericDAOHibernate<Bestia, Integer> implements BestiaDAO {

}
