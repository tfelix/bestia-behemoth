package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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

	public final static String NAME = "activateBestia";
	private final CacheManager<Long, Integer> activeBestias;

	@Autowired
	public ActivateBestiaActor(
			@Qualifier(CacheConfiguration.ACTIVE_BESTIA_CACHE) CacheManager<Long, Integer> activeBestias) {
		super(Arrays.asList(BestiaActivateMessage.class));
		this.activeBestias = Objects.requireNonNull(activeBestias);
	}

	@Override
	protected void handleMessage(Object msg) {

		final BestiaActivateMessage bestiaMsg = (BestiaActivateMessage) msg;

		// TODO Aus alter Logik übertragen und Entity aktivieren.
	}

}
