package net.bestia.zoneserver.script;

import javax.script.Invocable;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.battle.StatusBasedValueModifier;
import net.bestia.model.battle.StatusPointsModifier;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;

/**
 * This script class is used for scripts attached to equipments and status
 * effects. Both of them have the same API because of the use case.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusEffectScript extends Script {

	// private final String scriptName;
	private static final Logger LOG = LoggerFactory.getLogger(StatusEffectScript.class);

	public StatusEffectScript(String name) {
		super(name);
	}

	/*
	 * public Attack onBeforeAttack(Attack attack, Attackable self, Attackable
	 * enemy) {
	 * 
	 * }
	 * 
	 * public Damage onBeforeTakeDamage(Damage damage, Attackable self,
	 * Attackable enemy) {
	 * 
	 * }
	 * 
	 * public Damage onTakeDamage(Damage damage, Attackable self, Attackable
	 * enemy) {
	 * 
	 * }
	 * 
	 * public Attack onAttack(Attack attack, Attackable self, Attackable enemy)
	 * {
	 * 
	 * }
	 */

	public StatusPointsModifier onStatusPoints(StatusPoints originalStatus) {

		final StatusPointsModifier statusMods = new StatusPointsModifier();
		final Invocable invocable = getInvocable();

		try {
			invocable.invokeFunction("onStatusPoints", statusMods, originalStatus);
		} catch (NoSuchMethodException | ScriptException e) {
			LOG.error("Could not execute script function: onStatusPoints.", e);
		}

		return statusMods;
	}

	/**
	 * Called to generate the status based value modifier out of a custom
	 * scripted status effect.
	 * 
	 * @param status
	 *            The original status based values.
	 * @return The modifier of the status based values.
	 */
	public StatusBasedValueModifier onStatusBasedValues(StatusBasedValues status) {

		final StatusBasedValueModifier statusMods = new StatusBasedValueModifier();
		final Invocable invocable = getInvocable();

		try {
			invocable.invokeFunction("onStatusBasedValues", statusMods, status);
		} catch (NoSuchMethodException | ScriptException e) {
			LOG.error("Could not execute script function: onStatusBasedValues.", e);
		}

		return statusMods;
	}

	public void onDetach() {

	}

	public void onAttach() {

	}

	public void onTick() {

	}

	@Override
	protected ScriptType getScriptType() {
		return ScriptType.STATUS_EFFECT;
	}

}
