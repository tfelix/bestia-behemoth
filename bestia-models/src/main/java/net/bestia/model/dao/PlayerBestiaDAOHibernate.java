package net.bestia.model.dao;

import java.util.HashSet;
import java.util.Set;

import net.bestia.model.domain.PlayerBestia;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;

@Repository("playerBestiaDao")
public class PlayerBestiaDAOHibernate extends GenericDAOHibernate<PlayerBestia, Integer> implements PlayerBestiaDAO {

	@SuppressWarnings("unchecked")
	@Override
	public Set<PlayerBestia> findPlayerBestiasForAccount(long accId) {
		Query query = currentSession()
				.createQuery("from PlayerBestia pb where pb.owner.id = :owner and "
						+ "pb.id != (select acc.master.id from Account acc where acc.id = :owner)");
		query.setParameter("owner", accId);
		return new HashSet<>(query.list());
	}

}
