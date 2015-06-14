package net.bestia.model.dao;

import org.springframework.stereotype.Repository;

import net.bestia.model.domain.PlayerBestia;

@Repository("playerBestiaDao")
public class PlayerBestiaDAOHibernate extends GenericDAOHibernate<PlayerBestia, Integer> implements PlayerBestiaDAO {


}
