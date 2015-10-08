package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaManager;

/**
 * The attack scripts are triggered and executed as soon as an attack is
 * triggered. They are responsible for dispatching an entity with the apropriate
 * effects, applay status changes etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackScript extends Script {

	/**
	 * Ctor.
	 * 
	 * @param name
	 *            The name of the attack script.
	 * @param owner
	 *            The bestia manager of the one who uses the attack.
	 */
	public AttackScript(String name, BestiaManager owner) {
		super(name);

		addBinding("owner", owner);
	}

	/**
	 * Std. ctor.
	 */
	public AttackScript() {
		// no op.
	}

	@Override
	protected String getScriptPreKey() {
		return "attack";
	}

}
