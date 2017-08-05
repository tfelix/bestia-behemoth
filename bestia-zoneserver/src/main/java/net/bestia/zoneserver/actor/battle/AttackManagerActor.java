package net.bestia.zoneserver.actor.battle;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.messages.internal.entity.EntitySkillMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.battle.BattleService;

/**
 * This manager provides the handling of attacks for the bestia system.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class AttackManagerActor extends AbstractActor {

	public final static String NAME = "attack";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final BattleService battleService;

	private ActorRef playerAttackUseActor;

	@Autowired
	public AttackManagerActor(BattleService battleService) {

		this.battleService = Objects.requireNonNull(battleService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AttackUseMessage.class, this::handleClientAttackMessage)
				.match(EntitySkillMessage.class, this::handleEntitySkillMessage)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		playerAttackUseActor = SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);
	}

	private void handleClientAttackMessage(AttackUseMessage msg) {
		playerAttackUseActor.tell(msg, getSelf());
	}

	/**
	 * Handles an attack by an entity to the ground or another entity.
	 * 
	 * @param msg
	 *            The message describing the attack.
	 */
	private void handleEntitySkillMessage(EntitySkillMessage msg) {

	}

}
