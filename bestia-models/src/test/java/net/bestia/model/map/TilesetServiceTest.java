package net.bestia.model.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TilesetServiceTest {

	private static final int INVALID_ID = 10;
	private static final int VALID_ID = 11;
	private static final int INVALID_DATA_ID = 12;

	private TilesetService tilesetServ;

	private static final String VALID_DATA = "{\"mingid\": 0, \"maxgid\": 200, \"size\": {\"width\": 10, \"height\": 20}, \"name\": \"mountain_landscape_23\", \"props\": null}";
	private static final String INVALID_DATA = "{mingid: 0, maxgid: 200, name: 'mountain_landscape_23'}";

	@Mock
	private TilesetData validData;

	@Mock
	private TilesetData invalidData;

	@Mock
	private TilesetDataRepository tilesetDao;

	@Before
	public void setup() {

		when(tilesetDao.findByGid(anyLong())).thenReturn(null);
		when(tilesetDao.findByGid(VALID_ID)).thenReturn(validData);
		when(tilesetDao.findByGid(INVALID_DATA_ID)).thenReturn(invalidData);

		when(validData.getData()).thenReturn(VALID_DATA);
		when(invalidData.getData()).thenReturn(INVALID_DATA);

		tilesetServ = new TilesetService(tilesetDao);
	}

	@Test
	public void findTileset_unknownId_empty() {

		Optional<Tileset> ts = tilesetServ.findTileset(INVALID_ID);
		assertFalse(ts.isPresent());

	}

	@Test
	public void findTileset_knownIdValidData_notEmpty() {

		Optional<Tileset> ts = tilesetServ.findTileset(VALID_ID);
		assertTrue(ts.isPresent());

	}

	@Test
	public void findTileset_knownIdInValidData_empty() {

		Optional<Tileset> ts = tilesetServ.findTileset(INVALID_DATA_ID);
		assertFalse(ts.isPresent());
	}

	@Test
	public void findAllTilesets_listWithKnownIds_results() {
		Set<Integer> ids = new HashSet<Integer>(Arrays.asList(VALID_ID));
		List<Tileset> ts = tilesetServ.findAllTilesets(ids);

		assertEquals(1, ts.size());
	}

	@Test
	public void findAllTilesets_listWithUnknwonIds_emptyList() {
		Set<Integer> ids = new HashSet<Integer>(Arrays.asList(INVALID_ID));
		List<Tileset> ts = tilesetServ.findAllTilesets(ids);

		assertEquals(0, ts.size());
	}
}
