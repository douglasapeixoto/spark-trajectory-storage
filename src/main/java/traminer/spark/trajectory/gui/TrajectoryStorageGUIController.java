package traminer.spark.trajectory.gui;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import traminer.io.log.ObservableLog;
import traminer.io.params.SparkParameters;
import traminer.spark.trajectory.clost.CloSTParameters;

/**
 * GUI controller class (JavaFX). Handle the events and components 
 * of the GUI {@link TrajectoryStorageScene.fxml}. Binds the GUI 
 * components with the Java code.
 * 
 * @author douglasapeixoto
 */
public class TrajectoryStorageGUIController implements Initializable {
	@FXML
	private Pane rootPane;
	
	@FXML
	private TextField sparkMasterTxt;
	@FXML
	private TextField numRDDPartitionsTxt;
	@FXML
	private TextField inputDataLocalTxt;
	@FXML
	private TextField coldDataLocalTxt;
	@FXML
	private TextField hdfsMasterTxt;
	@FXML
	private TextField inputDataHDFSTxt;
	@FXML
	private TextField coldDataHDFSTxt;
	@FXML
	private TextField minXTxt;
	@FXML
	private TextField minYTxt;
	@FXML
	private TextField maxXTxt;
	@FXML
	private TextField maxYTxt;
	@FXML
	private TextField nodesCapacityTxt;
	@FXML
	private TextField timeWindowTxt;
	@FXML
	private TextField sampleSizeTxt;
	@FXML
	private TextField activeTimeTxt;
	@FXML
	private TextField scanFrequencyTxt;
	@FXML
	private TextField concurrencyNumTxt;
	
	@FXML
	private RadioButton hotModeBtn;
	@FXML
	private RadioButton coldModeBtn;
	
	@FXML
	private ChoiceBox<String> dataLocationChoice;
	
	@FXML
	private Tab localAccessTab;
	@FXML
	private Tab hdfsAccessTab;
	@FXML
	private TabPane dataAccessPane;
	
	@FXML
	private TextArea logArea;
	
	/** GUI log observer */
	private LogObserver logObserver = new LogObserver();
	
	/** Running services */
	private boolean isPhisicalPlannerOn = false;
	private boolean isStorageControllerOn = false;
	private boolean isTaskSchedulerOn = false;
	
	/** Data access options */
	private static final String HDFS  = "HDFS";
	private static final String LOCAL = "LOCAL";
	
	/** App services parameters */
	private SparkParameters sparkParams;
	private CloSTParameters clostParams;
	private String coldDataLocation = "";
	private String inputDataLocation = "";
	
	/** Application model/business logic */
	private TrajectoryStorageClient storageClient;
	
	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		log("Appplication Starts.");
		
		// feed choice box
		dataLocationChoice.getItems().add(LOCAL);
		dataLocationChoice.getItems().add(HDFS);
		dataLocationChoice.setValue(LOCAL);

		// group radio buttons
		ToggleGroup radioGroup = new ToggleGroup();
		hotModeBtn.setToggleGroup(radioGroup);
		coldModeBtn.setToggleGroup(radioGroup);
		
		handleIntegerField(numRDDPartitionsTxt);
		handleIntegerField(nodesCapacityTxt);
		handleIntegerField(activeTimeTxt);
		handleIntegerField(scanFrequencyTxt);
		handleIntegerField(concurrencyNumTxt);
		handleIntegerField(timeWindowTxt);
		handleIntegerField(sampleSizeTxt);
		
		ObservableLog.instance().addObserver(logObserver);
	}

	/**
	 * Setup and starts the app business logic/model.
	 * 
	 * @return Whether or not the business logic was 
	 * started successfully,
	 */
	private boolean startBusinessLogic() {
		if (!validateDataAccess()) {
			showErrorMessage("Data Access fields must be provided!");
			return false;
		}
		
		// get cold data location
		if (dataLocationChoice.getValue().equals(LOCAL)) {
			inputDataLocation = inputDataLocalTxt.getText();
			coldDataLocation = coldDataLocalTxt.getText();
		} else 
		if (dataLocationChoice.getValue().equals(HDFS)) {
			String hdfsMaster = hdfsMasterTxt.getText();
			String coldDirHdfs = coldDataHDFSTxt.getText();
			String dataDirHdfs = inputDataHDFSTxt.getText();
			
			coldDataLocation = hdfsMaster + coldDirHdfs;
			inputDataLocation = hdfsMaster + dataDirHdfs;
		}	

		try {
			// get Spark access
			String sparkMaster = sparkMasterTxt.getText();
			sparkParams	= new SparkParameters(sparkMaster, "TrajectoryStorageApp");
					
			// start business logic - model
			storageClient = new TrajectoryStorageClient(
					sparkParams, clostParams, coldDataLocation);
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}

	@FXML
	private void actionDataLocationChoice() {
		if (dataLocationChoice.getValue().equals(LOCAL)) {
			hdfsAccessTab.setDisable(true);
			localAccessTab.setDisable(false);
			dataAccessPane.getSelectionModel().select(localAccessTab);
		} else 
		if (dataLocationChoice.getValue().equals(HDFS)) {
			localAccessTab.setDisable(true);
			hdfsAccessTab.setDisable(false);
			dataAccessPane.getSelectionModel().select(hdfsAccessTab);
		}
	}
	
	@FXML
	private void actionOpenInputData() {
    	final DirectoryChooser dirChooser = new DirectoryChooser();
    	dirChooser.setTitle("Open Trajectory Data");
        final File selectedDir = dirChooser.showDialog(
        		rootPane.getScene().getWindow());
        if (selectedDir != null) {
        	String dataPath = selectedDir.getAbsolutePath();
        	inputDataLocalTxt.setText(dataPath);
        	inputDataLocalTxt.home();
        }		
	}
	
	@FXML
	private void actionOpenColdData() {
    	final DirectoryChooser dirChooser = new DirectoryChooser();
    	dirChooser.setTitle("Open Trajectory Data");
        final File selectedDir = dirChooser.showDialog(
        		rootPane.getScene().getWindow());
        if (selectedDir != null) {
        	String dataPath = selectedDir.getAbsolutePath();
        	coldDataLocalTxt.setText(dataPath);
        	coldDataLocalTxt.home();
        }		
	}
	
	@FXML
	private void actionStartPhysicalPlannerExisting() {
		// TODO
		try {
			// spatial boundaries
			double minX = Double.parseDouble(minXTxt.getText());
			double minY = Double.parseDouble(minYTxt.getText());
			double maxX = Double.parseDouble(maxXTxt.getText());
			double maxY = Double.parseDouble(maxYTxt.getText());
			// CloST parameters
			long timeWindow = Long.parseLong(timeWindowTxt.getText());
			int nodesCapacity = Integer.parseInt(nodesCapacityTxt.getText());
			int sampleSize = Integer.parseInt(sampleSizeTxt.getText());

			// setup CloST parameters
			clostParams = new CloSTParameters(minX, maxX, minY, maxY, 
					timeWindow, nodesCapacity, sampleSize);
			
			// setup and start storage client
			if (!startBusinessLogic()) {
				showErrorMessage("Unable to start storage client!");
				return;
			}

			// Start physical planner in a separated thread
			final int numRDDPatitions = Integer.parseInt(numRDDPartitionsTxt.getText());
			Runnable process = new Runnable() {
				@Override
				public void run() {
					if (hotModeBtn.isSelected()) {
						isPhisicalPlannerOn = storageClient
								.startPhysicalPlannerFromExistingPartitioning(numRDDPatitions);
					} else 
					if (coldModeBtn.isSelected()) {
						// TODO
					}
				}
			};
			Thread thread = new Thread(process);
			thread.setDaemon(true);
			thread.start();
		} catch (NumberFormatException e) {
			showErrorMessage("Ivalid number format.");
			e.printStackTrace();
			return;
		}
	}
	@FXML
	private void actionStartPhysicalPlanner() {
		if (!validatePhisicalPlanner()) {
			showErrorMessage("Phisical Planner fields must be provided!");
			return;
		}
		try {
			// spatial boundaries
			double minX = Double.parseDouble(minXTxt.getText());
			double minY = Double.parseDouble(minYTxt.getText());
			double maxX = Double.parseDouble(maxXTxt.getText());
			double maxY = Double.parseDouble(maxYTxt.getText());
			// CloST parameters
			long timeWindow = Long.parseLong(timeWindowTxt.getText());
			int nodesCapacity = Integer.parseInt(nodesCapacityTxt.getText());
			int sampleSize = Integer.parseInt(sampleSizeTxt.getText());

			// setup CloST parameters
			clostParams = new CloSTParameters(minX, maxX, minY, maxY, 
					timeWindow, nodesCapacity, sampleSize);
			
			// setup and start storage client
			if (!startBusinessLogic()) {
				showErrorMessage("Unable to start storage client!");
				return;
			}

			// Start physical planner in a separated thread
			final int numRDDPatitions = Integer.parseInt(numRDDPartitionsTxt.getText());
			Runnable process = new Runnable() {
				@Override
				public void run() {
					if (hotModeBtn.isSelected()) {
						isPhisicalPlannerOn = storageClient.startPhysicalPlannerHot(
								inputDataLocation, numRDDPatitions);
					} else 
					if (coldModeBtn.isSelected()) {
						isPhisicalPlannerOn = storageClient.startPhysicalPlannerCold(
								inputDataLocation, numRDDPatitions);
					}
				}
			};
			Thread thread = new Thread(process);
			thread.setDaemon(true);
			thread.start();
		} catch (NumberFormatException e) {
			showErrorMessage("Ivalid number format.");
			e.printStackTrace();
			return;
		}
	}
	
	@FXML
	private void actionStartStorageController() {
		if (!isPhisicalPlannerOn) {
			showErrorMessage("Phisical Planner is OFF. Start it first!");
			return;
		}
		if (!validateStorageController()) {
			showErrorMessage("Storage Controller fields must be provided!");
			return;
		}

		final long activeTimeWindow = Long.parseLong(activeTimeTxt.getText());
		final long scanFrequency = Long.parseLong(scanFrequencyTxt.getText());
		// Start Storage Controller in a separated thread
		Runnable process = new Runnable() {
			@Override
			public void run() {
				isStorageControllerOn = storageClient.startStorageController(
						activeTimeWindow, scanFrequency);
			}};
		Thread thread = new Thread(process);
		thread.setDaemon(true);
		thread.start();
	}
	
	@FXML
	private void actionStopStorageController() {
		//TODO
		if (isStorageControllerOn) {
			showInfoMessage("Storage Controller is OFF!");
		} else {
			showInfoMessage("Storage Controller is already OFF!");
		}
	}
	
	@FXML
	private void actionStartTaskScheduler() {
		if (!isStorageControllerOn) {
			showErrorMessage("Storage Controller is OFF. Start it first!");
			return;
		}
		if (!validateTaskScheduler()) {
			showErrorMessage("Task Scheduler fields must be provided!");
			return;
		}
		
		final int maxTasksNumber = Integer.parseInt(concurrencyNumTxt.getText());
		// Start TaskScheduler in a separated thread
		Runnable process = new Runnable() {
			@Override
			public void run() {
				isTaskSchedulerOn = storageClient.startTaskScheduler(
						maxTasksNumber);
			}};
		Thread thread = new Thread(process);
		thread.setDaemon(true);
		thread.start();
		
		// open query window
		showQueryWindow();
	}
	
	@FXML
	private void actionStopTaskScheduler() {
		//TODO
		if (isTaskSchedulerOn) {
			showInfoMessage("Task Scheduller is OFF!");
		} else {
			showInfoMessage("Task Scheduller is already OFF!");
		}
	}
	
	/**
	 * Open a new Windows to input queries.
	 */
	private void showQueryWindow() {
		try {
	        Parent queryRoot = FXMLLoader.load(getClass().getResource("QueryScene.fxml"));

			Stage queryStage = new Stage();
	        queryStage.setTitle("Submit Trajectory Query");
	        queryStage.setScene(new Scene(queryRoot));
	        queryStage.setHeight(550.0);
	        queryStage.setWidth(480.0);
	        queryStage.setResizable(false);
	        queryStage.show();
		} catch (IOException e) {
			log("Error opening 'OutputScene' GUI.");
			e.printStackTrace();
		}
	}
	
	/**
	 * Force field to be integer number only 
	 * @param textField
	 */
	private void handleIntegerField(TextField textField) {
		textField.textProperty().addListener(new ChangeListener<String>() {
	        @Override
	        public void changed(ObservableValue<? extends String> observable, 
	        		String oldValue, String newValue) {
	            if (!newValue.matches("\\d*")) {
	            	textField.setText(newValue.replaceAll("[^\\d]", ""));
	            }
	        }
	    });	
	}
	
	/**
	 * Validate mandatory fields.
	 * @return
	 */
	private boolean validateDataAccess() {
		boolean validate = true;
		if (sparkMasterTxt.getText().isEmpty()) {
			log("Spark Master must be provided!");
			validate = false;
		}
		if (numRDDPartitionsTxt.getText().isEmpty()) {
			log("Number of RDD Partitions must be provided!");
			validate = false;
		}
		// Validate local data fields
		if (dataLocationChoice.getValue().equals(LOCAL)) {
			if (inputDataLocalTxt.getText().isEmpty()) {
				log("Input Data Location must be provided!");
				validate = false;
			}
			if (coldDataLocalTxt.getText().isEmpty()) {
				log("Cold Data Location must be provided!");
				validate = false;
			}
		}
		// Validate HDFS data fields
		if (dataLocationChoice.getValue().equals(HDFS)) {
			if (hdfsMasterTxt.getText().isEmpty()) {
				log("HDFS Master must be provided!");
				validate = false;
			}
			if (inputDataHDFSTxt.getText().isEmpty()) {
				log("Input Data Location in HDFS must be provided!");
				validate = false;
			}
			if (coldDataHDFSTxt.getText().isEmpty()) {
				log("Cold Data Location in HDFS must be provided!");
				validate = false;
			}
		}

		return validate;
	}
	
	/**
	 * Validate mandatory fields.
	 * @return
	 */
	private boolean validatePhisicalPlanner() {
		boolean validate = true;
		if (minXTxt.getText().isEmpty()) {
			log("MinX. Value must be provided!");
			validate = false;
		}
		if (minYTxt.getText().isEmpty()) {
			log("MinY. Value must be provided!");
			validate = false;
		}
		if (maxXTxt.getText().isEmpty()) {
			log("MaxX. Value must be provided!");
			validate = false;
		}
		if (maxYTxt.getText().isEmpty()) {
			log("MaxY. Value must be provided!");
			validate = false;
		}
		if (nodesCapacityTxt.getText().isEmpty()) {
			log("Nodes Capacity must be provided!");
			validate = false;
		}
		if (timeWindowTxt.getText().isEmpty()) {
			log("Time Window must be provided!");
			validate = false;
		}
		if (sampleSizeTxt.getText().isEmpty()) {
			log("Sample Size must be provided!");
			validate = false;
		}

		return validate;
	}
	
	/**
	 * Validate mandatory fields.
	 * @return
	 */
	private boolean validateStorageController() {
		boolean validate = true;
		if (activeTimeTxt.getText().isEmpty()) {
			log("Active Time must be provided!");
			validate = false;
		}
		if (scanFrequencyTxt.getText().isEmpty()) {
			log("Scan Frequency must be provided!");
			validate = false;
		}

		return validate;
	}
	
	/**
	 * Validate mandatory fields.
	 * @return
	 */
	private boolean validateTaskScheduler() {
		boolean validate = true;
		if (concurrencyNumTxt.getText().isEmpty()) {
			log("Concurrency Number must be provided!");
			validate = false;
		}

		return validate;
	}
	
	/**
	 * Open a simple ERROR alert/dialog with the given message.
	 * 
	 * @param message The message content.
	 */
	private void showErrorMessage(String message) {
		Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Error Message");
		alert.setHeaderText(null);
		alert.setContentText(message);

		alert.show();
		log(message);
	}

	/**
	 * Open a simple INFO alert/dialog with the given message.
	 * 
	 * @param message The message content.
	 */
	private void showInfoMessage(String message) {
		Alert alert = new Alert(AlertType.INFORMATION);
		alert.setTitle("Info Message");
		alert.setHeaderText(null);
		alert.setContentText(message);

		alert.show();
		log(message);
	}
	
	/**
	 * Add message to the log.
	 * @param msg
	 */
	private void log(String msg) {
		logArea.appendText("> [LOG] " + msg + "\n");
	}
	
	@SuppressWarnings("serial")
	private class LogObserver implements Observer, Serializable {
		//private ObservableLog observable;
		@Override
		public void update(Observable obs, Object o) {
			//observable = (ObservableLog)obs;
			log(ObservableLog.instance().getMessage());
		}
	}
}
