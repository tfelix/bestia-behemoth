package net.bestia.zoneserver.zone.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.bestia.zoneserver.zone.shape.Rect;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * CLass holds reference to multiple areas (shapes) and provide a facility to
 * efficiently get a random coordinate out of these multiple areas.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class SpawnLocation {

	private class PossibleSpawn {
		public final int value;
		public final Rect rect;

		public PossibleSpawn(int value, Rect rect) {
			this.value = value;
			this.rect = rect;
		}
	}

	private Random rand = new Random();
	private int maxValue = 0;

	private List<PossibleSpawn> areas = new ArrayList<>();

	/**
	 * Adds an area to the possible spawn locations.
	 * 
	 * @param rect
	 *            The new area as a spawn location.
	 */
	public void addArea(Rect rect) {

		final int area = getArea(rect);
		areas.add(new PossibleSpawn(area, rect));
		maxValue += getArea(rect);
	}

	/**
	 * Calculates a random new spawn coordinate from the added areas.
	 * 
	 * @return A random coordinate from the spawn areas.
	 */
	public Vector2 getSpawn() {

		int i = rand.nextInt(maxValue);
		int j = 0;

		while (i >= 0) {
			final PossibleSpawn ps = areas.get(j);
			i -= ps.value;
			j++;
		}

		final PossibleSpawn ps = areas.get(j);
		final int y = rand.nextInt(ps.rect.getHeight());
		final int x = rand.nextInt(ps.rect.getWidth());

		return new Vector2(ps.rect.getX() + x, ps.rect.getY() + y);
	}

	/**
	 * Gets the area out of the shape.
	 * 
	 * @param rect
	 * @return
	 */
	private int getArea(Rect rect) {
		return rect.getHeight() * rect.getWidth();
	}
}
