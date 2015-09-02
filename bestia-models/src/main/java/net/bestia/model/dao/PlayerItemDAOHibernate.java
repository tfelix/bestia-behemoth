package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.PlayerItem;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;

@Repository("playerItemDao")
public class PlayerItemDAOHibernate extends GenericDAOHibernate<PlayerItem, Integer> implements PlayerItemDAO {

	/**
	 * @inheritDoc
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<PlayerItem> findPlayerItemsForAccount(long accId) {
		final Query query = currentSession().createQuery("from PlayerItem pi where pi.account.id = :accId");
		query.setParameter("accId", accId);
		return (List<PlayerItem>)query.list();
	}

	@Override
	public PlayerItem findPlayerItem(long accId, int itemId) {
		final Query query = currentSession().createQuery("from PlayerItem pi where pi.account.id = :accId and pi.item.id = :itemId");
		query.setParameter("accId", accId);
		query.setParameter("itemId", itemId);
		query.setMaxResults(1);
		return (PlayerItem) query.list().get(0);
	}

}
