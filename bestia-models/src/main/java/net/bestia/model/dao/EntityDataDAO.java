package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.EntityData;

@Repository("entityDataDao")
public interface EntityDataDAO extends CrudRepository<EntityData, Long> {

}
