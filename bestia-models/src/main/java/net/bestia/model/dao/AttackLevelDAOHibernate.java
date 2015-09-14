package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.AttackLevel;
import net.bestia.model.domain.Bestia;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("attackLevelDao")
@Transactional(readOnly = true)
public class AttackLevelDAOHibernate extends GenericDAOHibernate<AttackLevel, Integer> implements AttackLevelDAO {

	@SuppressWarnings("unchecked")
	@Override
	public List<AttackLevel> getAllAttacksForBestia(Bestia bestia) {
		
		final Query query = currentSession()
				.createQuery("from AttackLevel al where al.bestia.id = :bestiaId order by al.minLevel asc");
		query.setParameter("bestiaId", bestia.getId());
		return (List<AttackLevel>) query.list();
	}

}
