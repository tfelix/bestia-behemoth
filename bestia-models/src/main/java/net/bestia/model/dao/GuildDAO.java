package net.bestia.model.dao;

import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Guild;

@Repository
public interface GuildDAO extends CrudOptionalRepository<Guild, Integer> {
}