package net.bestia.model.domain;

/**
 * Standard location for bestias on the world. This is an interface because we
 * need to do some tricky stuff and proxy implementations especially in the ECS.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Location {

	/**
	 * The map database name of the bestia.
	 * 
	 * @return Unique map database name.
	 */
	String getMapDbName();

	/**
	 * Sets the map database name.
	 * 
	 * @param mapDbName
	 *            New map database name.
	 */
	void setMapDbName(String mapDbName);

	/**
	 * X cord
	 * 
	 * @return X Cord.
	 */
	int getX();

	/**
	 * Sets X cord.
	 * 
	 * @param x
	 *            New x cord.
	 */
	void setX(int x);

	/**
	 * Gets Y cord.
	 * 
	 * @return Y cord.
	 */
	int getY();

	/**
	 * Sets Y cord.
	 * 
	 * @param y
	 *            New y cord.
	 */
	void setY(int y);

	/**
	 * Sets both: X and Y at the same time.
	 * 
	 * @param x
	 * @param y
	 */
	void setPos(int x, int y);

	/**
	 * Sets to the same location and map as the given location object.
	 * 
	 * @param loc
	 *            Sets to this coordinates and map.
	 */
	void set(Location loc);
}