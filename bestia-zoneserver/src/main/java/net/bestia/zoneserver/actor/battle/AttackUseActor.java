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
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.messages.internal.entity.EntitySkillMessage;
import net.bestia.model.battle.Damage;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.battle.BattleService;

/**
 * Transforms the incoming player attack message into a entity skill message
 * after some sanity checks.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class AttackUseActor extends AbstractActor {

	public final static String NAME = "attackUse";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final BattleService battleService;
	private final ActorRef clientSendActor;

	@Autowired
	public AttackUseActor(BattleService battleService) {

		this.battleService = Objects.requireNonNull(battleService);
		this.clientSendActor = SpringExtension.actorOf(getContext(), AttackPlayerUseActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AttackUseMessage.class, this::handleAttackMessage)
				.match(EntitySkillMessage.class, this::handleEntitySkillMessage)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(AttackUseMessage.class, EntitySkillMessage.class);
		context().parent().tell(msg, getSelf());
	}

	/**
	 * This message is received directly from the clients and must be translated
	 * first.
	 * 
	 * @param msg
	 */
	private void handleAttackMessage(AttackUseMessage msg) {
		LOG.debug("Received essage: {}.", msg);
		clientSendActor.tell(msg, getSelf());
	}

	/**
	 * Handles an attack by an entity to the ground or another entity.
	 * 
	 * @param msg
	 *            The message describing the attack.
	 */
	private void handleEntitySkillMessage(EntitySkillMessage msg) {
		LOG.debug("Received skill message: {}", msg);

		if (msg.getTargetEntityId() != 0) {
			// Entity was targeted.
			final Damage dmg = battleService.attackEntity(msg.getAttackId(), 
					msg.getSourceEntityId(),
					msg.getTargetEntityId());
			
			final EntityDamageMessage dmgMsg = new EntityDamageMessage(msg.getTargetEntityId(), dmg);
			AkkaSender.sendActiveInRangeClients(getContext(), dmgMsg);

		} else {
			LOG.warning("Attackmode Currently not supported.");
		}
	}
}
