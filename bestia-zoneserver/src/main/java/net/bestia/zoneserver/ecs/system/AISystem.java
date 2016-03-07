package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.manager.NpcBestiaEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * The AI system will manage the current state of an entity, be responsible for evaluating the environment and if
 * necessary change the current state. The state itself is responsible for doing "actions" (walking, attacking etc).
 * This state must be highly flexible.
 * 
 * TODO: Gerade werden die AI Entities alle 3 Sekunden um 1 Felder bewegt.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class AISystem extends IntervalEntityProcessingSystem {

	private Random random = new Random();

	private ComponentMapper<NPCBestia> bestiaMapper;

	public AISystem() {
		super(Aspect.all(AI.class, NPCBestia.class), 7000);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		final NpcBestiaEntityProxy npcManager = bestiaMapper.get(e).manager;
		final Location pos = npcManager.getLocation();
		

		// Convert the strange JSON format to a path array.
		final List<Vector2> path = new ArrayList<>(1);
		Vector2 newPos;
		switch (random.nextInt(4)) {
		case 0:
			// Go top.
			if (pos.getY() == 0) {
				return;
			}
			newPos = new Vector2(pos.getX(), pos.getY() - 1);
			break;
		case 1:
			// go right.
			if (pos.getX() >= 50) {
				return;
			}
			newPos = new Vector2(pos.getX() + 1, pos.getY());
			break;
		case 2:
			// go bottom.
			if (pos.getY() >= 50) {
				return;
			}
			newPos = new Vector2(pos.getX(), pos.getY() + 1);
			pos.setY(pos.getY() + 1);
			break;
		case 3:
			// go left
			if (pos.getX() == 0) {
				return;
			}
			newPos = new Vector2(pos.getX() - 1, pos.getY());
			break;
		default:
			newPos = new Vector2(pos.getX(), pos.getY());
			break;
		}

		path.add(newPos);

		final Movement movement = e.edit().create(Movement.class);
		movement.path.addAll(path);
	}

}
