package net.bestia.entity.recycler;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

@Component
public class StatusComponentRecycler extends ComponentRecycler<StatusComponent> {

	private final ZoneAkkaApi akkaApi;

	@Autowired
	public StatusComponentRecycler(EntityService entityService, ZoneAkkaApi akkaApi) {
		super(entityService, StatusComponent.class);

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void doFreeComponent(StatusComponent component) {

		// Stop the actor.
		akkaApi.sendEntityActor(component.getEntityId(), msg);

		entityService.deleteComponent(component);
	}

}
