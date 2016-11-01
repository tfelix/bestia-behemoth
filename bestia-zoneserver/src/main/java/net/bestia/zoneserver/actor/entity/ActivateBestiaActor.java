package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.service.CacheManager;

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * needed information about the newly activated bestia is send to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ActivateBestiaActor extends BestiaRoutingActor {

	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(BestiaActivateMessage.class)));

	private final CacheManager<Long, Integer> activeBestias;

	@Autowired
	public ActivateBestiaActor(
			@Qualifier(CacheConfiguration.ACTIVE_BESTIA_CACHE) CacheManager<Long, Integer> activeBestias) {

		this.activeBestias = Objects.requireNonNull(activeBestias);
	}

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {

		final BestiaActivateMessage bestiaMsg = (BestiaActivateMessage) msg;

		// TODO Aus alter Logik übertragen und Entity aktivieren.
	}

}
