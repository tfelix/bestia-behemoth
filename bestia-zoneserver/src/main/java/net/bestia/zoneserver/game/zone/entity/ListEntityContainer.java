package net.bestia.core.game.zone.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bestia.core.game.zone.BoxCollisionShape;
import net.bestia.core.game.zone.Dimension;
import net.bestia.core.game.zone.Entity;
import net.bestia.core.game.zone.Point;

/**
 * Very simple implementation of EntityContainer. Backed up by a simple list.
 * THIS IS FOR TESTING PURPOSES DONT USE IN PRODUCTION.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ListEntityContainer implements EntityContainer {

	private ArrayList<Entity> data = new ArrayList<Entity>();

	@Override
	public void insert(Entity entity) {
		data.add(entity);
	}

	@Override
	public void remove(Entity entity) {
		data.remove(entity);
	}

	@Override
	public Collection<Entity> getElements(Dimension range) {
		List<Entity> out = new ArrayList<Entity>();

		for (Entity e : data) {
			if(e.getCollision().collide(new BoxCollisionShape(range)));
		}

		return out;
	}

	@Override
	public Collection<Entity> getElements(Point p) {
		List<Entity> out = new ArrayList<Entity>();

		for (Entity e : data) {
			if (e.getCollision().collide(p)) {
				out.add(e);
			}
		}

		return out;
	}

	@Override
	public int count() {
		return data.size();
	}

}
