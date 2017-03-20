package net.bestia.zoneserver.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.bestia.messages.entity.EntityAnimationMessage;
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.battle.StatusBasedValueModifier;
import net.bestia.model.battle.StatusBasedValuesDecorator;
import net.bestia.model.battle.StatusPointsDecorator;
import net.bestia.model.battle.StatusPointsModifier;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.entity.StatusBasedValuesImpl;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.traits.Equipable;
import net.bestia.zoneserver.entity.traits.Loadable;
import net.bestia.zoneserver.script.StatusEffectScript;

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
	private StatusPointsDecorator baseStatusPointModified;

	private StatusBasedValues statusBasedValues;
	private StatusBasedValuesDecorator statusBasedValuesModified;

	private final List<StatusEffectScript> statusEffectsScripts = new ArrayList<>();
	private final List<StatusEffect> statusEffects = new ArrayList<>();

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
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected void calculateStatusPoints() {

		baseStatusPoints = new StatusPointsImpl();

		final int atk = (baseValues.getAttack() * 2 + ivs.getAttack()
				+ effortValues.getAttack() / 4) * getLevel() / 100 + 5;

		final int def = (baseValues.getVitality() * 2 + ivs.getVitality()
				+ effortValues.getVitality() / 4) * getLevel() / 100 + 5;

		final int spatk = (baseValues.getIntelligence() * 2 + ivs.getIntelligence()
				+ effortValues.getIntelligence() / 4) * getLevel() / 100 + 5;

		final int spdef = (baseValues.getWillpower() * 2 + ivs.getWillpower()
				+ effortValues.getWillpower() / 4) * getLevel() / 100 + 5;

		int spd = (baseValues.getAgility() * 2 + ivs.getAgility()
				+ effortValues.getAgility() / 4) * getLevel() / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp()
				+ effortValues.getHp() / 4 * getLevel() / 100 + 10 + getLevel();

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana()
				+ effortValues.getMana() / 4 * getLevel() / 100 + 10 + getLevel() * 2;

		baseStatusPoints.setMaxHp(maxHp);
		baseStatusPoints.setMaxMana(maxMana);
		baseStatusPoints.setStrenght(atk);
		baseStatusPoints.setVitality(def);
		baseStatusPoints.setIntelligence(spatk);
		baseStatusPoints.setMagicDefense(spdef);
		baseStatusPoints.setAgility(spd);

		baseStatusPointModified = new StatusPointsDecorator(baseStatusPoints);
		baseStatusPointModified.clearModifier();

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusPointsModifier mod = statScript.onStatusPoints(baseStatusPoints);
			baseStatusPointModified.addModifier(mod);
		}

		statusBasedValues = new StatusBasedValuesImpl(baseStatusPointModified, getLevel());
		statusBasedValuesModified = new StatusBasedValuesDecorator(statusBasedValues);
		statusBasedValuesModified.clearModifier();

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusBasedValueModifier mod = statScript.onStatusBasedValues(statusBasedValues);
			statusBasedValuesModified.addStatusModifier(mod);
		}
	}

	@Override
	public StatusPoints getStatusPoints() {
		// Dereferred calculate status points upon first request.
		if (baseStatusPointModified == null) {
			calculateStatusPoints();
		}

		return baseStatusPointModified;
	}

	@Override
	public StatusPoints getOriginalStatusPoints() {

		return baseStatusPoints;
	}

	@Override
	public void addStatusEffect(StatusEffect effect) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeStatusEffect(StatusEffect effect) {
		statusEffects.remove(effect);
	}

	@Override
	public List<StatusEffect> getStatusEffects() {
		return Collections.unmodifiableList(statusEffects);
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
		super.kill();
		// Send death animation to client.
		final EntityAnimationMessage animMsg = new EntityAnimationMessage(getId(), "die");
		getContext().sendMessage(animMsg);
	}

	@Override
	public Damage takeDamage(Damage damage) {
		// TODO Den Schaden richtig verrechnen.
		int curHp = getStatusPoints().getCurrentHp();
		
		// Send the message to all clients in visible range.
		getContext().sendMessage(new EntityDamageMessage(getId(), damage));
		
		if (curHp - damage.getDamage() > 0) {
			getStatusPoints().setCurrentHp(curHp - damage.getDamage());
			
		} else {
			kill();
		}

		return damage;
	}

	@Override
	public Damage checkDamage(Damage damage) {
		// TODO Auto-generated method stub
		return damage;
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
	public float getMovementSpeed() {
		return statusBasedValues.getWalkspeed() / 100f;
	}

	@Override
	public void setLevel(int level) {
		super.setLevel(level);

		statusBasedValues.setLevel(level);
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
