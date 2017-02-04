package net.bestia.model.service;

import org.springframework.stereotype.Service;

/**
 * This service can be used to control the tiles stored to the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class TileService {

	//private final static Logger LOG = LoggerFactory.getLogger(TileService.class);

	/**
	 * This will delete the whole map on the server.
	 * <p>
	 * <b>WARNING</b> this will delete the whole map. Afterwards a new map must
	 * be created.
	 * </p>
	 */
	public void deleteMap() {
		/*final Session session = sessionFactory.getCurrentSession();
		final String stringQuery = "DELETE FROM Tile";
		Query query = session.createQuery(stringQuery);
		query.executeUpdate();*/

	}

}
