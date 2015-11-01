package net.bestia.model.dao;

import java.util.List;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.domain.AttackLevel;

@Repository("attackLevelDao")
@Transactional(readOnly = true)
public class AttackLevelDAOHibernate extends GenericDAOHibernate<AttackLevel, Integer> implements AttackLevelDAO {

	@SuppressWarnings("unchecked")
	@Override
	public List<AttackLevel> getAllAttacksForBestia(int bestia) {
		
		final Query query = currentSession()
				.createQuery("from AttackLevel al where al.bestia.id = :bestiaId order by al.minLevel asc");
		query.setParameter("bestiaId", bestia);
		return (List<AttackLevel>) query.list();
	}

}
