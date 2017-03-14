package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.MapData;

@Repository("itemDao")
public interface MapDataDAO extends CrudRepository<MapData, Long> {

}
