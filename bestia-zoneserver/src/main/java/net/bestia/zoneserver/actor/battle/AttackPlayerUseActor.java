package net.bestia.zoneserver.actor.battle;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.entity.Entity;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.messages.internal.entity.EntitySkillMessage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.battle.AttackService;

/**
 * This actor simply performs some safety checks for incoming player attack
 * messages and then returns a appropriate {@link EntitySkillMessage} back to
 * the sender which will use it to perform the skill/attack.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class AttackPlayerUseActor extends AbstractActor {

	public final static String NAME = "attackPlayerUse";

	//private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService playerEntityService;

	private final AttackService attackService;

	@Autowired
	public AttackPlayerUseActor(
			PlayerEntityService playerEntityService,
			AttackDAO attackDao,
			AttackService attackService) {

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.attackService = Objects.requireNonNull(attackService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AttackUseMessage.class, this::handleAttackMessage)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(AttackUseMessage.class);
		context().parent().tell(msg, getSelf());
	}

	/**
	 * Transforms the message from the player into usable message and replies it
	 * back.
	 * 
	 * @param msg
	 */
	private void handleAttackMessage(AttackUseMessage msg) {

		final Entity pbe = playerEntityService.getActivePlayerEntity(msg.getAccountId());

		// We must check if all preconditions for using the attack are
		// fulfilled.

		attackService.knowsAttack(pbe, msg.getAttackId());

		// Does the target of the attack matches the target provided in the
		// message?
		// If this is not the case by the user send data dont perform the
		// attack.
		final EntitySkillMessage skillMsg = new EntitySkillMessage(pbe.getId(),
				msg.getAttackId(),
				msg.getTargetEntityId());

		getSender().tell(skillMsg, getSelf());
	}
}
