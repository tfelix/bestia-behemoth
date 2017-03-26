package net.bestia.model.battle;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import net.bestia.model.domain.StatusPoints;

/**
 * This implements the {@link StatusPoints} interface but this class allows to
 * add {@link StatusPointsModifier} which will modify the status upon returning.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusPointsDecorator implements StatusPoints, Serializable {

	private static final long serialVersionUID = 1L;

	private final StatusPoints wrapped;

	private List<StatusPointsModifier> statusMods = new ArrayList<>();

	/**
	 * Ctor. Wrapps the given {@link StatusPoints} object and allows the
	 * modification of the underlying status points via adding of
	 * {@link StatusPointsModifier}.
	 * 
	 * @param wrapped
	 *            The wrapped {@link StatusPoints} object.
	 */
	public StatusPointsDecorator(StatusPoints wrapped) {

		this.wrapped = Objects.requireNonNull(wrapped);
	}

	public void addModifier(StatusPointsModifier mod) {
		statusMods.add(Objects.requireNonNull(mod));
	}

	public void removeModifier(StatusPointsModifier mod) {
		statusMods.remove(mod);
	}

	private float sumFloat(ToDoubleFunction<? super StatusPointsModifier> func) {
		
		if(statusMods.size() == 0) {
			return 1f;
		}
		
		return (float) statusMods.stream()
				.mapToDouble(func)
				.reduce(1, (a, b) -> a * b);
	}

	private int sumInt(ToIntFunction<? super StatusPointsModifier> func) {
		
		return statusMods.stream()
				.mapToInt(func)
				.sum();
	}

	@Override
	public int getCurrentHp() {
		return wrapped.getCurrentHp();
	}

	@Override
	public int getMaxHp() {

		final int val = sumInt(StatusPointsModifier::getMaxHpValue);
		final float mod = sumFloat(StatusPointsModifier::getMaxHpValue);

		return Math.round(wrapped.getMaxHp() * mod) + val;
	}

	@Override
	public int getCurrentMana() {
		return wrapped.getCurrentMana();
	}

	@Override
	public int getMaxMana() {

		final int val = sumInt(StatusPointsModifier::getMaxManaValue);
		final float mod = sumFloat(StatusPointsModifier::getMaxManaMod);

		return Math.round(wrapped.getMaxMana() * mod) + val;
	}

	@Override
	public int getDefense() {

		final int val = sumInt(StatusPointsModifier::getDefenseValue);
		final float mod = sumFloat(StatusPointsModifier::getDefenseMod);

		return Math.round(wrapped.getDefense() * mod) + val;
	}

	@Override
	public int getMagicDefense() {

		final int val = sumInt(StatusPointsModifier::getMagicDefenseValue);
		final float mod = sumFloat(StatusPointsModifier::getMagicDefenseMod);

		return Math.round(wrapped.getMagicDefense() * mod) + val;
	}

	@Override
	public int getStrength() {

		final int val = sumInt(StatusPointsModifier::getStrengthValue);
		final float mod = sumFloat(StatusPointsModifier::getStrengthMod);

		return Math.round(wrapped.getStrength() * mod) + val;
	}

	@Override
	public int getVitality() {
		
		final int val = sumInt(StatusPointsModifier::getVitalityValue);
		final float mod = sumFloat(StatusPointsModifier::getVitalityMod);

		return Math.round(wrapped.getVitality() * mod) + val;
	}

	@Override
	public int getIntelligence() {

		final int val = sumInt(StatusPointsModifier::getIntelligenceValue);
		final float mod = sumFloat(StatusPointsModifier::getIntelligenceMod);

		return Math.round(wrapped.getIntelligence() * mod) + val;
	}

	@Override
	public int getAgility() {

		final int val = sumInt(StatusPointsModifier::getAgilityValue);
		final float mod = sumFloat(StatusPointsModifier::getAgilityMod);

		return Math.round(wrapped.getAgility() * mod) + val;
	}

	@Override
	public int getWillpower() {

		final int val = sumInt(StatusPointsModifier::getWillpowerValue);
		final float mod = sumFloat(StatusPointsModifier::getWillpowerMod);

		return Math.round(wrapped.getWillpower() * mod) + val;
	}

	@Override
	public int getDexterity() {

		final int val = sumInt(StatusPointsModifier::getDexterityValue);
		final float mod = sumFloat(StatusPointsModifier::getDexterityMod);

		return Math.round(wrapped.getDexterity() * mod) + val;
	}

	public void setDexterity(int dexterity) {
		wrapped.setDexterity(dexterity);
	}

	public void setWillpower(int willpower) {
		wrapped.setWillpower(willpower);
	}

	public void setAgility(int agi) {
		wrapped.setAgility(agi);
	}

	public void setIntelligence(int intel) {
		wrapped.setIntelligence(intel);
	}

	public void setVitality(int vit) {
		wrapped.setVitality(vit);
	}

	public void setStrenght(int str) {
		wrapped.setStrenght(str);
	}

	public void setMagicDefense(int mdef) {
		wrapped.setMagicDefense(mdef);
	}

	public void setDefense(int def) {
		wrapped.setDefense(def);
	}

	public void setMaxMana(int maxMana) {
		wrapped.setMaxMana(maxMana);
	}

	public void setCurrentMana(int mana) {
		wrapped.setCurrentMana(mana);
	}

	public void setMaxHp(int maxHp) {
		wrapped.setMaxHp(maxHp);
	}

	public void setCurrentHp(int hp) {
		wrapped.setCurrentHp(hp);
	}

	public void clearModifier() {
		statusMods.clear();
	}

	@Override
	public String toString() {
		return "StatusPointsDecorator[]";
	}
}
