package traminer.spark.trajectory.exp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import traminer.io.IOService;
import traminer.util.spatial.objects.st.STCircle;

public class QueryGenerator {

	public static void main(String[] args) {
		//generateHotRegion();
		//generateColdRegion();
		//randomQueries();
		//sample();
		//parseResult();
		parseResultToMin();
	}
	
	private static void parseResultToMin() {
		try {
			List<String> lines = IOService.readFile("C:\\data\\app\\result\\graph\\throughput-nodes-cold.txt");
			String res = "";
			for (String line : lines) {
				String[] words = line.split("\\s+");
				res += words[0] + " 0";
				for (int i=2; i<words.length;) {
					int sum = 0;
					for (int j=0; j<6 && i<words.length; j++, i++) {
						sum += Integer.parseInt(words[i]);
					}
					res += " "+sum;
				}
				res += "\n";
			}
			IOService.writeFile(res, "C:\\data\\app\\result\\graph\\", "throughput-nodes-cold-min.txt");
		} catch (IOException e) {}
	}

	private static void parseResult() {
		try {
			List<String> lines = IOService.readFile("C:\\data\\app\\result\\result-cold-120s-6core.txt");
			int prev = 0;
			int counter = 0;
			String res = "";
			for (String line : lines) {
				if (line.startsWith("Counter:")) {
					String count = line.split(" ")[1];
					counter = Integer.parseInt(count);
					int diff = counter - prev;
					prev = counter;
					res += ""+diff+" ";
				}
			}
			IOService.writeFile(res, "C:\\data\\app\\result\\graph\\", "result-cold-120s-6core.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void sample() {
		try {
			Stream<String> i = IOService.readFileAsStream("C:\\data\\app\\trajectory\\data_file_1524221448173.csv");
			i = i.skip(899900);
			IOService.writeFile(i, "C:\\data\\app\\trajectory\\", "sample.csv");
			System.out.println("Finished....");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static List<STCircle> readQuery() {
		List<STCircle> queryList = new ArrayList<>(10000);
		try {
			List<String> file = IOService.readFile("C:/data/query/query.txt");
						
			for (String line : file) {
				String[] words = line.split(",");
				int x = Integer.parseInt(words[0]);
				int y = Integer.parseInt(words[1]);
				int r = Integer.parseInt(words[2]);
				long ta = Long.parseLong(words[3]);
				long tb = Long.parseLong(words[4]);
				
				queryList.add(new STCircle(x, y, r, ta, tb));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return queryList;
	}
	
	private static void randomQueries() {
		try {
			List<String> hot  = IOService.readFile("C:/data/app/query/hot.txt");
			List<String> cold = IOService.readFile("C:/data/app/query/cold.txt");
			List<String> query = new ArrayList<>(2000);
			
			int hotCount  = 0;
			int coldCount = 0;
			for (int i=0; i<2000; i++) {
				int rand = (int)(Math.random()*10);
				if (rand == 1 && coldCount < 200) {
					query.add(cold.get(coldCount++));
				} else
				if (hotCount < 1800) {
					query.add(hot.get(hotCount++));
				} else {
					query.add(cold.get(coldCount++));
				}
			}
			IOService.writeFile(query, "C:/data/app/query/", "query.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateHotRegion() {
		final int x0 = 200;
		final int y0 = 200;
		final int r0 = 2;
		final int t0 = 1;
		final int t1 = 1000;
		final int cshift = 50;
		final int rshift = 50;
		final int tshift = 100000;
		
		List<String> data = new ArrayList<>(1800);
		for (int i=0; i<1800; i++) {
			int x = x0 + (int)(Math.random()*cshift);
			int y = y0 + (int)(Math.random()*cshift);
			int r = r0 + (int)(Math.random()*rshift);
			int ta = t0 + (int)(Math.random()*tshift);
			int tb = t1 + ta + (int)(Math.random()*tshift);
			
			String s = x+","+y+","+r+","+ta+","+tb;
			System.out.println(s);
			data.add(s);
		}
		
		try {
			IOService.writeFile(data, "C:/data/app/query/", "hot.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void generateColdRegion() {
		final int x0 = 10;
		final int y0 = 10;
		final int r0 = 1;
		final int t0 = 1;
		final int t1 = 1000;
		final int cshift = 990;
		final int rshift = 50;
		final int tshift = 100000;
		
		List<String> data = new ArrayList<>(200);
		for (int i=0; i<200; i++) {
			int x = x0 + (int)(Math.random()*cshift);
			int y = y0 + (int)(Math.random()*cshift);
			int r = r0 + (int)(Math.random()*rshift);
			int ta = t0 + (int)(Math.random()*tshift);
			int tb = t1 + ta + (int)(Math.random()*tshift);
			
			String s = x+","+y+","+r+","+ta+","+tb;
			System.out.println(s);
			data.add(s);
		}
		
		try {
			IOService.writeFile(data, "C:/data/app/query/", "cold.txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
/*

double minX = 0.0;
double minY = 0.0;
double maxX = 1000.0;
double maxY = 1000.0;
long minT = 1;
long maxT = 200000;
*/