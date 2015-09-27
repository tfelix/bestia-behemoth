package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.zone.shape.Vector2;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

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

	private ComponentMapper<Position> positionMapper;

	public AISystem() {
		super(Aspect.all(AI.class, Position.class), 7000);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		Position pos = positionMapper.get(e);

		// Convert the strange JSON format to a path array.
		final List<Vector2> path = new ArrayList<>(1);
		Vector2 newPos;
		switch (random.nextInt(4)) {
		case 0:
			// Go top.
			if (pos.y == 0) {
				return;
			}
			newPos = new Vector2(pos.x, pos.y - 1);
			break;
		case 1:
			// go right.
			if (pos.x >= 50) {
				return;
			}
			newPos = new Vector2(pos.x + 1, pos.y);
			break;
		case 2:
			// go bottom.
			if (pos.y >= 50) {
				return;
			}
			newPos = new Vector2(pos.x, pos.y + 1);
			break;
		case 3:
			// go left
			if (pos.x == 0) {
				return;
			}
			newPos = new Vector2(pos.x - 1, pos.y);
			break;
		default:
			newPos = new Vector2(pos.x, pos.y);
			break;
		}

		path.add(newPos);

		Movement movement = e.edit().create(Movement.class);
		movement.path.addAll(path);
	}

}
