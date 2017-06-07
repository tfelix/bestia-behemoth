package net.bestia.zoneserver.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.entity.EntityStatusTickActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.StatusComponent;

/**
 * The {@link StatusComponentInterceptor} creates updating entity actor which
 * will call tick jobs for regeneration and it will also perform updates to the
 * client if a value of a status has changed.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class StatusComponentInterceptor extends ComponentInterceptor<StatusComponent>  {
	
	private static final Logger LOG = LoggerFactory.getLogger(StatusComponentInterceptor.class);
	
	private final ZoneAkkaApi actorApi;

	@Autowired
	public StatusComponentInterceptor(ZoneAkkaApi actorApi) {
		super(StatusComponent.class);
		
		this.actorApi = Objects.requireNonNull(actorApi);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, StatusComponent comp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, StatusComponent comp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, StatusComponent comp) {
		
		LOG.trace("StatusComponent created.");
		
		final ActorRef tickActor = actorApi.startUnnamedActor(EntityStatusTickActor.class);
		tickActor.tell(entity.getId(), ActorRef.noSender());
		
	}

}
