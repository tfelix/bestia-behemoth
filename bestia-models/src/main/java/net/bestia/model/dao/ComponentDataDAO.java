package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.ComponentData;

@Repository("componentDataDao")
public interface ComponentDataDAO extends CrudRepository<ComponentData, Long> {

}
