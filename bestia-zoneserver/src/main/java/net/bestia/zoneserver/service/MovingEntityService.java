package net.bestia.zoneserver.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorRef;

/**
 * This manager holds references of currently moving entities and their movement
 * managing actors in order to control movement after it has been triggered. It
 * also moves entities to different places and does so in an transactional
 * manner.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class MovingEntityService {

	private static final String MOVEMENT_KEY = "entity.moving";

	private final HazelcastInstance cache;

	@Autowired
	public MovingEntityService(HazelcastInstance cache) {

		this.cache = Objects.requireNonNull(cache);
	}

	public void setMovingActorRef(long entityId, ActorRef ref) {
		final Map<Long, ActorRef> refs = cache.getMap(MOVEMENT_KEY);
		refs.put(entityId, ref);
	}

	public void removeMovingActorRef(long entityId) {
		final Map<Long, ActorRef> refs = cache.getMap(MOVEMENT_KEY);
		refs.remove(entityId);
	}

	public ActorRef getMovingActorRef(long entityId) {
		final Map<Long, ActorRef> refs = cache.getMap(MOVEMENT_KEY);
		return refs.get(entityId);
	}
	
	public void moveTo(long entityId, long x, long y) {
		// not implemented.
	}
}
