package net.bestia.palantir;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApplication extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(MainApplication.class);
	private static final String MAIN_FXML = "/fxml/Main.fxml";

	@Override
	public void start(Stage primaryStage) {
		LOG.info("Starting Palantir application");

		try {
			final URL mainFxml = getClass().getResource(MAIN_FXML);
			final Parent rootNode = FXMLLoader.load(mainFxml);
			final Scene scene = new Scene(rootNode, 400, 200);
			primaryStage.setTitle("Bestia Palantir");
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch (Exception e) {
			LOG.error("Error during startup.", e);
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		launch(args);
	}

}
