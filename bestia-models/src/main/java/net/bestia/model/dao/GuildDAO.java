package net.bestia.model.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Guild;

@Repository
public interface GuildDAO extends org.springframework.data.repository.Repository<Guild, Integer> {
	Optional<Guild> findOne(Integer id);
}