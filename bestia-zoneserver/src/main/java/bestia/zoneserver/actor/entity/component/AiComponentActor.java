package bestia.zoneserver.actor.entity.component;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;

/**
 * At the current implementation this actor will only periodically start a short
 * movement for the entity at a random interval.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class AiComponentActor extends AbstractActor {

	@Override
	public Receive createReceive() {
		// TODO Auto-generated method stub
		return null;
	}

}
