package net.bestia.maven.map;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import net.bestia.util.BestiaVersion;
import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.core.Tile;
import tiled.core.TileLayer;
import tiled.core.TileSet;

/**
 * Writes an TMX map as the JSON format usable by the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TmxJsonWriter {

	private static final Logger LOG = LogManager.getLogger(TmxJsonWriter.class);

	private static final BestiaVersion projectInfo = new BestiaVersion();
	
	private static final Pattern LAYER_PATTERN = Pattern.compile("layer_(?<index>\\d+)(_(?<subindex>\\d+))?");
	private static final Pattern LAYER_SUB_PATTERN = Pattern.compile("_(\\d+)");

	private final JsonFactory jFactory = new JsonFactory();

	private Map tmxMap;
	private JsonGenerator jgen;
	private int tileSize;

	private java.util.Map<String, BufferedImage> tilemapCache = new HashMap<>();

	/**
	 * The names of the map properties which are ignored when translating into
	 * JSON.
	 */
	private static final Set<String> IGNORED_MAP_LAYERS;
	static {
		final Set<String> ignoredMapLayers = new HashSet<>();

		ignoredMapLayers.add("spawn");
		ignoredMapLayers.add("portals");
		ignoredMapLayers.add("scripts");

		IGNORED_MAP_LAYERS = Collections.unmodifiableSet(ignoredMapLayers);
	}

	/**
	 * The names of the map properties which are ignored when translating into
	 * JSON.
	 */
	private static final Set<String> IGNORED_MAP_PROPERTIES;
	static {
		final Set<String> ignoredMapProps = new HashSet<>();

		ignoredMapProps.add("globalScripts");
		ignoredMapProps.add("spawn");

		IGNORED_MAP_PROPERTIES = Collections.unmodifiableSet(ignoredMapProps);
	}

	public void write(Map tmxMap, File outFile) throws IOException {

		this.tmxMap = tmxMap;
		this.jgen = jFactory.createGenerator(outFile, JsonEncoding.UTF8);

		jgen.writeStartObject();

		writeHeader();
		writeProperties();

		final List<List<String>> mergeLayers = getMergeLayers();
		final List<TileLayer> processedLayer = processLayers(mergeLayers);
		writeLayers(processedLayer);

		jgen.writeEndObject();

		tilemapCache.clear();

	}

	/**
	 * Generates a list of layer lists which should be merged.
	 * 
	 * @return
	 */
	private List<List<String>> getMergeLayers() {

		final java.util.Map<Integer, List<String>> mergeLayers = new HashMap<>();
		final List<List<String>> sortedMergeLayers = new ArrayList<>();

		final List<TileLayer> temp = tmxMap.getLayers()
				.stream()
				.filter(x -> x instanceof TileLayer)
				.map(x -> (TileLayer) x)
				.collect(Collectors.toList());

		for (int i = 0; i < temp.size(); i++) {

			final String lName = temp.get(i).getName();
			final Matcher m = LAYER_PATTERN.matcher(lName);

			// Find next sequence.
			if (m.find()) {

				final int index = Integer.parseInt(m.group("index"));
				//final int subindex = m.group("subindex") != null ? Integer.parseInt(m.group("subindex")) : -1;

				if (!mergeLayers.containsKey(index)) {
					mergeLayers.put(index, new ArrayList<>());
				}

				mergeLayers.get(index).add(lName);

			}
		}

		// Now sort teh layerz!
		for(int i = 0; i < mergeLayers.keySet().size(); i++) {
			final List<String> layerGroup = new ArrayList<>();
			sortedMergeLayers.add(layerGroup);
			
			final List<String> sort = mergeLayers.get(i);
			final String firstLayerName = "layer_" + i;
			// Find the first layer.
			for(int j = 0; j < sort.size(); j++) {
				sort.get(j).equals(firstLayerName);
				layerGroup.add(sort.get(j));
				sort.remove(j);
			}
			
			// Sort the sub layers.
			Collections.sort(sort, new Comparator<String>() {
			    @Override
			    public int compare(String s1, String s2) {
			    	final Matcher m1 = LAYER_SUB_PATTERN.matcher(s1);
			    	final Matcher m2 = LAYER_SUB_PATTERN.matcher(s2);
			    	
			    	m1.find();
			    	m2.find();
			    	
			    	final int n1 = Integer.parseInt(m1.group());
			    	final int n2 = Integer.parseInt(m2.group());
			    	
			    	if (n1 == n2) {
			    		return 0;
			    	}
			    	
			    	if(n1 > n2) {
			    		return 1;
			    	} else {
			    		return -1;
			    	}
			    }
			});
			
			layerGroup.addAll(sort);
		}

		return sortedMergeLayers;
	}

	private List<TileLayer> processLayers(List<List<String>> mergeLayers) throws IOException {
		loadTilesets();
		
		// Process all layer groups in order.
		
			
			for(int y = 0; y < tmxMap.getHeight(); y++) {
				for(int x = 0; x < tmxMap.getWidth(); x++) {
					
					for(List<String> layerGroup : mergeLayers) {
						
					}
					
				}
			}

		/*final File sourceFile = new File();
		final BufferedImage sourceTilemap = ImageIO.read(input);

		// After we have merged the layer and have a list of tiles (and also
		// which tiles are stacked) we calculate the tilemap image dimensions.
		int numIndependentTiles = 145;

		// Miracle.

		int x = 10;
		int y = 15;

		final File destTilemapFile = new File("");
		final BufferedImage destTilemap = new BufferedImage(x * tileSize, y * tileSize, BufferedImage.TYPE_INT_ARGB);

		// Copy all the

		// write the destination tilemap.
		ImageIO.write(destTilemap, "png", destTilemapFile);*/

		return null;
	}

	private void loadTilesets() throws IOException {
		final Vector<TileSet> tilesets = tmxMap.getTileSets();
		for (TileSet tileset : tilesets) {
			// String baseDir = tileset.getBaseDir();
			final String sourceFileName = tileset.getTilebmpFile();

			if (sourceFileName == null) {
				throw new IOException(String.format("SourceFile of tileset %s was null.", tileset.getName()));
			}

			File sourceFile = new File(sourceFileName);

			// If its a relative path join it with the filename of the tmx map.
			if (!sourceFile.isAbsolute()) {
				final String tmxFilename = tmxMap.getFilename();
				final File tmxFile = new File(tmxFilename);
				sourceFile = new File(tmxFile.getParentFile(), sourceFileName);
			}

			final BufferedImage sourceTilemap = ImageIO.read(sourceFile);

			// Save the image as we need to refer to it later.
			tilemapCache.put(tileset.getName(), sourceTilemap);
		}
	}

	/**
	 * Writes the layer of the map. However these layers must have been
	 * processed a bit: Tile layers should have been condensed down into a 2
	 * layers (ground and an upper layer). The tiles should have been as well
	 * written to a single tilemap and remapped into the map.
	 * 
	 * @param layers
	 * @throws IOException
	 */
	private void writeLayers(List<TileLayer> layers) throws IOException {
		jgen.writeArrayFieldStart("layers");

		for (TileLayer layer : layers) {
			if (IGNORED_MAP_LAYERS.contains(layer.getName())) {
				continue;
			}

			// Write the maplayer.
			jgen.writeStartObject();

			jgen.writeNumberField("height", layer.getHeight());
			jgen.writeNumberField("width", layer.getWidth());
			jgen.writeNumberField("x", 0);
			jgen.writeNumberField("y", 0);
			jgen.writeBooleanField("visible", true);
			jgen.writeStringField("name", layer.getName());
			jgen.writeNumberField("opacity", 1);
			jgen.writeStringField("type", "tilelayer");

			// Now we must write the tiles.
			jgen.writeArrayFieldStart("data");

			for (int x = 0; x < layer.getWidth(); x++) {
				for (int y = 0; y < layer.getHeight(); y++) {
					final Tile t = layer.getTileAt(x, y);
					jgen.writeNumber(t.getId());
				}
			}

			jgen.writeEndArray();

			jgen.writeEndObject();
		}

		jgen.writeEndArray();

	}

	private void writeProperties() throws IOException {
		jgen.writeObjectFieldStart("properties");

		final Properties props = tmxMap.getProperties();

		for (String key : props.stringPropertyNames()) {
			if (IGNORED_MAP_PROPERTIES.contains(key)) {
				continue;
			}

			final String value = props.getProperty(key);
			jgen.writeStringField(key, value);
		}

		jgen.writeEndObject();
	}

	private void writeHeader() throws IOException {
		jgen.writeStringField("generatedWith",
				String.format("%s - %s", projectInfo.getName(), projectInfo.getVersion()));
		jgen.writeStringField("orientation", "orthogonal");
		jgen.writeNumberField("width", tmxMap.getWidth());
		jgen.writeNumberField("height", tmxMap.getHeight());
		jgen.writeNumberField("version", 1);
		jgen.writeNumberField("tilewidth", tmxMap.getTileWidth());
		jgen.writeNumberField("tileheight", tmxMap.getTileHeight());

		if (tmxMap.getTileHeight() != tmxMap.getTileWidth()) {
			throw new IOException("Tiles must be of equal height and width.");
		}

		tileSize = tmxMap.getTileHeight();
	}

}
