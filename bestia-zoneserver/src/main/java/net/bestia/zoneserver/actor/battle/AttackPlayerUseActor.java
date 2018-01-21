package net.bestia.zoneserver.actor.battle;

import java.util.Objects;

import net.bestia.messages.entity.EntitySkillUseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.entity.Entity;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor simply performs some safety checks for incoming player attack
 * messages and then returns a appropriate {@link EntitySkillUseMessage} back to
 * the sender which will use it to perform the skill/attack.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class AttackPlayerUseActor extends AbstractActor {

	public final static String NAME = "attackPlayerUse";

	private final PlayerEntityService playerEntityService;

	@Autowired
	public AttackPlayerUseActor(PlayerEntityService playerEntityService) {

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
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

		// Does the target of the attack matches the target provided in the
		// message?
		// If this is not the case by the user send data dont perform the
		// attack.
		final EntitySkillUseMessage skillMsg = new EntitySkillUseMessage(pbe.getId(),
				msg.getAttackId(),
				msg.getTargetEntityId());

		getSender().tell(skillMsg, getSelf());
	}
}
