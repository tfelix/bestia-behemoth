package net.bestia.palantir.controller;

import javafx.event.ActionEvent;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.bestia.palantir.map.MapManager;

public class MapController implements Initializable {
	private static final Logger LOG = LoggerFactory.getLogger(MapController.class);

	@FXML
	private Canvas worldMapCanvas;
	
	@FXML
	private AnchorPane anchorPane;
	
	private MapManager mapManager = new MapManager();

	private MapTools selectedMapTool = MapTools.ZOOM;

	public void handleMouseClick() {
		LOG.info("Works.");
	}

	public void handleOpenMapConfig(ActionEvent ae) {

		final Node source = (Node) ae.getSource();
		final Window stage = source.getScene().getWindow();

		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open Bestia map config");
		final File configFile = fileChooser.showOpenDialog(stage);
		
		try {
			mapManager.loadMapConfig(configFile);
		} catch (IOException e) {
			LOG.warn("Could not load map configuration.", e);
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		LOG.info("Loading map data.");

		worldMapCanvas.setOnDragDetected(new EventHandler<MouseEvent>() {

			@Override
			public void handle(MouseEvent event) {
				LOG.info("Drag detected.");
			}
		});

		worldMapCanvas.setOnMouseClicked(event -> {
			LOG.info("Click detected.");
		});

		// Testing
		final GraphicsContext gc = worldMapCanvas.getGraphicsContext2D();

		gc.setFill(Color.AZURE);
		gc.fillRect(0, 0, worldMapCanvas.getWidth(), worldMapCanvas.getHeight());

		gc.setFill(Color.GREEN);
		gc.strokeLine(40, 10, 10, 40);
	}
}
