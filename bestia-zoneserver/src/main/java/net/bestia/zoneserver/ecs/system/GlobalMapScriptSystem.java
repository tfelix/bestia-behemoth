package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.GlobalScript;
import net.bestia.zoneserver.script.MapScript;
import net.bestia.zoneserver.script.MapScriptAPI;
import net.bestia.zoneserver.zone.Zone;

/**
 * Executes the global mapscript once at startup of the zone. It then disables
 * itself.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class GlobalMapScriptSystem extends IteratingSystem {

	private ComponentMapper<GlobalScript> scriptMapper;

	@Wire
	private MapScriptAPI mapScriptApi;

	@Wire
	private CommandContext ctx;

	@Wire
	private Zone zone;

	public GlobalMapScriptSystem() {
		super(Aspect.all(GlobalScript.class));
		// no op
	}

	@Override
	protected void process(int entityId) {
		final String scriptName = scriptMapper.get(entityId).globalScriptName;

		final MapScript ms = new MapScript(zone.getName(), scriptName, mapScriptApi);

		ctx.getScriptManager().execute(ms);

		// Remove the entity.
		world.delete(entityId);
	}

	/**
	 * All entities where processed and it is not possible to attach global map
	 * scripts after the ECS has started. This system can now shut down.
	 */
	@Override
	protected void end() {
		setEnabled(false);
	}

}
