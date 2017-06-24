package net.bestia.zoneserver.service;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.MapChunk;
import net.bestia.model.map.TilesetService;
import net.bestia.zoneserver.map.MapService;

@RunWith(MockitoJUnitRunner.class)
public class MapServiceTest {

	private static final String MAP_NAME = "kalarian";

	private MapService ms;

	@Mock
	private MapDataDAO dataNoMapDao;

	@Mock
	private MapDataDAO dataMapDao;

	@Mock
	private MapParameterDAO paramDao;

	@Mock
	private MapParameterDAO paramNoMapDao;

	@Mock
	private MapParameter mapParams;

	@Mock
	private TilesetService tilesetService;

	@Before
	public void setup() {

		when(dataMapDao.count()).thenReturn(1L);
		when(mapParams.getName()).thenReturn(MAP_NAME);
		when(paramDao.findFirstByOrderByIdDesc()).thenReturn(mapParams);

		ms = new MapService(dataNoMapDao, paramDao, tilesetService);
	}

	@Test
	public void isMapInitialized_noMapInsideDB_false() {
		Assert.assertFalse(ms.isMapInitialized());
	}

	@Test
	public void isMapInitialized_mapInsideDB_true() {
		ms = new MapService(dataMapDao, paramDao, tilesetService);
		Assert.assertTrue(ms.isMapInitialized());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getMap_illegalCoordinates_throws() {
		ms.getMap(10, 10, -10, 10);
	}

	@Test
	public void getMap_legalCoordinates_validMap() {

		Map m = ms.getMap(5, 10, 10, 10);

		Assert.assertNotNull(m);
		Assert.assertEquals(new Rect(5, 10, 10, 10), m.getRect());
	}

	@Test(expected = NullPointerException.class)
	public void saveMapData_null_throws() throws IOException {
		ms.saveMapData(null);
	}

	@Test
	public void getMapName_noMapInsideDB_emptyStr() {
		ms = new MapService(dataNoMapDao, paramNoMapDao, tilesetService);
		Assert.assertEquals("", ms.getMapName());
	}

	@Test
	public void getMapName_mapInsideDB_validStr() {
		Assert.assertEquals(MAP_NAME, ms.getMapName());
	}

	@Test(expected = NullPointerException.class)
	public void getChunks_null_throws() {
		ms.getChunks(null);
	}

	@Test
	public void getChunks_validCords_listWithChunks() {
		List<Point> chunkCords = new ArrayList<>();
		chunkCords.add(new Point(1, 1));
		List<MapChunk> chunks = ms.getChunks(chunkCords);

		Assert.assertNotNull(chunks);
		Assert.assertEquals(1, chunks.size());
	}
}
