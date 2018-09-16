package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Guild;

@Repository
public interface GuildDAO extends CrudRepository<Guild, Integer> {
}