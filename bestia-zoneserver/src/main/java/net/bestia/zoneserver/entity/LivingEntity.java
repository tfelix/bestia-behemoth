package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.Position;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.InteractionType;
import net.bestia.model.entity.Visual;
import net.bestia.model.entity.VisualType;
import net.bestia.model.geometry.Collision;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.traits.Attackable;
import net.bestia.zoneserver.entity.traits.Collidable;
import net.bestia.zoneserver.entity.traits.Equipable;
import net.bestia.zoneserver.entity.traits.Interactable;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.entity.traits.Visible;

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
public class LivingEntity extends BaseEntity
		implements Locatable, Visible, Attackable, Collidable, Equipable, Interactable {

	private static final long serialVersionUID = 1L;

	/**
	 * Contains all the applied status effects.
	 */
	private Set<StatusEffect> statusEffects = new HashSet<>();
	private Position position = new Position();

	private Direction headFacing = Direction.SOUTH;
	private Visual sprite;
	private boolean isVisible = true;

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

	public LivingEntity(BaseValues baseValues, BaseValues effortValues, String spriteName) {
		this(baseValues, BaseValues.getNewIndividualValues(), effortValues, spriteName);
	}

	public LivingEntity(BaseValues baseValues, BaseValues ivs, BaseValues effortValues, String spriteName) {
		
		this.baseValues = Objects.requireNonNull(baseValues);
		this.ivs = Objects.requireNonNull(ivs);
		this.effortValues = Objects.requireNonNull(effortValues);

		this.sprite = new Visual(spriteName, VisualType.PACK);
	}

	public Direction getHeadFacing() {
		return headFacing;
	}

	public void setHeadFacing(Direction headFacing) {
		this.headFacing = headFacing;
	}

	/**
	 * Returns the maximum item weight the current bestia could carry. Please
	 * note: only the bestia master will be used to calculate the inventory max
	 * weight.
	 * 
	 * @return
	 */
	public int getMaxItemWeight() {
		final StatusPoints sp = getStatusPoints();
		return 100 + 100 * sp.getAtk() * 3 + getLevel();
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
	public Visual getVisual() {
		return sprite;
	}

	@Override
	public boolean isVisible() {
		return isVisible;
	}

	@Override
	public Point getPosition() {
		return new Point(position.getX(), position.getY());
	}

	@Override
	public void setPosition(long x, long y) {
		this.position.setX(x);
		this.position.setY(y);
		getContext().notifyPosition(this);
	}

	public void setPosition(Position pos) {
		this.position.set(pos);
		getContext().notifyPosition(this);
	}

	@Override
	public int getLevel() {
		return 1;
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
		// Dereferred calculate status points upon first request.
		if (baseStatusPoints == null) {
			calculateStatusPoints();
		}

		return baseStatusPoints;
	}

	@Override
	public void addStatusEffect(StatusEffect effect) {
		if (effect == null) {
			return;
		}
		statusEffects.add(effect);
	}

	@Override
	public void removeStatusEffect(StatusEffect effect) {
		if (effect == null) {
			return;
		}

		statusEffects.remove(effect);
	}

	@Override
	public Element getElement() {
		return Element.NORMAL;
	}

	@Override
	public void kill() {
		// TODO Auto-generated method stub

	}

	@Override
	public Collision getCollision() {
		return new Point(position.getX(), position.getY());
	}

	/**
	 * Basic living entities can usually not equip anything.
	 */
	@Override
	public boolean canEquip(Item item) {
		return false;
	}

	@Override
	public void equipItem(Item item) {
		// no op.
	}

	@Override
	public void unequipItem(Item item) {
		// no op.
	}

	@Override
	public Set<EquipmentSlot> getAvailableEquipmentSlots() {
		return Collections.emptySet();
	}

	@Override
	public void collide(Collidable collider) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<InteractionType> getInteractions(Interactable interacter) {
		return Collections.emptySet();
	}
}
