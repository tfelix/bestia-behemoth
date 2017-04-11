package net.bestia.zoneserver.map;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.map.MapDataPart;

/**
 * Implementation of the {@link MapGenDAO} to the Hazelcast system for map
 * creation purposes. The {@link MapDataPart}s are currently saved in an temp
 * directory in the local filesystem.
 * 
 * @author Thomas Felix
 *
 */
@Repository
public class HazelcastMapGenDAO implements MapGenDAO {

	private static final String MASTER_STORE = "mapgen_master_store";
	private static final String CLIENT_STORE = "mapgen_client_store";

	private IMap<String, Object> masterStore;
	private IMap<String, Object> clientStore;

	private final String nodeName;
	private final MapGenDAO localFileDao;

	@Autowired
	public HazelcastMapGenDAO(@Value("${server.name}") String nodeName,
			@Qualifier("localMapGenDao") MapGenDAO localFileDao,
			HazelcastInstance hz) throws IOException {

		masterStore = hz.getMap(MASTER_STORE);
		clientStore = hz.getMap(CLIENT_STORE);
		this.nodeName = Objects.requireNonNull(nodeName);
		this.localFileDao = Objects.requireNonNull(localFileDao);

	}

	@Override
	public void saveMapDataPart(MapDataPart part) {
		localFileDao.saveMapDataPart(part);
	}

	@Override
	public Iterator<MapDataPart> getMapDataPartIterator() {
		return localFileDao.getMapDataPartIterator();
	}

	@Override
	public Object getNodeData(String key) {
		return clientStore.get(getNodeKey(key));
	}

	@Override
	public List<Object> getAllData(String key) {
		return clientStore.entrySet()
				.stream()
				.filter(e -> e.getKey().endsWith(key))
				.map(e -> e.getValue())
				.collect(Collectors.toList());
	}

	@Override
	public Object getMasterData(String key) {
		return masterStore.get(key);
	}

	@Override
	public void saveMasterData(String key, Object data) {
		masterStore.put(key, data);
	}

	@Override
	public void saveNodeData(String key, Object data) {
		clientStore.put(getNodeKey(key), data);
	}

	private String getNodeKey(String key) {
		return String.format("node_%s-%s", nodeName, key);
	}

	/**
	 * Deleates all data from the hazelcast system used up by the map creation.
	 */
	public void clearData() {
		masterStore.clear();
		clientStore.clear();
	}
}
