package bestia.model.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import bestia.model.domain.Attack;
import bestia.model.domain.AttackTarget;
import bestia.model.domain.AttackType;
import bestia.model.domain.Element;

/**
 * Wraps/decorates an attack to modify its returned values.
 * 
 * @author Thomas Felix
 *
 */
public class AttackDecorator implements Attack {

	private final Attack wrappedAttack;

	private AttackTarget atkTarget = null;
	private String dbName = null;
	private Element element = null;
	private Boolean lineOfSight = null;
	private AttackType type = null;

	private final List<AttackModifier> modifier = new ArrayList<>();

	public AttackDecorator(Attack wrap) {

		this.wrappedAttack = Objects.requireNonNull(wrap);
	}

	public void addAttackMod(AttackModifier mod) {

		modifier.add(Objects.requireNonNull(mod));
	}

	@Override
	public AttackTarget getTarget() {
		if (atkTarget == null) {
			return wrappedAttack.getTarget();
		}

		return atkTarget;
	}

	@Override
	public String getDatabaseName() {
		if (dbName == null) {
			return wrappedAttack.getDatabaseName();
		}

		return dbName;
	}

	@Override
	public int getStrength() {

		final float strengthMod = (float) modifier.stream()
				.mapToDouble(d -> d.getStrengthMod())
				.sum();

		final int strengthValue = modifier.stream()
				.mapToInt(d -> d.getStrengthValue())
				.sum();

		return Math.round(wrappedAttack.getStrength() * strengthMod) + strengthValue;
	}

	@Override
	public Element getElement() {

		if (element == null) {
			return wrappedAttack.getElement();
		}

		return element;
	}

	@Override
	public int getManaCost() {

		final float manahMod = (float) modifier.stream()
				.mapToDouble(AttackModifier::getManaCostMod)
				.sum();

		final int manaValue = modifier.stream()
				.mapToInt(AttackModifier::getManaCostValue)
				.sum();

		return Math.round(wrappedAttack.getManaCost() * manahMod) + manaValue;
	}

	@Override
	public boolean needsLineOfSight() {

		if (lineOfSight == null) {
			return wrappedAttack.needsLineOfSight();
		}

		return lineOfSight;
	}

	@Override
	public AttackType getType() {

		if (type == null) {
			return wrappedAttack.getType();
		}

		return type;
	}

	@Override
	public int getCasttime() {

		final float cooldownMod = (float) modifier.stream()
				.mapToDouble(AttackModifier::getCooldownMod)
				.sum();

		final int cooldownValue = modifier.stream()
				.mapToInt(AttackModifier::getCooldownValue)
				.sum();

		return Math.round(wrappedAttack.getCooldown() * cooldownMod) + cooldownValue;
	}

	@Override
	public int getRange() {

		final float rangeMod = (float) modifier.stream()
				.mapToDouble(AttackModifier::getRangeMod)
				.sum();

		final int rangeValue = modifier.stream()
				.mapToInt(AttackModifier::getRangeValue)
				.sum();

		return Math.round(wrappedAttack.getRange() * rangeMod) + rangeValue;
	}

	@Override
	public int getCooldown() {

		final float cooldownMod = (float) modifier.stream()
				.mapToDouble(AttackModifier::getCooldownMod)
				.sum();

		final int cooldownValue = modifier.stream()
				.mapToInt(AttackModifier::getCooldownValue)
				.sum();

		return Math.round(wrappedAttack.getCooldown() * cooldownMod) + cooldownValue;
	}

	@Override
	public int getId() {
		return wrappedAttack.getId();
	}

	@Override
	public String getIndicator() {
		return wrappedAttack.getIndicator();
	}

	@Override
	public boolean hasScript() {
		return wrappedAttack.hasScript();
	}
}
