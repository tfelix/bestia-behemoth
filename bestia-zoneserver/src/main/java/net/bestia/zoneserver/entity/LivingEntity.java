package net.bestia.zoneserver.entity;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.bestia.messages.entity.AnimationPlayMessage;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.entity.traits.Equipable;
import net.bestia.zoneserver.entity.traits.Loadable;

/**
 * <p>
 * Base entity used by the bestia system to represent all game objects inside
 * the "game" or zone-graph. This class represents interactable entities which
 * can be attacked, positioned, seen etc.
 * </p>
 * <p>
 * Derived from this entity there are multiple objects which contains date to be
 * able to interact with each other or to provide functionality to add status
 * effects etc. to it.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class LivingEntity extends ResourceEntity implements Equipable, Loadable {

	private static final long serialVersionUID = 1L;

	private Direction headFacing = Direction.SOUTH;

	private final BaseValues baseValues;
	private final BaseValues ivs;
	private final BaseValues effortValues;

	/**
	 * Contains the unmodified (by equip or effects) base status points.
	 */
	private StatusPoints baseStatusPoints;

	/**
	 * Contains the modified (by equip of effects) status points.
	 */
	private StatusPoints modifiedStatusPoints;

	public LivingEntity(BaseValues baseValues, BaseValues ivs, BaseValues effortValues, SpriteInfo visual) {

		this.baseValues = Objects.requireNonNull(baseValues);
		this.ivs = Objects.requireNonNull(ivs);
		this.effortValues = Objects.requireNonNull(effortValues);

		setVisual(visual);
	}

	public Direction getHeadFacing() {
		return headFacing;
	}

	public void setHeadFacing(Direction headFacing) {
		this.headFacing = headFacing;
	}

	/**
	 * Re-calculates the current HP and mana regeneration rate based on stats.
	 */
	protected void calculateRegenerationRates() {
		final StatusPoints statusPoints = getStatusPoints();

		final int level = getLevel();
		final float hpRegen = (statusPoints.getDef() * 4 + statusPoints.getSpDef() * 1.5f + level) / 100.0f;

		final float manaRegen = (statusPoints.getDef() * 1.5f + statusPoints.getSpDef() * 3 + level) / 100.0f;

		statusPoints.setHpRegenerationRate(hpRegen);
		statusPoints.setManaRegenenerationRate(manaRegen);
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected void calculateStatusPoints() {

		final int atk = (baseValues.getAtk() * 2 + ivs.getAtk()
				+ effortValues.getAtk() / 4) * getLevel() / 100 + 5;

		final int def = (baseValues.getDef() * 2 + ivs.getDef()
				+ effortValues.getDef() / 4) * getLevel() / 100 + 5;

		final int spatk = (baseValues.getSpAtk() * 2 + ivs.getSpAtk()
				+ effortValues.getSpAtk() / 4) * getLevel() / 100 + 5;

		final int spdef = (baseValues.getSpDef() * 2 + ivs.getSpDef()
				+ effortValues.getSpDef() / 4) * getLevel() / 100 + 5;

		int spd = (baseValues.getSpd() * 2 + ivs.getSpd()
				+ effortValues.getSpd() / 4) * getLevel() / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp()
				+ effortValues.getHp() / 4 * getLevel() / 100 + 10 + getLevel();

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana()
				+ effortValues.getMana() / 4 * getLevel() / 100 + 10 + getLevel() * 2;

		baseStatusPoints = new StatusPoints();
		baseStatusPoints.setMaxValues(maxHp, maxMana);
		baseStatusPoints.setAtk(atk);
		baseStatusPoints.setDef(def);
		baseStatusPoints.setSpAtk(spatk);
		baseStatusPoints.setSpDef(spdef);
		baseStatusPoints.setSpd(spd);
	}

	protected void calculateModifiedStatusPoints() {
		calculateStatusPoints();
		modifiedStatusPoints = baseStatusPoints;
	}

	@Override
	public StatusPoints getStatusPoints() {
		// Dereferred calculate status points upon first request.
		if (modifiedStatusPoints == null) {
			calculateModifiedStatusPoints();
		}
		return modifiedStatusPoints;
	}

	@Override
	public StatusPoints getOriginalStatusPoints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addStatusEffect(StatusEffect effect) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeStatusEffect(StatusEffect effect) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<StatusEffect> getStatusEffects() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getElement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getOriginalElement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void kill() {
		// Send death animation to client.
		final AnimationPlayMessage animMsg = new AnimationPlayMessage(0, "die", getId());
		getContext().sendMessage(animMsg);
	}

	@Override
	public Damage takeDamage(Damage damage) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Damage checkDamage(Damage damage) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void takeTrueDamage(Damage damage) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canEquip(Item item) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void equip(Item item) {
		// TODO Auto-generated method stub

	}

	@Override
	public void takeOff(Item item) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<EquipmentSlot> getAvailableEquipmentSlots() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CollisionShape getShape() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setShape(CollisionShape shape) {
		// TODO Auto-generated method stub

	}

	@Override
	public float getMovementSpeed() {
		return 1.0f;
	}

	/**
	 * Moves the entity a certain path. This will trigger a message to the actor
	 * system.
	 */
	@Override
	public void moveTo(List<Point> path) {
		Objects.requireNonNull(path);

		final EntityMoveInternalMessage moveMsg = new EntityMoveInternalMessage(getId(), path);
		getContext().sendMessage(moveMsg);
	}
}
