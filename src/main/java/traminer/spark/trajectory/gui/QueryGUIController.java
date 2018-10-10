package traminer.spark.trajectory.gui;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.spark.api.java.JavaRDD;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;

import traminer.io.IOService;
import traminer.spark.trajectory.querymanager.BoxQuery;
import traminer.spark.trajectory.querymanager.CircleQuery;
import traminer.spark.trajectory.taskmanager.TaskScheduler;
import traminer.util.spatial.objects.Circle;
import traminer.util.spatial.objects.Rectangle;
import traminer.util.trajectory.Trajectory;

public class QueryGUIController implements Initializable {
	@FXML
	private AnchorPane rootPanel;
	@FXML
	private TextField queryTimeIniTxt;
	@FXML
	private TextField queryTimeEndTxt;
	@FXML
	private TextField queryMinXTxt;
	@FXML
	private TextField queryMinYTxt;
	@FXML
	private TextField queryMaxXTxt;
	@FXML
	private TextField queryMaxYTxt;
	@FXML
	private TextField queryXTxt;
	@FXML
	private TextField queryYTxt;
	@FXML
	private TextField queryRadiusTxt;
	@FXML
	private TextField queryFileTxt;
	
	@FXML
	private TextArea queryLogArea;
	
	/** Number of queries completed so far */
	private static int queryCounter = 0;
	
	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		log("Ready to submit!");
		
// TODO Put this in the model (task manager)
// How often should it print the results
final long timeFrequency = 10000; // 10 s
startQueryTimer(timeFrequency); // diagnosis?
	}
	
	@FXML
	private void actionSubmitBoxQuery() {
		if (!validateBoxFields()) {
			showErrorMessage("Box query fields must be provided!");
			return;
		}
		try {
			long timeIni = Long.parseLong(queryTimeIniTxt.getText());
			long timeEnd = Long.parseLong(queryTimeEndTxt.getText());
			double minX = Double.parseDouble(queryMinXTxt.getText());
			double minY = Double.parseDouble(queryMinYTxt.getText());
			double maxX = Double.parseDouble(queryMaxXTxt.getText());
			double maxY = Double.parseDouble(queryMaxYTxt.getText());
			
			// creates the query object
			Rectangle region = new Rectangle(minX, minY, maxX, maxY);
			BoxQuery query = new BoxQuery(region, timeIni, timeEnd);
			
			// TODO		
			Future<JavaRDD<Trajectory>> result = TaskScheduler.getInstance()
					.submit(query);
			//logResult(query, result);
						
			showInfoMessage("Box Query submited");
		} catch (NumberFormatException e) {
			showErrorMessage("Invalid number format!");
			e.printStackTrace();
		}
	}
	
	@FXML
	private void actionSubmitCircleQuery() {
		if (!validateCircleFields()) {
			showErrorMessage("Circle query fields must be provided!");
			return;
		}
		try {
			long timeIni = Long.parseLong(queryTimeIniTxt.getText());
			long timeEnd = Long.parseLong(queryTimeEndTxt.getText());
			double x = Double.parseDouble(queryXTxt.getText());
			double y = Double.parseDouble(queryYTxt.getText());
			double r = Double.parseDouble(queryRadiusTxt.getText());
			
			// creates the query object
			Circle region = new Circle(x, y, r);
			CircleQuery query = new CircleQuery(region, timeIni, timeEnd);
			
			log("Query submited!");
			Future<JavaRDD<Trajectory>> result = TaskScheduler.getInstance().submit(query);
			logResult(query, result);			
		} catch (NumberFormatException e) {
			showErrorMessage("Invalid number format!");
			e.printStackTrace();
		}
	}
	
	@FXML
	private void actionOpenQueryFile() {
    	final FileChooser fileChooser = new FileChooser();
    	fileChooser.setTitle("Open Query Data");
        final File file = fileChooser.showOpenDialog(
        		rootPanel.getScene().getWindow());
        if (file != null) {
        	String filePath = file.getAbsolutePath();
        	queryFileTxt.setText(filePath);
        	queryFileTxt.home();
        }		
	}
	@FXML
	private void actionRunExperiments() {
		log("Running Experimental Queries!");
		List<CircleQuery> queryList = readQueryFile();
		for (CircleQuery query : queryList) {
			Future<JavaRDD<Trajectory>> result = 
					TaskScheduler.getInstance().submit(query);
			logResult(query, result);
		}
	}

	private List<CircleQuery> readQueryFile() {
		final String filePath = queryFileTxt.getText();
		List<CircleQuery> queryList = new ArrayList<>();
		try {
			List<String> file = IOService.readFile(filePath);
						
			for (String line : file) {
				String[] words = line.split(",");
				int x = Integer.parseInt(words[0]);
				int y = Integer.parseInt(words[1]);
				int r = Integer.parseInt(words[2]);
				long ta = Long.parseLong(words[3]);
				long tb = Long.parseLong(words[4]);
				
				queryList.add(new CircleQuery(x, y, r, ta, tb));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return queryList;
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
	
	private boolean validateBoxFields() {
		boolean validate = true;
		if (queryTimeIniTxt.getText().isEmpty()) {
			log("Query Initial Time value must be provided!");
			validate = false;
		}
		if (queryTimeEndTxt.getText().isEmpty()) {
			log("Query Final Time value must be provided!");
			validate = false;
		}
		if (queryMinXTxt.getText().isEmpty()) {
			log("Query MinX. value must be provided!");
			validate = false;
		}
		if (queryMinYTxt.getText().isEmpty()) {
			log("Query MinY. value must be provided!");
			validate = false;
		}
		if (queryMaxXTxt.getText().isEmpty()) {
			log("Query MaxX. value must be provided!");
			validate = false;
		}
		if (queryMaxYTxt.getText().isEmpty()) {
			log("Query MaxY. value must be provided!");
			validate = false;
		}
		return validate;
	}
	
	private boolean validateCircleFields() {
		boolean validate = true;
		if (queryTimeIniTxt.getText().isEmpty()) {
			log("Query Initial Time value must be provided!");
			validate = false;
		}
		if (queryTimeEndTxt.getText().isEmpty()) {
			log("Query Final Time value must be provided!");
			validate = false;
		}
		if (queryXTxt.getText().isEmpty()) {
			log("Query center X value must be provided!");
			validate = false;
		}
		if (queryYTxt.getText().isEmpty()) {
			log("Query center Y value must be provided!");
			validate = false;
		}
		if (queryRadiusTxt.getText().isEmpty()) {
			log("Query Radius value must be provided!");
			validate = false;
		}
		return validate;
	}
	/**
	 * Add message to the log.
	 * @param msg
	 */
	private synchronized void log(String msg) {
		System.out.println(msg);
		queryLogArea.appendText("> [LOG] " + msg + "\n");
	}

	private synchronized void logResult(CircleQuery query, Future<JavaRDD<Trajectory>> result) {
		try {
			if (result == null || result.get()==null) {
				log("No Results Found for Query: " + query.getId());
			} else {
				JavaRDD<Trajectory> resultsRDD = result.get();
				long count = resultsRDD.count();
				log("QUERY: " + query.getId() + " Finished");
				
				if (count > 0) {
					log("RESULT: " + count + " Trajectories Found!");
					log("QUERY: " + query.toString());
				}
				//printResult(query, resultsRDD);
			}
			queryCounter++;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	private void printResult(CircleQuery query, JavaRDD<Trajectory> resultsRDD) {
		// print result
		List<Trajectory> tList = resultsRDD.collect();
		for (Trajectory t : tList) {
			log("[Q"+query.getId()+"]: " + t.toString());
		}
		log("");
	}
	
	/** 
	 * Start a service to measure the system throughput.
	 * Print the number of queries completed from time to time
	 */
	private void startQueryTimer(long timeFrequency) {
		log("[QUERY] Starting query timer service.");
		
		// start the query time service
		Timer timer = new Timer();
		timer.schedule(new QueryTimerTask(), 0, timeFrequency);
	}
	
	/**
	 * Print from time to time, how many queries have been 
	 * completed (system throughput), and memory usage (number
	 * of hot partitions in memory).
	 */
	@SuppressWarnings("serial")
	private class QueryTimerTask extends TimerTask implements Serializable {
		private List<String> resultsFile = new ArrayList<>();
		@Override
		public void run() {
			final long currentTime = System.currentTimeMillis();
			log("[QUERY] Timer: " + currentTime);
			log("[QUERY] Query Count: " + queryCounter);
			resultsFile.add("Timer: " + currentTime);
			resultsFile.add("Counter: " + queryCounter);
			// save result 
			if (queryCounter >= 1999) {
				try {
					IOService.writeFile(resultsFile, "C:\\data\\app\\", "result.txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
