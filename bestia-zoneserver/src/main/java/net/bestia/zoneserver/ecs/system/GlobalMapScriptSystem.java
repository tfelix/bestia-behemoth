package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.GlobalScript;
import net.bestia.zoneserver.script.Script;
import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.script.ScriptBuilder;
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
	private ScriptApi mapScriptApi;

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

		final ScriptBuilder sb = new ScriptBuilder();
		final Script script = sb.setName(scriptName)
				.setApi(mapScriptApi)
				.setScriptPrefix(Script.SCRIPT_PREFIX_MAP)
				.build();

		ctx.getScriptManager().execute(script);

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
