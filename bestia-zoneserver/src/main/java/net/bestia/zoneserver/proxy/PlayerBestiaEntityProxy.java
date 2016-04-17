package net.bestia.zoneserver.proxy;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.messages.entity.SpriteType;
import net.bestia.model.I18n;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.PlayerItem;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Damage;
import net.bestia.model.misc.Sprite.InteractionType;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

/**
 * The PlayerBestiaManager is responsible for executing the "business logic" to
 * a {@link PlayerBestia} DTO.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class PlayerBestiaEntityProxy extends BestiaEntityProxy {
	private final static Logger LOG = LogManager.getLogger(PlayerBestiaEntityProxy.class);
	private final static int MAX_LEVEL = 40;

	private final PlayerBestia bestia;

	private final StatusPoints statusPoints;
	private final String language;

	private final Entity entity;

	private UuidEntityManager uuidManager;
	private ComponentMapper<Attacks> attacksMapper;
	private Zoneserver server;
	private ComponentMapper<net.bestia.zoneserver.ecs.component.PlayerBestia> playerBestiaMapper;
	private CommandContext ctx;	
	private PlayerBestiaSpawnManager playerBestiaSpawnManager;
	
	private final String entityUUID;

	private Direction headFacing;

	public PlayerBestiaEntityProxy(
			World world,
			Entity entity,
			PlayerBestia playerBestia) {
		super(world, entity.getId());
		
		world.inject(this);

		this.entity = entity;

		// Get all the mapper.
		this.entityUUID = uuidManager.getUuid(entity).toString();
		this.bestia = playerBestia;
		this.headFacing = Direction.SOUTH;

		// Shortcut to the acc. language.
		this.language = bestia.getOwner().getLanguage().toString();
		this.statusPoints = new StatusPoints();

		// Setup all the references.
		statusMapper.get(entityID).statusPoints = this.statusPoints;
		//bestiaMapper.get(entityID).manager = this;
		playerBestiaMapper.get(entityID).playerBestia = this;

		final Visible visible = visibleMapper.get(entityID);
		visible.sprite = playerBestia.getOrigin().getSprite();
		visible.interactionType = InteractionType.GENERIC;
		visible.spriteType = SpriteType.PLAYER_ANIM;

		// Set the current position.
		final Position ecsPos = positionMapper.get(entityID);
		ecsPos.setLocationReference(bestia.getCurrentPosition());

		calculateStatusPoints();
	}

	/**
	 * Gets the status values depending on equipment and current bestia level.
	 * 
	 * @return The current status points.
	 */
	@Override
	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	@Override
	public int getLevel() {
		return bestia.getLevel();
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected void calculateStatusPoints() {

		final int atk = (bestia.getBaseValues().getAtk() * 2 + bestia.getIndividualValue().getAtk()
				+ bestia.getEffortValues().getAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int def = (bestia.getBaseValues().getDef() * 2 + bestia.getIndividualValue().getDef()
				+ bestia.getEffortValues().getDef() / 4) * bestia.getLevel() / 100 + 5;

		final int spatk = (bestia.getBaseValues().getSpAtk() * 2 + bestia.getIndividualValue().getSpAtk()
				+ bestia.getEffortValues().getSpAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int spdef = (bestia.getBaseValues().getSpDef() * 2 + bestia.getIndividualValue().getSpDef()
				+ bestia.getEffortValues().getSpDef() / 4) * bestia.getLevel() / 100 + 5;

		int spd = (bestia.getBaseValues().getSpd() * 2 + bestia.getIndividualValue().getSpd()
				+ bestia.getEffortValues().getSpd() / 4) * bestia.getLevel() / 100 + 5;

		final int maxHp = bestia.getBaseValues().getHp() * 2 + bestia.getIndividualValue().getHp()
				+ bestia.getEffortValues().getHp() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel();

		final int maxMana = bestia.getBaseValues().getMana() * 2 + bestia.getIndividualValue().getMana()
				+ bestia.getEffortValues().getMana() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel() * 2;

		statusPoints.setMaxValues(maxHp, maxMana);
		statusPoints.setCurrentHp(bestia.getCurrentHp());
		statusPoints.setCurrentMana(bestia.getCurrentMana());
		statusPoints.setAtk(atk);
		statusPoints.setDef(def);
		statusPoints.setSpAtk(spatk);
		statusPoints.setSpDef(spdef);
		statusPoints.setSpd(spd);
	}

	public void setAttacks(List<Integer> atkIds) {
		try {
			// TODO das ist sehr ungeschickt da hier die DB schon beschrieben
			// werden muss. Und beim persistieren noch mal.
			final PlayerBestiaService service = ctx.getServiceLocator().getBean(PlayerBestiaService.class);
			service.saveAttacks(bestia.getId(), atkIds);

			final Attacks attacksComp = attacksMapper.get(entityID);
			attacksComp.clear();
			attacksComp.addAll(atkIds);

		} catch (IllegalArgumentException ex) {
			// Attack can not be learned. Hacking?
			// no op.
		}
	}

	/**
	 * Uses an attack in a given slot. If the slot is not set then it will do
	 * nothing. Also if the requisites for execution of an attack (mana,
	 * cooldown etc.) are not met there will also be no effect. If the slot
	 * number is invalid an {@link IllegalArgumentException} will be thrown.
	 * Slot numbering starts with 0 for the first slot.
	 * 
	 * @param slot
	 *            Number of the slot attack to be used. Starts with 0.
	 */
	public boolean useAttack(int atkId) {

		final Attacks atks = attacksMapper.get(entityID);

		if (atks.hasAttack(atkId)) {
			// FIXME
			return useAttack(atkId);
		} else {
			// Attack ID not learned. Hacking?
			return false;
		}
	}

	/**
	 * Sends a system message to the owner of this bestia.
	 * 
	 * @param text
	 */
	private void sendSystemMessage(String text) {
		if (server == null) {
			// When null dont send any messages.
			return;
		}
		final ChatMessage msg = ChatMessage.getSystemMessage(bestia.getOwner(), text);
		server.sendMessage(msg);
	}

	/**
	 * Adds a certain amount of experience to the bestia. After this it checks
	 * if a levelup has occured. Experience must be positive.
	 * 
	 * @param exp
	 *            Experience to be added.
	 */
	public void addExp(int exp) {
		if (exp < 0) {
			LOG.warn("Exp can not be smaller then 0. Cancelling.");
			return;
		}

		// Send system message for chat.
		sendSystemMessage(I18n.t(language, "MSG.bestia_gained_exp", bestia.getName(), exp));

		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Checks if a level up has occured. If this is the case it will recalculate
	 * all the stats messages the user and recursively calls itself to check for
	 * multiple level ups at once.
	 * 
	 */
	private void checkLevelUp() {
		final int neededExp = getNeededExp();

		if (bestia.getExp() < neededExp || bestia.getLevel() >= MAX_LEVEL) {
			return;
		}

		bestia.setExp(bestia.getExp() - neededExp);
		bestia.setLevel(bestia.getLevel() + 1);

		// Send system message for chat.
		sendSystemMessage(I18n.t(language, "msg.bestia_reached_level", bestia.getName(), bestia.getLevel()));

		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();

		// Recalculate the new status values.
		calculateStatusPoints();

		// Refill HP and Mana.
		statusPoints.setCurrentHp(statusPoints.getCurrentHp());
		statusPoints.setCurrentMana(statusPoints.getCurrentMana());
	}

	/**
	 * Adds the damage to the player bestia and sends an update to the owner of
	 * it.
	 * 
	 * @param dmg
	 *            Simple amount of damage to be taken.
	 */
	public void takeDamage(int dmgValue) {

		// Spawn the damage display indicator in the ECS to let it get send to
		// all player in range.
		final Damage damage = Damage.getHit(entityUUID, dmgValue);

		final EntityDamageMessage dmgMsg = new EntityDamageMessage(0, damage);
		playerBestiaSpawnManager.sendMessageToSightrange(getEntityId(), dmgMsg);

		// Update our current hp.
		final int newHp = statusPoints.getCurrentHp() - dmgValue;

		if (newHp <= 0) {
			kill();
			statusPoints.setCurrentHp(0);
			return;
		}

		statusPoints.setCurrentHp(newHp);
	}

	/**
	 * Kills a player bestia.
	 */
	public void kill() {
		LOG.debug("Player bestia {} was killed.", getPlayerBestiaId());
	}

	/**
	 * Enrich the location with the map on which the bestia currently resides.
	 * This information is not known for at the general level.
	 */
	@Override
	public Location getLocation() {

		final Location loc = super.getLocation();

		final String curMap = bestia.getCurrentPosition().getMapDbName();
		loc.setMapDbName(curMap);

		return loc;
	}

	/**
	 * Sets the item shortcuts for this bestia.
	 * 
	 * @param itemIds
	 */
	public void setItems(List<Integer> itemIds) {

		try {
			final PlayerBestiaService service = ctx.getServiceLocator().getBean(PlayerBestiaService.class);
			final PlayerItem[] itemShortcuts = service.saveItemShortcuts(bestia.getId(), itemIds);

			bestia.setItem1(itemShortcuts[0]);
			bestia.setItem2(itemShortcuts[1]);
			bestia.setItem3(itemShortcuts[2]);
			bestia.setItem4(itemShortcuts[3]);
			bestia.setItem5(itemShortcuts[4]);

		} catch (IllegalArgumentException ex) {
			// Attack can not be learned. Hacking?
			// no op.
		}
	}

	public Direction getHeadFacing() {
		return headFacing;
	}

	public void setHeadFacing(Direction headFacing) {
		this.headFacing = headFacing;
	}

	/**
	 * Returns a list of attacks of the currently wrapped bestia.
	 * 
	 * @return
	 */
	public Collection<Integer> getAttackIds() {
		final Set<Integer> atks = new HashSet<>();

		for (int i = 1; i <= 5; i++) {
			Attack atk = null;
			switch (i) {
			case 1:
				atk = bestia.getAttack1();
				break;
			case 2:
				atk = bestia.getAttack2();
				break;
			case 3:
				atk = bestia.getAttack3();
				break;
			case 4:
				atk = bestia.getAttack4();
				break;
			case 5:
				atk = bestia.getAttack5();
				break;
			}

			if (atk == null) {
				continue;
			} else {
				atks.add(atk.getId());
			}
		}
		return atks;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getAccountId()
	 */
	public long getAccountId() {
		return bestia.getOwner().getId();
	}

	@Override
	public String toString() {
		return String.format("PlayerBestiaManager[%s]", bestia.toString());
	}

	@Override
	public int hashCode() {
		return bestia.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;

		final PlayerBestiaEntityProxy other = (PlayerBestiaEntityProxy) obj;

		if (bestia == null) {
			if (other.bestia != null)
				return false;
		} else if (!bestia.equals(other.bestia))
			return false;
		return true;
	}

	/**
	 * Shortcut to get the id of the wrapped bestia.
	 * 
	 * @return The id of the wrapped bestia.
	 */
	public int getPlayerBestiaId() {
		return bestia.getId();
	}

	/**
	 * Returns the underling {@link PlayerBestia} in order to persist the data
	 * etc. This should be used with care since directly changing values inside
	 * the PlayerBestia might not get into the ECS and thus setting the data
	 * between the ECS (the PlayerBesitaEntityProxy) and the data model out of
	 * sync.
	 * 
	 * @return The {@link PlayerBestia} backed by this proxy.
	 */
	public PlayerBestia getPlayerBestia() {
		return bestia;
	}

	/**
	 * Returns the maximum item weight the current bestia could carry. Plase
	 * note: only the bestia master will be used to calculate the inventory max
	 * weight.
	 * 
	 * @return
	 */
	public int getMaxItemWeight() {
		final StatusPoints sp = getStatusPoints();
		return 100 + 100 * sp.getAtk() * 3 + bestia.getLevel();
	}

	/**
	 * Calculates the needed experience until the next levelup.
	 * 
	 * @return Exp needed for next levelup.
	 */
	private int getNeededExp() {
		return (int) (Math.ceil(Math.exp(bestia.getLevel()) / 3) + 15);
	}

	/**
	 * Sets the current bestia active for receiving commands.
	 * 
	 * @param isActive
	 */
	public void setActive(boolean isActive) {
		if (isActive) {
			ctx.getAccountRegistry().setActiveBestia(getAccountId(), getPlayerBestiaId());
			entity.edit().create(Active.class);

		} else {
			ctx.getAccountRegistry().unsetActiveBestia(getAccountId(), getPlayerBestiaId());
			entity.edit().remove(Active.class);
		}
	}
}
