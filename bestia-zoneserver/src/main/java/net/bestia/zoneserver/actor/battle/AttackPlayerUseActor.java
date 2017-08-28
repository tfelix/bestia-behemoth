package net.bestia.zoneserver.actor.battle;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.messages.internal.entity.EntitySkillMessage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackImpl;
import net.bestia.model.domain.AttackTarget;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.battle.AttackService;
import net.bestia.zoneserver.battle.BattleService;

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

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService playerEntityService;

	private final AttackService attackService;
	private final AttackDAO attackDao;

	@Autowired
	public AttackPlayerUseActor(
			PlayerEntityService playerEntityService,
			AttackDAO attackDao,
			AttackService attackService) {

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.attackService = Objects.requireNonNull(attackService);
		this.attackDao = Objects.requireNonNull(attackDao);
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

	private void handleAttackMessage(AttackUseMessage msg) {

		final Entity pbe = playerEntityService.getActivePlayerEntity(msg.getAccountId());

		// We must check if all preconditions for using the attack are
		// fulfilled.
		final Attack usedAttack;

		// Some special handling for basic attacks.
		if (msg.getAttackId() == BattleService.DEFAULT_MELEE_ATTACK_ID) {

			usedAttack = AttackImpl.getDefaultMeleeAttack();

		} else if (msg.getAttackId() == BattleService.DEFAULT_RANGE_ATTACK_ID) {

			usedAttack = AttackImpl.getDefaultMeleeAttack();

		} else {

			// Check if the bestia owns this attack.
			if (!attackService.knowsAttack(pbe, msg.getAttackId())) {
				LOG.warning("Player {} does now know attack: {}", pbe, msg);
				return;
			}

			usedAttack = attackDao.findOne(msg.getAttackId());
			if (usedAttack == null) {
				LOG.warning("Attack not found: {}.", msg);
				return;
			}
		}

		// Does the target of the attack matches the target provided in the
		// message?
		// If this is not the case by the user send data dont perform the
		// attack.
		final EntitySkillMessage skillMsg;
		final AttackTarget atkTarget = usedAttack.getTarget();

		switch (atkTarget) {
		case ENEMY_ENTITY:
		case FRIENDLY_ENTITY:
		case SELF:
			// if an entity was targeted a entity id must be provided in the
			// message.
			if (msg.getTargetEntityId() <= 0) {
				LOG.warning("");
			}
			skillMsg = new EntitySkillMessage(pbe.getId(), usedAttack.getId(), msg.getTargetEntityId());
			break;
		case GROUND:
			final Point targetPoint = new Point(msg.getX(), msg.getY());
			skillMsg = new EntitySkillMessage(pbe.getId(), usedAttack.getId(), targetPoint);
			break;
		default:
			LOG.warning("No valid target for the attack was found.");
			return;
		}

		getSender().tell(skillMsg, getSelf());
	}
}
