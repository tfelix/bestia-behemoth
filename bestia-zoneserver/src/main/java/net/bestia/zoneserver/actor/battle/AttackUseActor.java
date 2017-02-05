package net.bestia.zoneserver.actor.battle;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * This actor just executes the attack. It can be used by npc entities as well as
 * of player entities.
 * 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class AttackUseActor extends BestiaRoutingActor {

	public final static String NAME = "attackUse";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);


	@Autowired
	public AttackUseActor() {
		super(Arrays.asList(AttackUseMessage.class));

	}

	@Override
	protected void handleMessage(Object msg) {

		final AttackUseMessage atkMsg = (AttackUseMessage) msg;
		

	}
}
