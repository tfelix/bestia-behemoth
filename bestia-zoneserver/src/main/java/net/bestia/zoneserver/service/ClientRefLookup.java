package net.bestia.zoneserver.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorRef;

@Service
public class ClientRefLookup {

	private static final String CLIENT_REF_KEY = "clientrefs";

	private final HazelcastInstance cache;

	@Autowired
	public ClientRefLookup(HazelcastInstance cache) {

		this.cache = Objects.requireNonNull(cache);
	}

	public void setActorRef(long accountId, ActorRef ref) {
		
		final ConcurrentMap<Long, ActorRef> refs = cache.getMap(CLIENT_REF_KEY);
		refs.put(accountId, ref);
	}

	public ActorRef getActorRef(long accountId) {

		final ConcurrentMap<Long, ActorRef> refs = cache.getMap(CLIENT_REF_KEY);
		return refs.get(accountId);
	}

	public void removeActorRef(long accountId) {
		final ConcurrentMap<Long, ActorRef> refs = cache.getMap(CLIENT_REF_KEY);
		refs.remove(accountId);
	}

}
