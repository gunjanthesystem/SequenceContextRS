package org.activity.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.activity.loader.GeolifeDataLoader;
import org.activity.objects.ActivityObject;
import org.activity.objects.DataEntry;
import org.activity.objects.FlatActivityLogEntry;
import org.activity.objects.Pair;
import org.activity.objects.TimelineWithNext;
import org.activity.objects.TrackListenEntry;
import org.activity.objects.TrajectoryEntry;
import org.activity.objects.Triple;
import org.activity.objects.UserDayTimeline;
import org.activity.ui.PopUps;
import org.activity.util.Constant;
import org.activity.util.DateTimeUtils;
import org.activity.util.UtilityBelt;
import org.activity.util.weka.WekaUtilityBelt;
import org.apache.commons.math3.complex.Complex;

/**
 * TODO: convert all method to take in full path name, i.e., absolute file name
 * 
 * @author gunjan
 *
 */
public class WritingToFile
{
	static String commonPath;// = Constant.getCommonPath();//
								// "/run/media/gunjan/OS/Users/gunjan/Documents/DCU Data Works/WorkingSet7July/";
	
	// static final String[] activityNames = { "Not Available", "Unknown", "airplane", "bike", "boat", "bus", "car",
	// "motorcycle", "run", "subway", "taxi",
	// "train", "walk" };
	
	static int counterEditAllEndPoints = 0;
	
	/**
	 * Writes a file with MUs as rows, Users as columns and the MRR as the cell value.
	 * 
	 * @param rootPath
	 *            the path to read, i.e., the root path for all MU results
	 * @param absFileNameToWrite
	 *            file name to write for MRR for all user and all MUs result
	 * @return number of users
	 */
	public static int writeMRRForAllUsersAllMUs(String rootPath, String absFileNameToWrite)
	{
		double[] matchingUnitArray = null;
		int numberOfUsers = -1;
		
		if (rootPath != null)
		{
			WritingToFile.appendLineToFileAbsolute("MUs/Users\n", absFileNameToWrite);
			
			if (Constant.lookPastType.equals("Count"))
			{
				matchingUnitArray = Constant.matchingUnitAsPastCount;// matchingUnitAsPastCount; // PopUps.showMessage(matchingUnitArray.toString());
			}
			else if (Constant.lookPastType.equals("Hrs"))
			{
				matchingUnitArray = Constant.matchingUnitHrsArray;// matchingUnitHrsArray; // PopUps.showMessage(matchingUnitArray.toString());
			}
			else
			{
				System.err.println("Error: unknown look past type in in setMatchingUnitArray() RecommendationTests()");
				System.exit(-1);
			}
			
			for (double mu : matchingUnitArray)
			{
				String fileName = rootPath + "MatchingUnit" + mu + "/AlgoAllMeanReciprocalRank.csv";
				
				List<Double> mrrVals = ReadingFromFile.oneColumnReaderDouble(fileName, ",", 1, true);
				numberOfUsers = mrrVals.size(); // note we need to do this only once, but no harm done if done multiple times, overwriting the same value.
				String mrrValsString = mrrVals.stream().map(Object::toString).collect(Collectors.joining(","));
				
				WritingToFile.appendLineToFileAbsolute("" + mu + "," + mrrValsString + "\n", absFileNameToWrite);
			}
			
			// writeMaxOfColumns(absFileNameToWrite, absFileNameToWrite + "MaxOfCols.csv", 1, 18, matchingUnitArray);
		}
		else
		{
			System.out.println("root path is empty");
		}
		
		return numberOfUsers;
	}
	
	/**
	 * 
	 * 
	 * @param absFileNameToRead
	 *            with each col corresponding to user while each row corresponding to an MU and the cell values containing the corresponding MRR
	 * @param absFileNameToWrite
	 * @param numberOfUsers
	 * @param hasRowHeader
	 * @param booleanHasColHeader
	 * @return LinkedHashMap (UserID, Pair( MUs having Max MRR, max MRR)) // User ID as User1, User2, ...
	 */
	public static LinkedHashMap<String, Pair<List<Double>, Double>> writeDescendingMRRs(String absFileNameToRead, String absFileNameToWrite,
			int numberOfUsers, boolean hasRowHeader, boolean booleanHasColHeader)
	{
		int startColIndx = 0, lastColIndx = numberOfUsers - 1;
		List<Double> rowLabels = new ArrayList<Double>();
		
		// (User, Pair( MUs having Max MRR, max MRR))
		LinkedHashMap<String, Pair<List<Double>, Double>> usersMaxMUMRRMap = new LinkedHashMap<String, Pair<List<Double>, Double>>();
		
		if (hasRowHeader)
		{
			startColIndx += 1; // 1
			lastColIndx += 1; // 18
			rowLabels = ReadingFromFile.oneColumnReaderDouble(absFileNameToRead, ",", 0, booleanHasColHeader);
		}
		else
		{
			int numOfRows = ReadingFromFile.oneColumnReaderDouble(absFileNameToRead, ",", 0, booleanHasColHeader).size();
			for (int i = 0; i < numOfRows; i++)
			{
				rowLabels.add(Double.valueOf(i)); // Row = 0 to Row = <numOfUsers-1>
			}
		}
		
		WritingToFile.appendLineToFileAbsolute("User" + ",MU, MRR\n", absFileNameToWrite);
		LinkedHashMap<String, String> userCluster = new LinkedHashMap<String, String>();
		
		for (int colInd = startColIndx; colInd <= lastColIndx; colInd++) // each column is for a user
		{
			List<Double> mrrVals = ReadingFromFile.oneColumnReaderDouble(absFileNameToRead, ",", colInd, booleanHasColHeader);
			
			// (MU,MRR)
			LinkedHashMap<Double, Double> mrrMap = new LinkedHashMap<Double, Double>();
			
			int serialNum = 0;
			for (Double v : mrrVals)
			{
				mrrMap.put(rowLabels.get(serialNum), v);
				serialNum++;
			}
			
			mrrMap = (LinkedHashMap<Double, Double>) UtilityBelt.sortByValue(mrrMap);// sorted by descending vals
			
			double maxMRR = Collections.max(mrrMap.values()); // for this col, i.e., for this user
			List<Double> MUsHavingMaxMRR = new ArrayList<Double>(); // for this col, i.e., for this user
			
			// find the MU's having this max MRR
			for (Entry<Double, Double> entry : mrrMap.entrySet())
			{
				if (entry.getValue() == maxMRR)
				{
					MUsHavingMaxMRR.add(entry.getKey());// adding the corresponding MU
				}
				WritingToFile.appendLineToFileAbsolute("User " + colInd + "," + entry.getKey() + "," + entry.getValue() + "\n",
						absFileNameToWrite);
			}
			WritingToFile.appendLineToFileAbsolute("\n", absFileNameToWrite);
			
			Collections.sort(MUsHavingMaxMRR); // MUs with have the max MRR are sorted in ascending order of their MU value, just for convenience of reading
			// Pair(List of MUs with highest MRR, highestMRR)
			Pair<List<Double>, Double> MUsWithMaxMRR = new Pair(MUsHavingMaxMRR, maxMRR);
			usersMaxMUMRRMap.put("User" + colInd, MUsWithMaxMRR);
			
			// WritingToFile.appendLineToFileAbsolute(
			// "User " + colInd + "," + maxMUMRR.getFirst() + "," + maxMUMRR.getSecond() + "," + getClusterLabel(Double.valueOf(maxMUMRR.getFirst()))
			// + "\n", absFileNameToWrite + "Cluster.csv");
		}
		
		return usersMaxMUMRRMap;
	}
	
	/**
	 * Incomplete to write the max MRR over MUs
	 * 
	 * @param absFileNameToRead
	 * @param absFileNameToWrite
	 * @param startColIndx
	 * @param lastColIndx
	 */
	public static void writeMaxOfColumns(String absFileNameToRead, String absFileNameToWrite, int startColIndx, int lastColIndx,
			double[] matchingUnitArray)// , boolean
										// hasColHeader)
	{
		// int startColInd = 0;
		// if (hasColHeader)
		// {
		// startColInd = 1;
		// }
		WritingToFile.appendLineToFileAbsolute("User" + ",MU, MRR\n", absFileNameToWrite);
		
		LinkedHashMap<String, String> userCluster = new LinkedHashMap<String, String>();
		
		for (int colInd = startColIndx; colInd <= lastColIndx; colInd++) // each column is for a user
		{
			List<Double> mrrVals = ReadingFromFile.oneColumnReaderDouble(absFileNameToRead, ",", colInd, true);
			
			LinkedHashMap<String, Double> mrrMap = new LinkedHashMap<String, Double>();
			
			int count = 0;
			for (Double v : mrrVals)
			{
				
				mrrMap.put(Double.toString(matchingUnitArray[count]), v);
				count++;
			}
			
			mrrMap = (LinkedHashMap<String, Double>) UtilityBelt.sortByValue(mrrMap);// sorted by descending vals
			
			Pair<String, Double> maxMUMRR = new Pair<String, Double>("0", 0.0);
			for (Entry<String, Double> entry : mrrMap.entrySet())
			{
				// String mrrMapString = mrrMap.stream().map(Object::toString).collect(Collectors.joining(","));
				if (entry.getValue() > maxMUMRR.getSecond())
				{
					maxMUMRR = new Pair(entry.getKey(), entry.getValue());
				}
				WritingToFile.appendLineToFileAbsolute("User " + colInd + "," + entry.getKey() + "," + entry.getValue() + "\n",
						absFileNameToWrite);
			}
			
			WritingToFile.appendLineToFileAbsolute("\n", absFileNameToWrite);
			
			WritingToFile.appendLineToFileAbsolute(
					"User " + colInd + "," + maxMUMRR.getFirst() + "," + maxMUMRR.getSecond() + ","
							+ WekaUtilityBelt.getClusterLabelClustering0(Double.valueOf(maxMUMRR.getFirst())) + "\n",
					absFileNameToWrite + "Cluster.csv");
		}
	}
	
	public static void main(String args[])
	{
		// List<Double> vals = new ArrayList<Double>();
		// vals.add(12.2);
		// vals.add(34.0);
		// vals.add(55.0);
		//
		// String joined = vals.stream().map(Object::toString).collect(Collectors.joining(","));
		
		// LinkedHashMap<String,String> map = new Map
		// System.out.println(joined);
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan27Daywise/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan27DaywiseAllMRR.csv");
		
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan27NCount/Geolife/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan27NCountAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan28NCount/Geolife/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan28NCountAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan28NCount2NoShuffle/Geolife/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan28NCount2NoShuffleAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan28NCount3NoShuffle/Geolife/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan28NCount3NoShuffleAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/August14/Geolife/SimpleV3/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Aug14NCountAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/August14/Geolife/SimpleV3/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Aug14NCountAllMRR.csv");
		//
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/June18HJDistance/Geolife/SimpleV3/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/June18NCountAllMRR.csv");
		
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Jan27NCountBlackListed/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Jan27NCountBlackListedAllMRR.csv");
		
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Feb4NCount/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Feb4NCountAllMRR.csv");
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/Feb4NCount2/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/Feb4NCount2AllMRR.csv");
		// writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/June18HJDistance/Geolife/SimpleV3/",
		// "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/June18HJDistanceAllMRR.csv");
		writeMRRForAllUsersAllMUs("/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/June18HJDistance/Geolife/SimpleV3/",
				"/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/ComparisonsJan28/June18HJDistanceAllMRR.csv");
		
		// /
		//
	}
	
	/**
	 * Returns stream to redirect the console output and error to the given path. (note: redirects the system output stream as well as system error stream.)
	 * 
	 * @param fullPathFileName
	 *            absolute path with filename
	 * @return PrintStream <b><font color="red">Remember to close this Printstream after writing to it.</font></b>
	 */
	public static PrintStream redirectConsoleOutput(String fullPathFileName)
	{
		PrintStream consoleLogStream = null;
		try
		{
			File consoleLog = new File(fullPathFileName);
			consoleLog.delete();
			consoleLog.createNewFile();
			consoleLogStream = new PrintStream(consoleLog);
			// System.setOut(new PrintStream(new FileOutputStream("/dev/stdout")));
			System.setOut(new PrintStream(consoleLogStream));
			System.setErr(consoleLogStream);
		}
		catch (Exception e)
		{
			System.out.println("Exception generated for fullPathFileName =" + fullPathFileName);
			e.printStackTrace();
		}
		
		return consoleLogStream;
	}
	
	/**
	 * Returns a BufferedWriter for the file (with append as true). Alert:If the file exists, the old file is deleted and new file is created.
	 * 
	 * @param fullAbsolutePath
	 *            absolute path for the file
	 * @return BufferedWriter for given file
	 */
	public static BufferedWriter getBufferedWriterForNewFile(String fullPath)
	{
		BufferedWriter bw = null;
		// System.out.println("fullpath =" + fullPath);
		try
		{
			File file = new File(fullPath);
			file.delete();
			file.createNewFile();
			
			FileWriter writer = new FileWriter(file.getAbsoluteFile(), true);
			bw = new BufferedWriter(writer);
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-90);
		}
		return bw;
	}
	
	/**
	 * Returns a BufferedWriter for the file (with append as true). Alert:If the file exists, the old file is kept and new values are appended at the end.
	 * 
	 * @param fullAbsolutePath
	 *            absolute path for the file
	 * @return BufferedWriter for given file
	 */
	public static BufferedWriter getBufferedWriterForExistingFile(String fullPath)
	{
		BufferedWriter bw = null;
		// System.out.println("fullpath =" + fullPath);
		try
		{
			File file = new File(fullPath);
			FileWriter writer = new FileWriter(file.getAbsoluteFile(), true);
			bw = new BufferedWriter(writer);
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-90);
		}
		return bw;
	}
	
	public static void writeArrayList2(ArrayList<Pair<String, Long>> arrayList, String fileNameToUse, String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Pair<String, Long> t : arrayList)
			{
				
				bw.write(t.getFirst().toString() + "," + t.getSecond().toString());
				bw.newLine();
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeArrayList(ArrayList<Pair<String, Long>> arrayList, String fileNameToUse, String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Pair<String, Long> t : arrayList)
			{
				
				bw.write(Integer.parseInt(t.getFirst()) + "," + t.getSecond().toString());
				bw.newLine();
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeArrayListOfArrayList(ArrayList<ArrayList<Double>> arrayArrayList, String fileNameToUse, String headerLine,
			String commonPath)
	{
		// commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (int mu = 0; mu < arrayArrayList.size(); mu++)
			{
				ArrayList<Double> editDistances = arrayArrayList.get(mu);
				
				for (int pair = 0; pair < editDistances.size(); pair++)// (Pair<String, Long> t : arrayList)
				{
					bw.write((mu + 1) + "," + pair + "," + editDistances.get(pair));// Integer.parseInt(t.getFirst()) +
																					// "," + t.getSecond().toString());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeArrayList(ArrayList<Double> arrayList, String fileNameToUse, String headerLine, String commonPath)
	{
		// commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			if (headerLine.length() > 0)
			{
				bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
				bw.newLine();
			}
			for (int pair = 0; pair < arrayList.size(); pair++)// (Pair<String, Long> t : arrayList)
			{
				bw.write(arrayList.get(pair).toString());// Integer.parseInt(t.getFirst()) + "," +
															// t.getSecond().toString());
				bw.newLine();
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param arrayList
	 * @param fullPath
	 *            with file extension
	 * @param headerLine
	 */
	public static void writeArrayListAbsolute(ArrayList<Double> arrayList, String fullPath, String headerLine)
	{
		// commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = fullPath;
			System.out.println("full path = " + fullPath);
			
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			if (headerLine.length() > 0)
			{
				bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
				bw.newLine();
			}
			for (int pair = 0; pair < arrayList.size(); pair++)// (Pair<String, Long> t : arrayList)
			{
				bw.write(arrayList.get(pair).toString());// Integer.parseInt(t.getFirst()) + "," +
															// t.getSecond().toString());
				bw.newLine();
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeArrayListFlatActivityLogEntry(ArrayList<FlatActivityLogEntry> arrayList, String fileNameToUse,
			String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			file.delete();
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (FlatActivityLogEntry t : arrayList)
			{
				
				bw.write(t.toStringWithoutHeaders());
				bw.newLine();
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Writes the given string to a new file with given filename
	 * 
	 * @param msg
	 * @param absFileNameToUse
	 * @param headerLin
	 */
	public static void writeToNewFile(String msg, String absFileNameToUse)
	{
		// commonPath = Constant.getCommonPath();//
		// System.out.println("commonPath in writeString() is " + commonPath);
		try
		{
			// String fileName = commonPath + absFileNameToUse;// + ".csv";
			File file = new File(absFileNameToUse);
			file.delete();
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(msg);
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Note: .csv automatically added to the name
	 * 
	 * @param msg
	 * @param fileNameToUse
	 */
	public static void appendLineToFile(String msg, String fileNameToUse)
	{
		commonPath = Constant.getCommonPath();//
		// System.out.println("commonPath in writeString() is "+commonPath);
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			File file = new File(fileName);
			
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(msg);// + "\n");
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeNegativeZeroInvalidsLatLonAltHeader(String fileNameToUse)
	{
		appendLineToFile(
				"User," + "NumOfNegativeLatitudes, NumOfZeroLatitude,NumOfUnknownLatitudes,"
						+ "NumOfNegativeLongitudes, NumOfZeroLongitude,NumOfUnknownLongitudes,"
						+ "NumOfNegativeAltitudes, NumOfZeroAltitude,NumOfUnknownAltitudes, TotalNumOfTrajectoryEntries" + "\n",
				fileNameToUse);
	}
	
	public static void writeNegativeZeroInvalidsLatLonAltFooter(String fileNameToUse)
	{
		appendLineToFile("Note: This stat is generated during parsing the raw trajectoy entries." + "\n", fileNameToUse);
	}
	
	public static void writeNegativeZeroInvalidsLatLonAlt(String userName, String fileNameToUse)
	{
		
		String stringToWrite = userName + "," + TrajectoryEntry.getCountNegativeLatitudes() + "," + TrajectoryEntry.getCountZeroLatitudes()
				+ "," + TrajectoryEntry.getCountUnknownLatitudes() + "," + TrajectoryEntry.getCountNegativeLongitudes() + ","
				+ TrajectoryEntry.getCountZeroLongitudes() + "," + TrajectoryEntry.getCountUnknownLongitudes() + ","
				+ TrajectoryEntry.getCountNegativeAltitudes() + "," + TrajectoryEntry.getCountZeroAltitudes() + ","
				+ TrajectoryEntry.getCountUnknownAltitudes() + "," + TrajectoryEntry.getTotalCountTrajectoryEntries() + "\n";
		
		appendLineToFile(stringToWrite, fileNameToUse);
	}
	
	/**
	 * 
	 * @param msg
	 * @param fullPathfileNameToUse
	 *            with file extension
	 */
	public static void appendLineToFileAbsolute(String msg, String fullPathfileNameToUse)
	{
		try
		{
			String fileName = fullPathfileNameToUse;
			// PopUps.showMessage("Inside appendLineToFileAbsolute() for filename " + fileName);
			File file = new File(fileName);
			
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(msg);// + "\n");
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimestampedActivityObjectsForUser(LinkedHashMap<Timestamp, ActivityObject> ts, String fileNameToUse,
			String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, ActivityObject> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				// String actNameToPut;
				if (entry.getValue() == null) // no ao at this time
				{
					continue;
				}
				
				bw.write(timestamp.substring(0, timestamp.length() - 2) + "," + entry.getValue().getActivityName() + "\n");
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeAllTimestampedActivityObjects(LinkedHashMap<String, LinkedHashMap<Timestamp, ActivityObject>> ts,
			String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, ActivityObject>> entry : ts.entrySet())
			{
				writeTimestampedActivityObjectsForUser(entry.getValue(), fileNameToUse, entry.getKey());
			}
			
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimeSeriesIntForUser(LinkedHashMap<Timestamp, Integer> ts, String fileNameToUse, String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, Integer> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				bw.write(timestamp.substring(0, timestamp.length() - 2) + "," + entry.getValue() + "\n"); // also
																											// removes
																											// the last
																											// nano
																											// seconds
																											// precision
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimeSeriesDoubleForUser(LinkedHashMap<Timestamp, Double> ts, String fileNameToUse, String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, Double> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				bw.write(timestamp.substring(0, timestamp.length() - 2) + "," + entry.getValue() + "\n"); // also
																											// removes
																											// the last
																											// nano
																											// seconds
																											// precision
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimeSeriesLongForUser(LinkedHashMap<Timestamp, Long> ts, String fileNameToUse, String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, Long> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				bw.write(timestamp.substring(0, timestamp.length() - 2) + "," + entry.getValue() + "\n"); // also
																											// removes
																											// the last
																											// nano
																											// seconds
																											// precision
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimeSeriesOnlyIntValueForUser(LinkedHashMap<Timestamp, Integer> ts, String fileNameToUse, String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, Integer> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				bw.write(entry.getValue() + "\n"); // also removes the last nano seconds precision
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeTimeSeriesCharForUser(LinkedHashMap<Timestamp, String> ts, String fileNameToUse, String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + userName + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Timestamp, String> entry : ts.entrySet())
			{
				String timestamp = entry.getKey().toString();
				
				bw.write(timestamp.substring(0, timestamp.length() - 2) + "," + entry.getValue() + "\n"); // also
																											// removes
																											// the last
																											// nano
																											// seconds
																											// precision
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeShannonEntropy(LinkedHashMap<String, Double> ts, String fileNameToUse)// , String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<String, Double> entry : ts.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue() + "\n");
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMap(LinkedHashMap<String, String> ts, String fileNameToUse)// , String userName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<String, String> entry : ts.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue() + "\n");
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapStrInt(LinkedHashMap<String, Integer> ts, String absFileNameToUse)// , String userName)
	{
		// commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = absFileNameToUse;// commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<String, Integer> entry : ts.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue() + "\n");
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// TODO: make it for generic types..not just Double
	public static void writeLinkedHashMapOfArrayList(LinkedHashMap<String, ArrayList<Double>> ts, String absfileNameToUse)// , String userName)
	{
		// commonPath = Constant.getCommonPath();//
		try
		{
			File file = new File(absfileNameToUse);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<String, ArrayList<Double>> entry : ts.entrySet())
			{
				String s = entry.getKey();// + ",";
				
				for (Double t : entry.getValue())
				{
					s += "," + t.toString();
				}
				bw.write(s + "\n");
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private static Complex[] getFTTransform(double[] values)
	{
		
		return null;
	}
	
	public static void writeAllTimeSeriesInt(LinkedHashMap<String, LinkedHashMap<Timestamp, Integer>> ts, String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, Integer>> entry : ts.entrySet())
			{
				int userName = Integer.valueOf(entry.getKey());// UtilityBelt.getIndexOfUserID(Integer.valueOf(entry.getKey()));
				writeTimeSeriesIntForUser(entry.getValue(), fileNameToUse, String.valueOf(userName));
			}
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeAllTimeSeriesDouble(LinkedHashMap<String, LinkedHashMap<Timestamp, Double>> ts, String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, Double>> entry : ts.entrySet())
			{
				int userName = Integer.valueOf(entry.getKey());// UtilityBelt.getIndexOfUserID(Integer.valueOf(entry.getKey()));
				writeTimeSeriesDoubleForUser(entry.getValue(), fileNameToUse, String.valueOf(userName));
			}
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeAllTimeSeriesLong(LinkedHashMap<String, LinkedHashMap<Timestamp, Long>> ts, String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, Long>> entry : ts.entrySet())
			{
				int userName = Integer.valueOf(entry.getKey());// UtilityBelt.getIndexOfUserID(Integer.valueOf(entry.getKey()));
				writeTimeSeriesLongForUser(entry.getValue(), fileNameToUse, String.valueOf(userName));
			}
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeAllTimeSeriesOnlyIntValue(LinkedHashMap<String, LinkedHashMap<Timestamp, Integer>> ts, String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, Integer>> entry : ts.entrySet())
			{
				int userName = Integer.valueOf(entry.getKey());// UtilityBelt.getIndexOfUserID(Integer.valueOf(entry.getKey()));
				writeTimeSeriesOnlyIntValueForUser(entry.getValue(), fileNameToUse, String.valueOf(userName));
			}
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeAllTimeSeriesChar(LinkedHashMap<String, LinkedHashMap<Timestamp, String>> ts, String fileNameToUse)
	{
		try
		{
			for (Map.Entry<String, LinkedHashMap<Timestamp, String>> entry : ts.entrySet())
			{
				int userName = Integer.valueOf(entry.getKey());// UtilityBelt.getIndexOfUserID(Integer.valueOf(entry.getKey()));
				writeTimeSeriesCharForUser(entry.getValue(), fileNameToUse, String.valueOf(userName));
			}
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// // primarily used for writing unknowns, might be better to use generics here K,V
	public static void writeLinkedHashMapOfTreemap(LinkedHashMap<String, TreeMap<Timestamp, String>> mapOfMap, String fileNameToUse,
			String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : mapOfMap.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, String> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<Timestamp, String> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getKey() + "," + entryInside.getValue());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapOfTreemap2(LinkedHashMap<String, TreeMap<Timestamp, TrajectoryEntry>> mapOfMap,
			String fileNameToUse, String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, TrajectoryEntry>> entryForUser : mapOfMap.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, TrajectoryEntry> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<Timestamp, TrajectoryEntry> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getValue().toStringWithoutHeaders());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapOfTreemapDE(LinkedHashMap<String, TreeMap<Timestamp, DataEntry>> mapOfMap, String fileNameToUse,
			String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, DataEntry>> entryForUser : mapOfMap.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, DataEntry> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<Timestamp, DataEntry> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getValue().toStringWithoutHeaders());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapOfTreemapTLE(LinkedHashMap<String, TreeMap<Timestamp, TrackListenEntry>> mapOfMap,
			String fileNameToUse, String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, TrackListenEntry>> entryForUser : mapOfMap.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, TrackListenEntry> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<Timestamp, TrackListenEntry> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getValue().toStringWithoutHeaders());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapOfTreemapPureTrajectoryEntries(LinkedHashMap<String, TreeMap<Timestamp, TrajectoryEntry>> mapOfMap,
			String fileNameToUse, String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, TrajectoryEntry>> entryForUser : mapOfMap.entrySet())
			{
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, TrajectoryEntry> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<Timestamp, TrajectoryEntry> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getValue().toStringEssentialsWithoutHeaders());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeLinkedHashMapOfTreemapAllString(LinkedHashMap<String, TreeMap<String, String>> mapOfMap, String fileNameToUse,
			String headerLine)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + fileNameToUse + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(headerLine);// .replaceAll("||",",")); //replacing pipes by commma
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<String, String>> entryForUser : mapOfMap.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<String, String> mapForEachUser = entryForUser.getValue();
				
				for (Map.Entry<String, String> entryInside : mapForEachUser.entrySet())
				{
					bw.write(userName + "," + entryInside.getKey().toString() + "," + entryInside.getValue().toString());
					bw.newLine();
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// //
	
	// /////////
	/**
	 * Write to file about the 'Not Annotated' images in the given LinkedHashMap
	 * 
	 * @param data
	 *            LinkedHashMap containing the data
	 * @param filenameEndPhrase
	 *            Name for the file to be written
	 */
	public static void writeActivityTypeWithTimeDifference2(LinkedHashMap<String, TreeMap<Timestamp, TrajectoryEntry>> data,
			String activityNameToLookFor, String fileNameEnd)
	{
		String fileName = commonPath + activityNameToLookFor.replaceAll(" ", "_") + fileNameEnd + ".csv";
		
		try
		{
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("UserName,ImageTimestamp,DifferenceInSecondsWithNext, ActivityName");
			bw.newLine();
			for (Map.Entry<String, TreeMap<Timestamp, TrajectoryEntry>> entryForUser : data.entrySet())
			{
				
				String userName = entryForUser.getKey();
				
				TreeMap<Timestamp, TrajectoryEntry> mapForEachUser = new TreeMap<Timestamp, TrajectoryEntry>();
				
				for (Map.Entry<Timestamp, TrajectoryEntry> entry : entryForUser.getValue().entrySet())
				{
					// System.out.println(entry.getKey()+","+entry.getValue());
					if (entry.getValue().getMode().equalsIgnoreCase(activityNameToLookFor))
						bw.write(userName + "," + entry.getValue().toString() + "\n");
				}
			}
			bw.close();
		} // end of try
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// ////
	// /////////
	/**
	 * Write to file about the 'Not Annotated' images in the given LinkedHashMap
	 * 
	 * @param data
	 *            LinkedHashMap containing the data
	 * @param filenameEndPhrase
	 *            Name for the file to be written
	 */
	public static void writeActivityTypeWithTimeDifference(LinkedHashMap<String, TreeMap<Timestamp, String>> data,
			String activityNameToLookFor, String fileNameEnd)
	{
		String fileName = commonPath + activityNameToLookFor.replaceAll(" ", "_") + fileNameEnd + ".csv";
		
		try
		{
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("UserName,ImageTimestamp,DifferenceInSecondsWithNext, ActivityName");
			bw.newLine();
			for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : data.entrySet())
			{
				
				String userName = entryForUser.getKey();
				
				TreeMap<Timestamp, String> mapForEachUser = new TreeMap<Timestamp, String>();
				
				for (Map.Entry<Timestamp, String> entry : entryForUser.getValue().entrySet())
				{
					// System.out.println(entry.getKey()+","+entry.getValue());
					if (entry.getValue().contains(activityNameToLookFor))
						bw.write(userName + "," + entry.getKey() + "," + entry.getValue() + "\n");
				}
			}
			bw.close();
		} // end of try
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// ////
	
	/**
	 * To write the occurrence of given Activity name with duration and if that occurrence is a sandwich case.
	 * 
	 * @param mapForAllDataMergedPlusDuration
	 * @param activityNameToLookFor
	 *            the activity name to be looked for. If all avtivity names are to be looked for then use 'everything'
	 * @param fileNameEnd
	 * @param onlySandwich
	 *            if true only sandwich cases are mentioned, else sandwich as well as non-sandwich cases are mentioned
	 */
	public static void writeActivityTypeWithDurationGeo(
			LinkedHashMap<String, TreeMap<Timestamp, TrajectoryEntry>> mapForAllDataMergedPlusDuration, String activityNameToLookFor,
			String fileNameEnd, boolean onlySandwiches)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + activityNameToLookFor.replaceAll(" ", "_") + fileNameEnd + ".csv";
			File file = new File(fileName);
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(
					"User_Name,Timestamp,Activity_Name,Duration_in_seconds,Is_Sandwich_Case,Preceeding_Activity,Succeeding_Activity,Timediff_with_prev,Timediff_with_next");// ,Num_of_data_points_Merged");
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, TrajectoryEntry>> entryForUser : mapForAllDataMergedPlusDuration.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, TrajectoryEntry> mapForEachUser = entryForUser.getValue();
				String preceedingActivity = "";
				String succeedingActivity = "";
				
				int isSandwichCase = -99;
				
				ArrayList<TrajectoryEntry> entriesForUser = UtilityBelt.treeMapToArrayListGeo(mapForEachUser);
				
				for (int i = 0; i < entriesForUser.size(); i++)// String entry: entriesForUser)
				{
					// $$ System.out.println("Size is:"+entriesForUser.size()+" index is "+i);
					TrajectoryEntry te = entriesForUser.get(i);
					
					Timestamp timestamp = te.getTimestamp();
					String activityName = te.getMode();// splitted[1];
					String activityDurationInSecs = Long.toString(te.getDurationInSeconds());// splitted[2];
					long timeDiffWithPrev = -99, timeDiffWithNext = -99;
					
					// //////////////WRONG...PREfiltering give few wrong cases like like X---Target-- Empty is
					// classified as sandwich ..CHECKed IF THIS HAS ANY
					// EFFECT ON RESULT, LOGICALLY IT
					// SHOULDNT
					// if(!(activityName.trim().equalsIgnoreCase(activityNameToLookFor))){continue;}
					// //////////////
					
					if (i == 0) // no preceeding as its first
					{
						preceedingActivity = "--";
					}
					else if (i != 0) // our concern
					{
						// String splittedP[]=entriesForUser.get(i-1).split(Pattern.quote("||"));
						// preceedingActivity=splittedP[1];
						preceedingActivity = entriesForUser.get(i - 1).getMode();
						timeDiffWithPrev = (te.getTimestamp().getTime() - entriesForUser.get(i - 1).getTimestamp().getTime()) / 1000;
					}
					
					if (i == (entriesForUser.size() - 2)) // no succeeding activity as it is the last activity
					{
						succeedingActivity = "--";
					}
					
					else if (i < (entriesForUser.size() - 2)) // our concern
					{
						// String splittedS[]=entriesForUser.get(i+1).split(Pattern.quote("||"));
						// succeedingActivity=splittedS[1];
						succeedingActivity = entriesForUser.get(i + 1).getMode();
						timeDiffWithNext = (entriesForUser.get(i + 1).getTimestamp().getTime() - te.getTimestamp().getTime()) / 1000;
					}
					
					if (!(succeedingActivity.equals("--") || preceedingActivity.equals("--"))
							&& succeedingActivity.equals(preceedingActivity))
					{
						isSandwichCase = 1;
					}
					
					else if (!((!(succeedingActivity.equals("--") || preceedingActivity.equals("--"))
							&& succeedingActivity.equals(preceedingActivity))))
					{
						isSandwichCase = 0;
					}
					
					else
					{
						System.err.println("Check Error: This should be unreachable code in writeActivityTypeWithDurationGeo");
					}
					
					// //////////////////////////////////////////////////////////
					// write all activity names, NO FILTER
					if (activityNameToLookFor.toLowerCase().trim().equals("everything"))
					{
						if (onlySandwiches == false) // NO SANDWICH FILTER
						{
							bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + "," + isSandwichCase
									+ "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev + ","
									+ timeDiffWithNext);
							bw.newLine();
						}
						else if (onlySandwiches == true) // SANDWICH FILTER
						{
							if (isSandwichCase == 1) // sandwiches
							{
								bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + ","
										+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev
										+ "," + timeDiffWithNext);
								bw.newLine();
							}
						}
					} // // Write only valids activity names
					else if (activityNameToLookFor.toLowerCase().trim().equals("validsonly"))
					{
						if (onlySandwiches == false) // NO SANDWICH FILTER
						{
							if (((activityName.trim().equalsIgnoreCase(Constant.INVALID_ACTIVITY1))
									|| (activityName.trim().equalsIgnoreCase(Constant.INVALID_ACTIVITY2))) == false)
							{
								bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + ","
										+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev
										+ "," + timeDiffWithNext);
								bw.newLine();
							}
						}
						else if (onlySandwiches == true) // SANDWICH FILTER
						{
							if ((((activityName.trim().equalsIgnoreCase(Constant.INVALID_ACTIVITY1))
									|| (activityName.trim().equalsIgnoreCase(Constant.INVALID_ACTIVITY2))) == false) && isSandwichCase == 1) // not
																																				// just
																																				// sandwiches
							{
								bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + ","
										+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev
										+ "," + timeDiffWithNext);
								bw.newLine();
							}
						}
					}
					
					else
					// write only given activity names ,ACTIVITY NAME FILTER
					{
						if (onlySandwiches == false) // NO SANDWICH FILTER
						{
							if (activityName.trim().equalsIgnoreCase(activityNameToLookFor)) // not just sandwiches
							{
								bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + ","
										+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev
										+ "," + timeDiffWithNext);
								bw.newLine();
							}
						}
						else if (onlySandwiches == true) // SANDWICH FILTER
						{
							if (activityName.trim().equalsIgnoreCase(activityNameToLookFor) && isSandwichCase == 1) // not
																													// just
																													// sandwiches
							{
								bw.write(userName + "," + timestamp + "," + activityName + "," + activityDurationInSecs + ","
										+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + timeDiffWithPrev
										+ "," + timeDiffWithNext);
								bw.newLine();
							}
						}
					}
					
				}
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// ////
	
	/**
	 * 
	 * @param mapForAllDataMergedPlusDuration
	 *            <UserName, <Timestamp,'activityname||durationInSeconds'>>
	 */
	public static void writeActivityTypeWithDuration(LinkedHashMap<String, TreeMap<Timestamp, String>> mapForAllDataMergedPlusDuration,
			String activityNameToLookFor, String fileNameEnd)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + activityNameToLookFor.replaceAll(" ", "_") + fileNameEnd + ".csv";
			
			File file = new File(fileName);
			
			file.delete();
			if (!file.exists())
			{
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(
					"User_Name,Timestamp,Activity_Name,Duration_in_seconds,Is_Sandwich_Case,Preceeding_Activity,Succeeding_Activity,Num_of_Images_Merged");
			bw.newLine();
			
			for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : mapForAllDataMergedPlusDuration.entrySet())
			{
				
				String userName = entryForUser.getKey();
				TreeMap<Timestamp, String> mapForEachUser = entryForUser.getValue();
				
				// <Timestamp,'activityname||durationInSeconds'>
				String preceedingActivity = "";
				String succeedingActivity = "";
				int isSandwichCase = -99;
				
				ArrayList<String> entriesForUser = UtilityBelt.treeMapToArrayListString(mapForEachUser);
				
				for (int i = 0; i < entriesForUser.size(); i++)// String entry: entriesForUser)
				{
					// $$ System.out.println("Size is:"+entriesForUser.size()+" index is "+i);
					
					String splitted[] = entriesForUser.get(i).split(Pattern.quote("||"));
					// String dateString= UtilityBelt.getDateString(new Timestamp(Long.valueOf(splitted[0])));
					Timestamp timestamp = new Timestamp(Long.valueOf(splitted[0]));
					String activityName = splitted[1];
					String activityDurationInSecs = splitted[2];
					
					String theRest = "";
					
					int k = 3;
					while (k < splitted.length)
					{
						theRest = theRest + splitted[k] + ",";
						// $$System.out.println("k="+k);
						k++;
					}
					
					if (i == 0)
					{
						preceedingActivity = "--";
					}
					else if (i != 0)
					{
						String splittedP[] = entriesForUser.get(i - 1).split(Pattern.quote("||"));
						preceedingActivity = splittedP[1];
					}
					
					if (i == (entriesForUser.size() - 2))
					{
						succeedingActivity = "--";
					}
					
					else if (i < (entriesForUser.size() - 2))
					{
						String splittedS[] = entriesForUser.get(i + 1).split(Pattern.quote("||"));
						succeedingActivity = splittedS[1];
					}
					
					if (!(succeedingActivity.equals("--") || preceedingActivity.equals("--"))
							&& succeedingActivity.equals(preceedingActivity))
					{
						isSandwichCase = 1;
					}
					
					else if (!((!(succeedingActivity.equals("--") || preceedingActivity.equals("--"))
							&& succeedingActivity.equals(preceedingActivity))))
					{
						isSandwichCase = 0;
					}
					
					if (activityName.trim().equalsIgnoreCase(activityNameToLookFor) && isSandwichCase == 1) // remove
																											// this
																											// condition
																											// after
																											// experiment
																											// //this
																											// enumerates
																											// only
																											// sandwiched
					{
						bw.write(userName + "," + timestamp + "," + activityNameToLookFor + "," + activityDurationInSecs + ","
								+ isSandwichCase + "," + preceedingActivity + "," + succeedingActivity + "," + theRest);
						bw.newLine();
					}
					
					/*
					 * else if(activityName.trim().equalsIgnoreCase("Not Available")) { bw.write(userName+","+timestamp+",Not_Annotated,"+activityDurationInSecs+
					 * ","+isSandwichCase+","+preceedingActivity+","+succeedingActivity+","); bw.newLine(); }
					 */
					
				}
				
				// for(Map.Entry<Timestamp, String> entry:entryForUser.getValue().entrySet())
				// {
				// String activityNameDuration=entry.getValue();
				//
				// String activityName=DCU_Data_Loader.getActivityNameFromDataEntry(activityNameDuration);
				// long activityDurationInSecs=DCU_Data_Loader.getDurationInSecondsFromDataEntry(activityNameDuration);
				// String dateString= UtilityBelt.getDateString(entry.getKey());
				//
				//
				// if(activityName.trim().equalsIgnoreCase("badImages"))
				// {
				// bw.write(userName+","+dateString+",Bad_Images,"+activityDurationInSecs);
				// bw.newLine();
				// }
				//
				// else if(activityName.trim().equalsIgnoreCase("Not Available"))
				// {
				// bw.write(userName+","+dateString+",Not_Annotated,"+activityDurationInSecs);
				// bw.newLine();
				// }
				// }
				
			}
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static int getNumberOfWeekendsInGivenDayTimelines(LinkedHashMap<Date, UserDayTimeline> userTimelines)
	{
		int numberOfWeekends = 0;
		
		for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
		{
			int weekDayInt = entry.getKey().getDay();
			
			if (weekDayInt == 0 || weekDayInt == 6)
			{
				numberOfWeekends++;
			}
		}
		
		return numberOfWeekends;
	}
	
	public static void writeEditSimilarityCalculation(ArrayList<ActivityObject> ActivityObjects1,
			ArrayList<ActivityObject> ActivityObjects2, double editDistance)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditSimilarityCalculations.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (int i = 0; i < ActivityObjects1.size(); i++)
			{
				// bw.write("ActsFirst:,");
				bw.write(ActivityObjects1.get(i).getActivityName() + "_" + ActivityObjects1.get(i).getStartTimestamp().getHours() + ":"
						+ ActivityObjects1.get(i).getStartTimestamp().getMinutes() + ":"
						+ ActivityObjects1.get(i).getStartTimestamp().getSeconds() + "_" + +ActivityObjects1.get(i).getDurationInSeconds()
						+ ",");
			}
			bw.newLine();
			
			for (int i = 0; i < ActivityObjects2.size(); i++)
			{
				// bw.write("ActsSecond:,");
				bw.write(ActivityObjects2.get(i).getActivityName() + "_" + ActivityObjects2.get(i).getStartTimestamp().getHours() + ":"
						+ ActivityObjects2.get(i).getStartTimestamp().getMinutes() + ":"
						+ ActivityObjects2.get(i).getStartTimestamp().getSeconds() + "_" + +ActivityObjects2.get(i).getDurationInSeconds()
						+ ",");
			}
			bw.newLine();
			
			bw.write("Edit_Distance," + editDistance);
			bw.newLine();
			bw.newLine();
			
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeEditSimilarityCalculationsHeader()
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditSimilarityCalculations.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);// appends
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(
					"UserAtRecomm,DateAtRecomm,TimeAtRecomm,CandidateTimelineID,EditDistance,ActLevelDistance,FeatLevelDistance,Trace, ActivityObjects1,ActivityObjects2\n");
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeEditSimilarityCalculations(ArrayList<ActivityObject> ActivityObjects1,
			ArrayList<ActivityObject> ActivityObjects2, double editDistance, String trace, double dAct, double dFeat, String userAtRecomm,
			String dateAtRecomm, String timeAtRecomm, Long candidateTimelineId)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditSimilarityCalculations.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);// appends
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(userAtRecomm + "," + dateAtRecomm + "," + timeAtRecomm + "," + candidateTimelineId + "," + editDistance + "," + dAct
					+ "," + dFeat + "," + trace + ",");
			
			// String activityObjects1String = "", activityObjects2String = "";
			
			StringBuilder activityObjects1String = new StringBuilder();
			StringBuilder activityObjects2String = new StringBuilder();
			
			if (Constant.WriteActivityObjectsInEditSimilarityCalculations)
			{
				for (int i = 0; i < ActivityObjects1.size(); i++)
				{
					// bw.write("ActsFirst:,");
					// activityObjects1String = activityObjects1String +
					activityObjects1String.append(
							">>" + (ActivityObjects1.get(i).getActivityName() + "_" + ActivityObjects1.get(i).getStartTimestamp().getHours()
									+ ":" + ActivityObjects1.get(i).getStartTimestamp().getMinutes() + ":"
									+ ActivityObjects1.get(i).getStartTimestamp().getSeconds() + "_"
									+ +ActivityObjects1.get(i).getDurationInSeconds()));
				}
				
				for (int i = 0; i < ActivityObjects2.size(); i++)
				{
					// bw.write("ActsSecond:,");
					// activityObjects2String = activityObjects2String +
					activityObjects2String.append(
							">>" + (ActivityObjects2.get(i).getActivityName() + "_" + ActivityObjects2.get(i).getStartTimestamp().getHours()
									+ ":" + ActivityObjects2.get(i).getStartTimestamp().getMinutes() + ":"
									+ ActivityObjects2.get(i).getStartTimestamp().getSeconds() + "_"
									+ +ActivityObjects2.get(i).getDurationInSeconds()));
				}
			}
			
			bw.write(activityObjects1String.toString() + "," + activityObjects2String.toString());
			bw.newLine();
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeOnlyTrace(String trace)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "tracesEncountered.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(trace + "\n");
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Writes EditDistancesForAllEndPoints.csv with fields "Counter, UserID,CurrentTimeline,CandidateTimeline,EndPointIndex,EditDistance"
	 */
	public static void writeEditDistancesOfAllEndPointsHeader()
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistancesForAllEndPoints.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("Counter, UserID,CurrentTimeline,CandidateTimeline,EndPointIndex,EditDistance");
			bw.newLine();
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeEditDistancesOfAllEndPoints(ArrayList<ActivityObject> activitiesGuidingRecomm, UserDayTimeline userDayTimeline,
			LinkedHashMap<Integer, Pair<String, Double>> distanceScoresForEachSubsequence)// String trace)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistancesForAllEndPoints.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			counterEditAllEndPoints++;
			for (Map.Entry<Integer, Pair<String, Double>> entry : distanceScoresForEachSubsequence.entrySet())
			{
				
				bw.write(counterEditAllEndPoints + "," + userDayTimeline.getUserID() + ","
						+ ActivityObject.getArrayListOfActivityObjectsAsString(activitiesGuidingRecomm) + ","
						+ userDayTimeline.getActivityObjectNamesInSequenceWithFeatures() + "," + entry.getKey() + ","
						+ entry.getValue().getSecond());
				bw.newLine();
			}
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeEndPoinIndexCheck24Oct(String currentAct, String cand, ArrayList<Integer> arr1, ArrayList<Integer> arr2)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EndPoinIndexCheck24Oct.csv";
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			boolean isEqual = true;
			
			if (arr1.size() != arr2.size())
				isEqual = false;
			
			if (isEqual)
			{
				isEqual = arr1.equals(arr2.toString());
			}
			
			int isEqualI;
			
			if (isEqual)
				isEqualI = 1;
			else
				isEqualI = 0;
			bw.write(arr1.toString() + "," + arr2.toString() + "," + isEqualI + "\n");
			bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeEditDistance(double editDistance)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistance.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("Edit_Distance," + editDistance);
			bw.newLine();
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// /**
	// * Writes EditDistancePerRtPerCand.csv
	// * @param getDistanceScoresSorted
	// */
	// public static void writeDistanceScoresSorted(LinkedHashMap<Date, Double> getDistanceScoresSorted)
	// {
	// commonPath = Constant.getCommonPath();//
	// try
	// {
	// String fileName = commonPath + "EditDistancePerRtPerCand.csv";
	//
	// FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
	// BufferedWriter bw = new BufferedWriter(fw);
	//
	// for (Map.Entry<Date, Double> entry : getDistanceScoresSorted.entrySet())
	// {
	// bw.write(entry.getValue() + "," + entry.getKey());
	// bw.newLine();
	// }
	// bw.close();
	//
	// }
	// catch (Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	
	/**
	 * Just writing to file EditDistancePerRtPerCand.csv using data from distanceScoresSortedMap
	 * 
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param getDistanceScoresSorted
	 * @param candidateTimelines
	 * @param topNames
	 * @param currentTimeline
	 */
	public static void writeDistanceScoresSortedMap(String userAtRecomm, Date dateAtRecomm, Time timeAtRecomm,
			LinkedHashMap<Date, Triple<Integer, String, Double>> getDistanceScoresSorted,
			LinkedHashMap<Date, UserDayTimeline> candidateTimelines, LinkedHashMap<Date, String> topNames,
			ArrayList<ActivityObject> currentTimeline)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistancePerRtPerCand.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Date, Triple<Integer, String, Double>> entry : getDistanceScoresSorted.entrySet())
			{
				int countOfL1Ops = UtilityBelt.getCountOfLevel1Ops(entry.getValue().getSecond());
				int countOfL2Ops = UtilityBelt.getCountOfLevel2Ops(entry.getValue().getSecond());
				
				bw.write(userAtRecomm + "," + dateAtRecomm.toString() + "," + timeAtRecomm.toString() + "," + entry.getKey().toString()
						+ "," + entry.getValue().getFirst() + "," + entry.getValue().getSecond() + "," + entry.getValue().getThird() + ","
						+ countOfL1Ops + "," + countOfL2Ops + "," + topNames.get(entry.getKey()) + ","
						+ candidateTimelines.get(entry.getKey()).getActivityObjectNamesInSequenceWithFeatures() + "," + ","
						+ getStringActivityObjArray(currentTimeline));
				bw.newLine();
			}
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// public static void writeDistanceScoresSortedMapMUBroken(String userAtRecomm, Date dateAtRecomm, Time
	// timeAtRecomm, LinkedHashMap<Integer, Pair<String,
	// Double>> getDistanceScoresSorted,
	// LinkedHashMap<Integer, TimelineWithNext> candidateTimelines, ArrayList<Triple<ActivityObject, Double, Integer>>
	// topNextActivityObjects,
	// ArrayList<ActivityObject> currentTimeline,
	// boolean writeCandidateTimeline)
	// {
	// commonPath = Constant.getCommonPath();//
	// try
	// {
	// String fileName = commonPath + userAtRecomm + "EditDistancePerRtPerCandBroken.csv";
	//
	// FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile());// , true);
	// BufferedWriter bw = new BufferedWriter(fw);
	//
	// boolean writefull = true;
	//
	// for (Map.Entry<Integer, Pair<String, Double>> entry : getDistanceScoresSorted.entrySet())
	// {
	// int candTimelineID = entry.getKey();
	// String editOps = entry.getValue().getFirst();
	// double editDist = entry.getValue().getSecond();
	//
	// int countOfL1Ops = UtilityBelt.getCountOfLevel1Ops(editOps);// entry.getValue().getFirst());
	// int countOfL2Ops = UtilityBelt.getCountOfLevel2Ops(editOps);// entry.getValue().getFirst());
	//
	// String topAOName = "null";
	// for (Triple<ActivityObject, Double, Integer> t : topNextActivityObjects) // topNextActivityObjects should be
	// converted to hashmap for faster access.
	// {
	// if (t.getThird() == candTimelineID)
	// {
	// topAOName = t.getFirst().getActivityName();
	// break;
	// }
	// }
	//
	// String candidateTimelineAsString;
	// if (writeCandidateTimeline)
	// {
	// candidateTimelineAsString =
	// candidateTimelines.get(candTimelineID).getActivityObjectNamesWithTimestampsInSequence();
	// }
	// else
	// {
	// candidateTimelineAsString = "";
	// }
	//
	// if (writefull)
	// {
	// bw.write(userAtRecomm + "," + dateAtRecomm.toString() + "," + timeAtRecomm.toString() + "," + candTimelineID +
	// "," + " " + "," + editOps + "," + editDist
	// + "," + countOfL1Ops + ","
	// + countOfL2Ops + "," + topAOName + "," + candidateTimelineAsString + "," + "," +
	// getStringActivityObjArray(currentTimeline));
	// writefull = false;
	// }
	// else
	// // no need to write same repeating things everytime
	// {
	// bw.write("',','," + candTimelineID + "," + " " + "," + editOps + "," + editDist + "," + countOfL1Ops + "," +
	// countOfL2Ops + "," + topAOName + "," +
	// candidateTimelineAsString + "," + ",'");
	// }
	// bw.newLine();
	// }
	// bw.close();
	//
	// }
	// catch (Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	
	/**
	 * Writes the file EditDistancePerRtPerCand.csv
	 * 
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param getDistanceScoresSorted
	 * @param candidateTimelines
	 * @param topNextActivityObjects
	 * @param currentTimeline
	 * @param writeCandidateTimeline
	 */
	public static void writeDistanceScoresSortedMapMU(String userAtRecomm, Date dateAtRecomm, Time timeAtRecomm,
			LinkedHashMap<Integer, Pair<String, Double>> getDistanceScoresSorted,
			LinkedHashMap<Integer, TimelineWithNext> candidateTimelines,
			ArrayList<Triple<ActivityObject, Double, Integer>> topNextActivityObjects, ArrayList<ActivityObject> currentTimeline,
			boolean writeCandidateTimeline, boolean writeEditOperations)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistancePerRtPerCand.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			boolean writefull = true;
			
			for (Map.Entry<Integer, Pair<String, Double>> entry : getDistanceScoresSorted.entrySet())
			{
				int candTimelineID = entry.getKey();
				String editOps = entry.getValue().getFirst();
				double editDist = entry.getValue().getSecond();
				
				int countOfL1Ops = UtilityBelt.getCountOfLevel1Ops(editOps);// entry.getValue().getFirst());
				int countOfL2Ops = UtilityBelt.getCountOfLevel2Ops(editOps);// entry.getValue().getFirst());
				
				String topNextAOName = "null";
				
				for (Triple<ActivityObject, Double, Integer> t : topNextActivityObjects) // topNextActivityObjects
																							// should be converted to
																							// hashmap for faster
																							// access.
				{
					if (t.getThird() == candTimelineID)
					{
						topNextAOName = t.getFirst().getActivityName();
						break;
					}
				}
				
				String candidateTimelineAsString = " ";
				String editOperationsString = " ";
				
				if (writeCandidateTimeline)
				{
					candidateTimelineAsString = candidateTimelines.get(candTimelineID).getActivityObjectNamesWithTimestampsInSequence();
				}
				
				if (writeEditOperations)
				{
					editOperationsString = editOps;
				}
				
				String userString = "'", dateString = "'", timeString = "'", currentTimelineString = "";
				
				/*
				 * "UserAtRecomm,DateAtRecomm,TimeAtRecomm, Candidate ID, End point index of cand, Edit operations trace of cand, Edit Distance of Candidate, #Level_1_EditOps, #ObjectsInSameOrder"
				 * + ",NextActivityForRecomm,CandidateTimeline,CurrentTimeline"
				 */
				if (writefull || Constant.WriteRedundant)
				{
					userString = userAtRecomm;
					dateString = dateAtRecomm.toString();
					timeString = timeAtRecomm.toString();
					currentTimelineString = getStringActivityObjArray(currentTimeline); // current timeline is same
																						// throughout an execution of
																						// this method.
					
					writefull = false;
				}
				
				bw.write(userString + "," + dateString + "," + timeString + "," + candTimelineID + "," + " " + "," + editOperationsString
						+ "," + editDist + "," + countOfL1Ops + "," + countOfL2Ops + "," + topNextAOName + "," + candidateTimelineAsString
						+ "," + currentTimelineString);
				// else
				// // no need to write same repeating things everytime
				// {
				// bw.write("',','," + candTimelineID + "," + " " + "," + editOperationsString + "," + editDist + "," +
				// countOfL1Ops + "," + countOfL2Ops + ","
				// + topNextAOName + ","
				// + candidateTimelineAsString + "," + ",'");
				// }
				bw.newLine();
			}
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static String getStringActivityObjArray(ArrayList<ActivityObject> array)
	{
		String s = "";
		
		for (ActivityObject ao : array)
		{
			s += ">>" + ao.getActivityName() + "--" + ao.getStartTimestamp() + "--" + ao.getDurationInSeconds();
		}
		return s;
	}
	
	/**
	 * Creates the file EditDistancePerRtPerCand.csv and write the header line
	 */
	public static void writeDistanceScoresSortedMapHeader()
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "EditDistancePerRtPerCand.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(
					"UserAtRecomm,DateAtRecomm,TimeAtRecomm, Candidate ID, End point index of cand, Edit operations trace of cand, Edit Distance of Candidate, #Level_1_EditOps, #ObjectsInSameOrder"
							+ ",NextActivityForRecomm,CandidateTimeline,CurrentTimeline");
			bw.newLine();
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeStartTimeDistancesSorted(LinkedHashMap<Date, Triple<Integer, ActivityObject, Double>> getDistanceScoresSorted)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			String fileName = commonPath + "StartTimeDistancePerRtPerCand.csv";
			
			FileWriter fw = new FileWriter(new File(fileName).getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (Map.Entry<Date, Triple<Integer, ActivityObject, Double>> entry : getDistanceScoresSorted.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue().getFirst() + "," + entry.getValue().getSecond() + ","
						+ entry.getValue().getThird());
				bw.newLine();
			}
			bw.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void writeNumOfDistinctValidActivitiesPerDayInGivenDayTimelines(String userName,
			LinkedHashMap<Date, UserDayTimeline> userTimelines, String timelinesPhrase)
	{
		commonPath = Constant.getCommonPath();//
		StringBuilder toWrite = new StringBuilder();
		
		try
		{
			String fileName = commonPath + userName + "CountDistinctValidIn" + timelinesPhrase + ".csv";
			
			System.out.println("writing " + userName + "CountDistinctValidIn" + timelinesPhrase + ".csv");
			
			File file = new File(fileName);
			file.delete();
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("Date, Num_of_Distict_Valid_Activities");
			bw.newLine();
			
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				int numOfDistinctValidActivities = entry.getValue().countNumberOfValidDistinctActivities();
				toWrite.append(entry.getKey() + "," + numOfDistinctValidActivities + "\n");
				// bw.write(entry.getKey() + "," + numOfDistinctValidActivities);
				// bw.newLine();
			}
			bw.write(toWrite.toString());
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		
	}
	
	/**
	 * Write all the given day timelines.
	 * 
	 * @param usersDayTimelines
	 * @param timelinesPhrase
	 * @param writeStartEndGeocoordinates
	 * @param writeDistanceTravelled
	 * @param writeAvgAltitude
	 */
	public static void writeUsersDayTimelines(LinkedHashMap<String, LinkedHashMap<Date, UserDayTimeline>> usersDayTimelines,
			String timelinesPhrase, boolean writeStartEndGeocoordinates, boolean writeDistanceTravelled, boolean writeAvgAltitude)
	{
		// System.out.println("Common path=" + commonPath);
		commonPath = Constant.getCommonPath();//
		System.out.println("Inside writeUsersDayTimelines(): num of users received = " + usersDayTimelines.size());
		System.out.println("Common path=" + commonPath);
		try
		{
			for (Map.Entry<String, LinkedHashMap<Date, UserDayTimeline>> entry : usersDayTimelines.entrySet())
			{
				writeGivenDayTimelines(entry.getKey(), entry.getValue(), timelinesPhrase, writeStartEndGeocoordinates,
						writeDistanceTravelled, writeAvgAltitude);
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		System.out.println("Exiting writeUsersDayTimelines()");
	}
	
	/**
	 * Write all the given day timelines.
	 * 
	 * @param usersDayTimelines
	 * @param timelinesPhrase
	 * @param writeStartEndGeocoordinates
	 * @param writeDistanceTravelled
	 * @param writeAvgAltitude
	 */
	public static void writeUsersDayTimelinesSameFile(LinkedHashMap<String, LinkedHashMap<Date, UserDayTimeline>> usersDayTimelines,
			String timelinesPhrase, boolean writeStartEndGeocoordinates, boolean writeDistanceTravelled, boolean writeAvgAltitude,
			String fileName)
	{
		// System.out.println("Common path=" + commonPath);
		commonPath = Constant.getCommonPath();//
		System.out.println("Inside writeUsersDayTimelinesSameFile(): num of users received = " + usersDayTimelines.size());
		System.out.println("Common path=" + commonPath);
		try
		{
			for (Map.Entry<String, LinkedHashMap<Date, UserDayTimeline>> entry : usersDayTimelines.entrySet())
			{
				writeGivenDayTimelinesSameFile2(entry.getKey(), entry.getValue(), timelinesPhrase, writeStartEndGeocoordinates,
						writeDistanceTravelled, writeAvgAltitude, fileName);
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		System.out.println("Exiting writeUsersDayTimelinesSameFile()");
	}
	
	public static void writeNumOfActsPerUsersDayTimelinesSameFile(
			LinkedHashMap<String, LinkedHashMap<Date, UserDayTimeline>> usersDayTimelines, String timelinesPhrase, String fileName)
	{
		// System.out.println("Common path=" + commonPath);
		commonPath = Constant.getCommonPath();//
		System.out.println("Inside writeNumOfActsPerUsersDayTimelinesSameFile(): num of users received = " + usersDayTimelines.size());
		System.out.println("Common path=" + commonPath);
		try
		{
			for (Map.Entry<String, LinkedHashMap<Date, UserDayTimeline>> entry : usersDayTimelines.entrySet())
			{
				writeNumOfActsInGivenDayTimelinesSameFile(entry.getKey(), entry.getValue(), timelinesPhrase, fileName);
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		System.out.println("Exiting writeNumOfActsPerUsersDayTimelinesSameFile()");
	}
	
	public static void writeNumOfDaysPerUsersDayTimelinesSameFile(
			LinkedHashMap<String, LinkedHashMap<Date, UserDayTimeline>> usersDayTimelines, String absFileName)
	{
		// System.out.println("Common path=" + commonPath);
		commonPath = Constant.getCommonPath();//
		System.out.println("Inside writeNumOfDaysPerUsersDayTimelinesSameFile(): num of users received = " + usersDayTimelines.size());
		System.out.println("Common path=" + commonPath);
		StringBuffer msg = new StringBuffer();
		msg.append("User,#Days\n");
		try
		{
			for (Map.Entry<String, LinkedHashMap<Date, UserDayTimeline>> entry : usersDayTimelines.entrySet())
			{
				msg.append(entry.getKey() + "," + entry.getValue().size() + "\n");
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		
		WritingToFile.writeToNewFile(msg.toString(), absFileName);
		System.out.println("Exiting writeNumOfDaysPerUsersDayTimelinesSameFile()");
	}
	
	/**
	 * Write all day timelines for a given user
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param timelinesPhrase
	 * @param writeStartEndGeoCoordinates
	 * @param writeDistanceTravelled
	 * @param writeAvgAltitude
	 */
	public static void writeGivenDayTimelines(String userName, LinkedHashMap<Date, UserDayTimeline> userTimelines, String timelinesPhrase,
			boolean writeStartEndGeoCoordinates, boolean writeDistanceTravelled, boolean writeAvgAltitude)
	{
		commonPath = Constant.getCommonPath();//
		
		try
		{
			StringBuilder toWrite = new StringBuilder();
			String fileName = commonPath + userName + "DayTimelines" + timelinesPhrase + ".csv";
			// PopUps.showMessage("Writing day timelines to" + fileName);
			System.out.println("writing " + userName + "DayTimelines" + timelinesPhrase + ".csv");
			
			// File file = new File(fileName);
			// file.delete();
			//
			// FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			// BufferedWriter bw = new BufferedWriter(fw);
			
			// bw.write
			toWrite.append("Date, DayTimeline\n");
			// bw.newLine();
			
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				
				// bw.write
				toWrite.append(entry.getKey() + ",");
				ArrayList<ActivityObject> ActivityObjects = entry.getValue().getActivityObjectsInDay();
				
				// if(!writeStartEndGeoCoordinates && !distanceTravelled && !)
				for (int i = 0; i < ActivityObjects.size(); i++)
				{
					// bw.write
					toWrite.append(ActivityObjects.get(i).getActivityName() + "__" + ActivityObjects.get(i).getStartTimestamp().getHours()
							+ ":" + ActivityObjects.get(i).getStartTimestamp().getMinutes() + ":"
							+ ActivityObjects.get(i).getStartTimestamp().getSeconds() + "_to_"
							+ ActivityObjects.get(i).getEndTimestamp().getHours() + ":"
							+ ActivityObjects.get(i).getEndTimestamp().getMinutes() + ":"
							+ ActivityObjects.get(i).getEndTimestamp().getSeconds());// + ",");
					
					if (Constant.getDatabaseName().equals("geolife1"))
					{
						if (writeStartEndGeoCoordinates)
						{
							// bw.write
							toWrite.append("__(" + ActivityObjects.get(i).getStartLatitude() + "-"
									+ ActivityObjects.get(i).getStartLongitude() + ") to (" + ActivityObjects.get(i).getEndLatitude() + "-"
									+ ActivityObjects.get(i).getEndLongitude() + ")");
						}
						if (writeDistanceTravelled)
						{
							// bw.write
							toWrite.append("__" + ActivityObjects.get(i).getDistanceTravelled());
						}
						if (writeAvgAltitude)
						{
							// bw.write
							toWrite.append("__" + ActivityObjects.get(i).getAvgAltitude());
						}
					}
					
					// bw.write
					toWrite.append(",");
				}
				// bw.newLine();
				// bw.write
				toWrite.append("\n");
			}
			WritingToFile.writeToNewFile(toWrite.toString(), fileName);
			// bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
	}
	
	/**
	 * Write all day timelines for a given user
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param timelinesPhrase
	 * @param writeStartEndGeoCoordinates
	 * @param writeDistanceTravelled
	 * @param writeAvgAltitude
	 */
	public static void writeGivenDayTimelinesSameFile(String userName, LinkedHashMap<Date, UserDayTimeline> userTimelines,
			String timelinesPhrase, boolean writeStartEndGeoCoordinates, boolean writeDistanceTravelled, boolean writeAvgAltitude,
			String fileName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			fileName = commonPath + fileName;// userName + "DayTimelines" + timelinesPhrase + ".csv";
			// PopUps.showMessage("Writing day timelines to" + fileName);
			// System.out.println("writing " + userName + "DayTimelines" + timelinesPhrase + ".csv");
			//
			// File file = new File(fileName);
			// file.delete();
			//
			// FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			// BufferedWriter bw = new BufferedWriter(fw);
			//
			// bw.write("Date, DayTimeline");
			// bw.newLine();
			
			String toWrite = "";
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				
				toWrite += (userName + "," + entry.getKey() + ",");
				ArrayList<ActivityObject> ActivityObjects = entry.getValue().getActivityObjectsInDay();
				
				// if(!writeStartEndGeoCoordinates && !distanceTravelled && !)
				for (int i = 0; i < ActivityObjects.size(); i++)
				{
					toWrite += (ActivityObjects.get(i).getActivityName() + "__" + ActivityObjects.get(i).getStartTimestamp().getHours()
							+ ":" + ActivityObjects.get(i).getStartTimestamp().getMinutes() + ":"
							+ ActivityObjects.get(i).getStartTimestamp().getSeconds() + "_to_"
							+ ActivityObjects.get(i).getEndTimestamp().getHours() + ":"
							+ ActivityObjects.get(i).getEndTimestamp().getMinutes() + ":"
							+ ActivityObjects.get(i).getEndTimestamp().getSeconds());// + ",");
					
					if (Constant.getDatabaseName().equals("geolife1"))
					{
						if (writeStartEndGeoCoordinates)
						{
							toWrite += ("__(" + ActivityObjects.get(i).getStartLatitude() + "-" + ActivityObjects.get(i).getStartLongitude()
									+ ") to (" + ActivityObjects.get(i).getEndLatitude() + "-" + ActivityObjects.get(i).getEndLongitude()
									+ ")");
						}
						if (writeDistanceTravelled)
						{
							toWrite += ("__" + ActivityObjects.get(i).getDistanceTravelled());
						}
						if (writeAvgAltitude)
						{
							toWrite += ("__" + ActivityObjects.get(i).getAvgAltitude());
						}
					}
					
					toWrite += (",");
				}
				toWrite += ("\n");
			}
			
			// bw.close();
			WritingToFile.appendLineToFileAbsolute(toWrite, fileName);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
	}
	
	/**
	 * Write all day timelines for a given user
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param timelinesPhrase
	 * @param writeStartEndGeoCoordinates
	 * @param writeDistanceTravelled
	 * @param writeAvgAltitude
	 */
	public static void writeGivenDayTimelinesSameFile2(String userName, LinkedHashMap<Date, UserDayTimeline> userTimelines,
			String timelinesPhrase, boolean writeStartEndGeoCoordinates, boolean writeDistanceTravelled, boolean writeAvgAltitude,
			String fileName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			fileName = commonPath + fileName;
			StringBuffer toWrite = new StringBuffer();
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				toWrite.append(userName + "," + entry.getKey() + ",");
				ArrayList<ActivityObject> ActivityObjects = entry.getValue().getActivityObjectsInDay();
				
				for (int i = 0; i < ActivityObjects.size(); i++)
				{
					if (Constant.getDatabaseName().equals("gowalla1"))
					{
						toWrite.append(ActivityObjects.get(i).toStringAllGowalla());
					}
					else
					{
						toWrite.append(
								ActivityObjects.get(i).getActivityName() + "__" + ActivityObjects.get(i).getStartTimestamp().getHours()
										+ ":" + ActivityObjects.get(i).getStartTimestamp().getMinutes() + ":"
										+ ActivityObjects.get(i).getStartTimestamp().getSeconds() + "_to_"
										+ ActivityObjects.get(i).getEndTimestamp().getHours() + ":"
										+ ActivityObjects.get(i).getEndTimestamp().getMinutes() + ":"
										+ ActivityObjects.get(i).getEndTimestamp().getSeconds());// + ",");
						
						if (Constant.getDatabaseName().equals("geolife1"))
						{
							if (writeStartEndGeoCoordinates)
							{
								toWrite.append("__(" + ActivityObjects.get(i).getStartLatitude() + "-"
										+ ActivityObjects.get(i).getStartLongitude() + ") to (" + ActivityObjects.get(i).getEndLatitude()
										+ "-" + ActivityObjects.get(i).getEndLongitude() + ")");
							}
							if (writeDistanceTravelled)
							{
								toWrite.append("__" + ActivityObjects.get(i).getDistanceTravelled());
							}
							if (writeAvgAltitude)
							{
								toWrite.append("__" + ActivityObjects.get(i).getAvgAltitude());
							}
						}
					}
					toWrite.append(",");
				}
				toWrite.append("\n");
			}
			WritingToFile.appendLineToFileAbsolute(toWrite.toString(), fileName);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
	}
	
	public static void writeNumOfActsInGivenDayTimelinesSameFile(String userName, LinkedHashMap<Date, UserDayTimeline> userTimelines,
			String timelinesPhrase, String fileName)
	{
		commonPath = Constant.getCommonPath();//
		try
		{
			fileName = commonPath + fileName;
			StringBuffer toWrite = new StringBuffer();
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				toWrite.append(userName + "," + entry.getKey() + "," + entry.getValue().getActivityObjectsInDay().size());
				toWrite.append("\n");
			}
			WritingToFile.appendLineToFileAbsolute(toWrite.toString(), fileName);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
	}
	
	/**
	 * Sums the duration in seconds of activities for each of the days of given day timelines and writes it to a file and sums the duration activities over all days of given
	 * timelines and return it as a LinkedHashMap
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param fileNamePhrase
	 * @return duration of activities over all days of given timelines
	 */
	public static LinkedHashMap<String, Long> writeActivityDurationInGivenDayTimelines(String userName,
			LinkedHashMap<Date, UserDayTimeline> userTimelines, String fileNamePhrase)
	{
		commonPath = Constant.getCommonPath();//
		String[] activityNames = Constant.getActivityNames();// activityNames;
		LinkedHashMap<String, Long> activityNameDurationPairsOverAllDayTimelines = new LinkedHashMap<String, Long>();
		// count over all the days
		
		try
		{
			// String userName=entryForUser.getKey();
			// System.out.println("\nUser ="+entryForUser.getKey());
			String fileName = commonPath + userName + "ActivityDuration" + fileNamePhrase + ".csv";
			
			if (Constant.verbose)
			{
				System.out.println("writing " + userName + "ActivityDuration" + fileNamePhrase + ".csv");
			}
			
			StringBuilder toWrite = new StringBuilder();
			
			toWrite.append(",");
			// bw.write(",");
			
			for (String activityName : activityNames)
			{
				if (UtilityBelt.isValidActivityName(activityName) == false)
				// (activityName.equals("Unknown")|| activityName.equals("Others"))
				{
					continue;
				}
				toWrite.append("," + activityName);
				// bw.write("," + activityName);
				activityNameDurationPairsOverAllDayTimelines.put(activityName, new Long(0));
			}
			toWrite.append("\n");
			// bw.newLine();
			
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				// System.out.println("Date =" + entry.getKey());
				// bw.write(entry.getKey().toString());
				// bw.write("," + (DateTimeUtils.getWeekDayFromWeekDayInt(entry.getKey().getDay())));
				
				toWrite.append(entry.getKey().toString() + "," + (DateTimeUtils.getWeekDayFromWeekDayInt(entry.getKey().getDay())));
				
				ArrayList<ActivityObject> activitiesInDay = entry.getValue().getActivityObjectsInDay();
				LinkedHashMap<String, Long> activityNameDurationPairs = new LinkedHashMap<String, Long>();
				
				for (String activityName : activityNames) // written beforehand to maintain the same order of activity
															// names
				{
					if (UtilityBelt.isValidActivityName(activityName))
					// if((activityName.equalsIgnoreCase("Others")||activityName.equalsIgnoreCase("Unknown"))==false)
					{
						activityNameDurationPairs.put(activityName, new Long(0));
					}
				}
				
				for (ActivityObject actEvent : activitiesInDay)
				{
					if (UtilityBelt.isValidActivityName(actEvent.getActivityName()))
					// if((actEvent.getActivityName().equalsIgnoreCase("Unknown") ||
					// actEvent.getActivityName().equalsIgnoreCase("Others") ) ==false)
					{
						Long durationInSecondsForActivity = actEvent.getDurationInSeconds();
						// summing of duration for current day
						activityNameDurationPairs.put(actEvent.getActivityName(),
								activityNameDurationPairs.get(actEvent.getActivityName()) + durationInSecondsForActivity);
						
						// accumulative duration over all days
						activityNameDurationPairsOverAllDayTimelines.put(actEvent.getActivityName(),
								activityNameDurationPairsOverAllDayTimelines.get(actEvent.getActivityName())
										+ durationInSecondsForActivity);
					}
				}
				
				// write the activityNameDurationPairs to the file
				for (Map.Entry<String, Long> entryWrite : activityNameDurationPairs.entrySet())
				{
					// bw.write("," + entryWrite.getValue());
					toWrite.append("," + entryWrite.getValue());
				}
				toWrite.append("\n");
				// bw.newLine();
			}
			// File file = new File(fileName);
			// file.delete();
			// FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			// BufferedWriter bw = new BufferedWriter(fw);
			WritingToFile.writeToNewFile(toWrite.toString(), fileName);
			// bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		
		writeSimpleLinkedHashMapToFileAppend(activityNameDurationPairsOverAllDayTimelines,
				"ActivityNameDurationPairsOver" + fileNamePhrase + ".csv", "Activity", "Duration");
		// TODO check if it
		// indeed should be
		// an append
		
		return activityNameDurationPairsOverAllDayTimelines;
		
	}
	
	// ///////////////////
	/**
	 * Counts activities for each of the days of given day timelines and writes it to a file and counts activities over all days of given timelines and return it as a LinkedHashMap
	 * (fileName = commonPath + userName + "ActivityCounts" + fileNamePhrase + ".csv")
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param fileNamePhrase
	 * @return count of activities over all days of given timelines
	 */
	public static LinkedHashMap<String, Long> writeActivityCountsInGivenDayTimelines(String userName,
			LinkedHashMap<Date, UserDayTimeline> userTimelines, String fileNamePhrase)
	{
		commonPath = Constant.getCommonPath();//
		
		if (Constant.verbose)
			System.out.println("Inside writeActivityCountsInGivenDayTimelines");
		
		/* <Activity Name, count over all days> */
		LinkedHashMap<String, Long> activityNameCountPairsOverAllDayTimelines = new LinkedHashMap<String, Long>(); // count over all the days
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		try
		{
			// String userName=entryForUser.getKey();
			// System.out.println("\nUser ="+entryForUser.getKey());
			String fileName = commonPath + userName + "ActivityCounts" + fileNamePhrase + ".csv";
			
			if (Constant.verbose)
			{
				System.out.println("writing " + userName + "ActivityCounts" + fileNamePhrase + ".csv");
			}
			// BufferedWriter bw = WritingToFile.getBufferedWriterForNewFile(fileName);// new BufferedWriter(fw);
			
			StringBuilder bwString = new StringBuilder();
			bwString.append(",");
			// bw.write(",");
			
			for (String activityName : activityNames)
			{
				if (UtilityBelt.isValidActivityName(activityName) == false) // if(activityName.equals("Unknown")|| activityName.equals("Not Available"))
				{
					continue;
				}
				// bw.write("," + activityName);
				bwString.append("," + activityName);
				activityNameCountPairsOverAllDayTimelines.put(activityName, new Long(0));
			}
			// bw.newLine();
			bwString.append("\n");
			
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				// System.out.println("Date =" + entry.getKey());
				// bw.write(entry.getKey().toString());
				// bw.write("," + (DateTimeUtils.getWeekDayFromWeekDayInt(entry.getKey().getDay())));
				
				bwString.append(entry.getKey().toString());
				bwString.append("," + (DateTimeUtils.getWeekDayFromWeekDayInt(entry.getKey().getDay())));
				
				ArrayList<ActivityObject> activitiesInDay = entry.getValue().getActivityObjectsInDay();
				
				/* <Activity Name, count for the current day> */
				LinkedHashMap<String, Integer> activityNameCountPairs = new LinkedHashMap<String, Integer>();
				
				// written beforehand to maintain the same order of activity names
				for (String activityName : activityNames)
				{
					if (UtilityBelt.isValidActivityName(activityName))
					// if((activityName.equalsIgnoreCase("Not Available")||activityName.equalsIgnoreCase("Unknown"))==false)
					{
						// System.out.println(" putting down -" + activityName + "- in activityNameCountPairs");
						activityNameCountPairs.put(activityName, 0);
					}
				}
				
				for (ActivityObject actEvent : activitiesInDay)
				{
					if (UtilityBelt.isValidActivityName(actEvent.getActivityName()))
					// if((actEvent.getActivityName().equalsIgnoreCase("Unknown") || actEvent.getActivityName().equalsIgnoreCase("Not Available") ) ==false)
					{
						String actName = actEvent.getActivityName();
						// System.out.println(activityNameCountPairs.size());
						
						// Integer val;
						// if (activityNameCountPairs.get(actName) == null)
						// {
						// val = 0;
						// }
						// else
						// {
						// val = activityNameCountPairs.get(actName);
						// }
						Integer val = activityNameCountPairs.get(actName);
						if (val == null)
						{
							new Exception(
									"Exception in org.activity.io.WritingToFile.writeActivityCountsInGivenDayTimelines(String, LinkedHashMap<Date, UserDayTimeline>, String) : actName = "
											+ actName + " has null val");// System.out.println("actName = " + actName);
						}
						
						// System.out.println("val:" + val);
						Integer newVal = new Integer(val.intValue() + 1);
						// count for current day
						activityNameCountPairs.put(actName, newVal);
						
						// accumulative count over all days
						activityNameCountPairsOverAllDayTimelines.put(actEvent.getActivityName(),
								activityNameCountPairsOverAllDayTimelines.get(actEvent.getActivityName()) + 1);
					}
				}
				
				// write the activityNameCountPairs to the file
				for (Map.Entry<String, Integer> entryWrite : activityNameCountPairs.entrySet())
				{
					// bw.write("," + entryWrite.getValue());
					bwString.append("," + entryWrite.getValue());
				}
				
				bwString.append("\n");
				// bw.newLine();
			}
			WritingToFile.writeToNewFile(bwString.toString(), fileName);
			// bw.write(bwString.toString());
			// bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		
		writeSimpleLinkedHashMapToFileAppend(activityNameCountPairsOverAllDayTimelines,
				"ActivityNameCountPairsOver" + fileNamePhrase + ".csv", "Activity", "Count"); // TODO check if it indeed should be an append
		
		if (Constant.verbose)
			System.out.println("Exiting writeActivityCountsInGivenDayTimelines");
		
		return activityNameCountPairsOverAllDayTimelines;
		
	}
	
	// ///////////////////
	/**
	 * percentage of timelines in which the activity occurrs and counts activities over all days of given timelines and return it as a LinkedHashMap
	 * 
	 * @param userName
	 * @param userTimelines
	 * @param fileNamePhrase
	 * @return count of activities over all days of given timelines
	 */
	public static LinkedHashMap<String, Double> writeActivityOccPercentageOfTimelines(String userName,
			LinkedHashMap<Date, UserDayTimeline> userTimelines, String fileNamePhrase)
	{
		commonPath = Constant.getCommonPath();//
		LinkedHashMap<String, Double> activityNameCountPairsOverAllDayTimelines = new LinkedHashMap<String, Double>();
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		try
		{
			// String userName=entryForUser.getKey();
			// System.out.println("\nUser ="+entryForUser.getKey());
			String fileName = commonPath + userName + "ActivityOccPerTimelines" + fileNamePhrase + ".csv";
			
			if (Constant.verbose)
			{
				System.out.println("writing " + userName + "ActivityOccPerTimelines" + fileNamePhrase + ".csv");
			}
			
			StringBuilder toWrite = new StringBuilder();
			// bw.write(",");
			
			for (String activityName : activityNames)
			{
				if (UtilityBelt.isValidActivityName(activityName) == false)
				// if(activityName.equals("Unknown")|| activityName.equals("Not Available"))
				{
					continue;
				}
				// bw.write("," + activityName);
				toWrite.append("," + activityName);
				
				activityNameCountPairsOverAllDayTimelines.put(activityName, new Double(0));
			}
			// bw.newLine();
			toWrite.append("\n");
			
			double numOfTimelines = userTimelines.size();
			
			for (String activityName : activityNames) // written beforehand to maintain the same order of activity names
			{
				if (UtilityBelt.isValidActivityName(activityName))
				// if((activityName.equalsIgnoreCase("Not Available")||activityName.equalsIgnoreCase("Unknown"))==false)
				{
					activityNameCountPairsOverAllDayTimelines.put(activityName, new Double(0));
				}
			}
			
			for (Map.Entry<Date, UserDayTimeline> entry : userTimelines.entrySet())
			{
				// System.out.println("Date =" + entry.getKey());
				// bw.write(entry.getKey().toString());
				// bw.write("," + (UtilityBelt.getWeekDayFromWeekDayInt(entry.getKey().getDay())));
				
				ArrayList<ActivityObject> activitiesInDay = entry.getValue().getActivityObjectsInDay();
				
				for (String activityName : activityNames) // written beforehand to maintain the same order of activity
															// names
				{
					if (UtilityBelt.isValidActivityName(activityName))
					// if((activityName.equalsIgnoreCase("Not Available")||activityName.equalsIgnoreCase("Unknown"))==false)
					{
						if (entry.getValue().hasActivityName(activityName) == true)
						{
							activityNameCountPairsOverAllDayTimelines.put(activityName,
									activityNameCountPairsOverAllDayTimelines.get(activityName) + 1);
						}
					}
				}
			}
			
			// write the activityNameCountPairs to the file
			for (Map.Entry<String, Double> entryWrite : activityNameCountPairsOverAllDayTimelines.entrySet())
			{
				String actName = entryWrite.getKey();
				Double val = entryWrite.getValue();
				double percentageOccurrenceOverTimeline =
						((double) activityNameCountPairsOverAllDayTimelines.get(actName) / (double) numOfTimelines) * 100;
				activityNameCountPairsOverAllDayTimelines.put(actName, percentageOccurrenceOverTimeline);
				// bw.write("," + percentageOccurrenceOverTimeline);
				toWrite.append("," + percentageOccurrenceOverTimeline);
			}
			// bw.newLine();
			toWrite.append("\n");
			
			// File file = new File(fileName);
			// file.delete();
			// FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			// BufferedWriter bw = new BufferedWriter(fw);
			WritingToFile.writeToNewFile(toWrite.toString(), fileName);
			// bw.close();
		}
		
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-5);
		}
		
		writeSimpleLinkedHashMapToFileAppend(activityNameCountPairsOverAllDayTimelines, "ActivityOccPerTimelines" + fileNamePhrase + ".csv",
				"Activity", "Count");// TODO check if it indeed should be an append
		
		return activityNameCountPairsOverAllDayTimelines;
		
	}
	
	/*
	 * This method is called from DCU_DataLoader
	 */
	public static int writeActivityDistributionOcurrence(LinkedHashMap<String, TreeMap<Timestamp, String>> allData) // OUTOUT
																													// VALIDATED
																													// WITH
																													// SQL
																													// OUTPUT
																													// OK
	{
		commonPath = Constant.getCommonPath();//
		// <User , <day-month-year, <activity name, count of occurence> >>
		LinkedHashMap<String, TreeMap<String, LinkedHashMap<String, Integer>>> dataToWrite =
				new LinkedHashMap<String, TreeMap<String, LinkedHashMap<String, Integer>>>();
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : allData.entrySet())
		{
			try
			{
				String userName = entryForUser.getKey();
				
				System.out.println("\nUser =" + entryForUser.getKey());
				
				// <day-month-year, <ActvityName, count of occurence>>
				TreeMap<String, LinkedHashMap<String, Integer>> mapForEachUser = new TreeMap<String, LinkedHashMap<String, Integer>>();
				
				int countOfDays = 0;
				
				for (Map.Entry<Timestamp, String> entry : entryForUser.getValue().entrySet())
				{
					// System.out.println(T)
					int date = entry.getKey().getDate();
					int month = entry.getKey().getMonth() + 1;
					int year = entry.getKey().getYear() + 1900;
					String day = date + "-" + month + "-" + year;
					
					if (mapForEachUser.containsKey(day) == false)
					{
						LinkedHashMap<String, Integer> activityNameValue = new LinkedHashMap<String, Integer>();
						
						for (String activityName : activityNames)
						{
							activityNameValue.put(activityName, new Integer(0));
						}
						
						mapForEachUser.put(day, activityNameValue);
						countOfDays++;
					}
					
					String activityNameInEntry = GeolifeDataLoader.getActivityNameFromDataEntry(entry.getValue());
					
					Integer currentCountForActivityInDay = mapForEachUser.get(day).get(activityNameInEntry);
					
					mapForEachUser.get(day).put(activityNameInEntry, currentCountForActivityInDay + 1);
					
					System.out.println(entry.getKey() + "," + entry.getValue());// +" "+ day);
				}
				
				System.out.println("count of days=" + countOfDays);
				
				dataToWrite.put(userName, mapForEachUser);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
		// //////////// LinkedHashMap<String, TreeMap<String,LinkedHashMap<String,Integer>> > dataToWrite
		
		for (Map.Entry<String, TreeMap<String, LinkedHashMap<String, Integer>>> entryForUser : dataToWrite.entrySet())
		{
			try
			{
				
				String userName = entryForUser.getKey();
				
				System.out.println("\nUser =" + entryForUser.getKey());
				String fileName = commonPath + userName + "ActivityDistributionOcurrence.csv";
				
				File file = new File(fileName);
				
				file.delete();
				
				FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
				BufferedWriter bw = new BufferedWriter(fw);
				
				bw.write(",");
				for (String activityName : activityNames)
				{
					if (UtilityBelt.isValidActivityName(activityName) == false)
					// if(activityName.equals("Unknown")|| activityName.equals("Others"))
					{
						continue;
					}
					bw.write("," + activityName);
				}
				bw.newLine();
				
				for (Map.Entry<String, LinkedHashMap<String, Integer>> entry : entryForUser.getValue().entrySet())
				{
					if (hasNonZeroValidActivityNamesInteger(entry.getValue()))
					{
						System.out.println("Date =" + entry.getKey());
						bw.write(entry.getKey());
						bw.write("," + DateTimeUtils.getWeekDayFromDateString(entry.getKey()));
						
						for (Map.Entry<String, Integer> entryForAct : entry.getValue().entrySet())
						{
							String key = entryForAct.getKey();
							
							if (UtilityBelt.isValidActivityName(key) == false)
							// if(key.equals("Unknown")|| key.equals("Others"))
							{
								continue;
							}
							
							Integer value = entryForAct.getValue();
							System.out.println(" " + key + "=" + value);
							bw.write("," + value);
						}
						bw.newLine();
					}
				}
				
				bw.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
		return 0;
	}
	
	public static int writeActivityDistributionDuration(LinkedHashMap<String, TreeMap<Timestamp, String>> allData) // OUTOUT
																													// VALIDATED
																													// WITH
																													// SQL
																													// OUTPUT
																													// OK
	{
		commonPath = Constant.getCommonPath();//
		// <User , <day-month-year, <activity name, sum of duration in seconds> >>
		LinkedHashMap<String, TreeMap<String, LinkedHashMap<String, Long>>> dataToWrite =
				new LinkedHashMap<String, TreeMap<String, LinkedHashMap<String, Long>>>();
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : allData.entrySet())
		{
			try
			{
				String userName = entryForUser.getKey();
				
				System.out.println("\nUser =" + entryForUser.getKey());
				
				// <day-month-year, <ActvityName, count of occurence>>
				TreeMap<String, LinkedHashMap<String, Long>> mapForEachUser = new TreeMap<String, LinkedHashMap<String, Long>>();
				
				int countOfDays = 0;
				
				for (Map.Entry<Timestamp, String> entry : entryForUser.getValue().entrySet())
				{
					// System.out.println(T)
					int date = entry.getKey().getDate();
					int month = entry.getKey().getMonth() + 1;
					int year = entry.getKey().getYear() + 1900;
					String day = date + "-" + month + "-" + year;
					
					if (mapForEachUser.containsKey(day) == false)
					{
						LinkedHashMap<String, Long> activityNameValue = new LinkedHashMap<String, Long>();
						
						for (String activityName : activityNames)
						{
							activityNameValue.put(activityName, new Long(0));
						}
						
						mapForEachUser.put(day, activityNameValue);
						countOfDays++;
					}
					
					String activityNameInEntry = GeolifeDataLoader.getActivityNameFromDataEntry(entry.getValue());
					long activityDurationInEntry = GeolifeDataLoader.getDurationInSecondsFromDataEntry(entry.getValue());
					
					Long currentDurationForActivityInDay = mapForEachUser.get(day).get(activityNameInEntry);
					
					mapForEachUser.get(day).put(activityNameInEntry, currentDurationForActivityInDay + activityDurationInEntry);
					
					System.out.println(entry.getKey() + "," + entry.getValue());// +" "+ day);
				}
				
				System.out.println("count of days=" + countOfDays);
				
				dataToWrite.put(userName, mapForEachUser);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
		// //////////// LinkedHashMap<String, TreeMap<String,LinkedHashMap<String,Integer>> > dataToWrite
		
		for (Map.Entry<String, TreeMap<String, LinkedHashMap<String, Long>>> entryForUser : dataToWrite.entrySet())
		{
			try
			{
				String userName = entryForUser.getKey();
				
				System.out.println("\nUser =" + entryForUser.getKey());
				String fileName = commonPath + userName + "ActivityDistributionDuration.csv";
				
				File file = new File(fileName);
				
				file.delete();
				
				FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
				BufferedWriter bw = new BufferedWriter(fw);
				
				bw.write(",");
				for (String activityName : activityNames)
				{
					if (UtilityBelt.isValidActivityName(activityName) == false)
					// if(activityName.equals("Unknown")|| activityName.equals("Others"))
					{
						continue;
					}
					bw.write("," + activityName);
				}
				bw.newLine();
				
				for (Map.Entry<String, LinkedHashMap<String, Long>> entry : entryForUser.getValue().entrySet())
				{
					// System.out.println(T)
					if (hasNonZeroValidActivityNamesLong(entry.getValue()))
					{
						System.out.println("Date =" + entry.getKey());
						bw.write(entry.getKey());
						bw.write("," + DateTimeUtils.getWeekDayFromDateString(entry.getKey()));
						
						for (Map.Entry<String, Long> entryForAct : entry.getValue().entrySet())
						{
							String key = entryForAct.getKey();
							if (UtilityBelt.isValidActivityName(key) == false)
							// if(key.equals("Unknown")|| key.equals("Others"))
							{
								continue;
							}
							Long value = entryForAct.getValue();
							System.out.println(" " + key + "=" + value);
							bw.write("," + value);
						}
						bw.newLine();
					}
				}
				
				bw.close();
				
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
		return 0;
	}
	
	public static boolean hasNonZeroValidActivityNamesInteger(LinkedHashMap<String, Integer> map)
	{
		boolean hasNonZeroValid = false;
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		for (int i = 2; i < activityNames.length; i++)
		{
			if (map.get(activityNames[i]) > 1)
			{
				hasNonZeroValid = true;
				break;
			}
		}
		
		return hasNonZeroValid;
	}
	
	public static boolean hasNonZeroValidActivityNamesLong(LinkedHashMap<String, Long> map)
	{
		boolean hasNonZeroValid = false;
		String[] activityNames = Constant.getActivityNames();// .activityNames;
		for (int i = 2; i < activityNames.length; i++)
		{
			if (map.get(activityNames[i]) > 1)
			{
				hasNonZeroValid = true;
				break;
			}
		}
		
		return hasNonZeroValid;
	}
	
	/**
	 * Write a Map to file
	 * 
	 * @param map
	 * @param fileName
	 *            with fullPath
	 * @param headerKey
	 * @param headerValue
	 */
	public static void writeSimpleMapToFile(Map<String, Long> map, String fileName, String headerKey, String headerValue)
	{
		commonPath = Constant.getCommonPath();//
		if (map.size() == 0 || map == null)
		{
			new Exception("Alert! writeSimpleMapToFile, the passed map is empty or null");
		}
		try
		{
			File fileToWrite = new File(fileName);
			fileToWrite.delete();
			fileToWrite.createNewFile();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite));// ,true));
			
			bw.write(headerKey + "," + headerValue);
			bw.newLine();
			
			for (Map.Entry<String, Long> entry : map.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue());
				
				bw.newLine();
			}
			
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Write a LinkedHashMap to file
	 * 
	 * @param map
	 * @param absFileName
	 *            absolute filename i.e., with absolute address
	 * @param headerKey
	 * @param headerValue
	 */
	public static void writeSimpleLinkedHashMapToFile(LinkedHashMap<String, ?> map, String absFileName, String headerKey,
			String headerValue)
	{
		// commonPath = Constant.getCommonPath();//
		// System.out.println("Inside writeSimpleLinkedHashMapToFile" + " commonPath=" + commonPath);
		try
		{
			File fileToWrite = new File(absFileName);
			fileToWrite.delete();
			fileToWrite.createNewFile();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite));// ,true));
			
			bw.write(headerKey + "," + headerValue);
			bw.newLine();
			
			for (Map.Entry<String, ?> entry : map.entrySet())
			{
				bw.write(entry.getKey() + "," + entry.getValue().toString());
				// System.out.println(entry.getKey() + "," + entry.getValue());
				bw.newLine();
			}
			
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Write a LinkedHashMap to file modified for append
	 * 
	 * @param map
	 * @param fileName
	 * @param headerKey
	 * @param headerValue
	 */
	public static void writeSimpleLinkedHashMapToFileAppend(LinkedHashMap<String, ?> map, String fileName, String headerKey,
			String headerValue)
	{
		// commonPath = Constant.getCommonPath();//
		System.out.println("Debug Feb24:  inside writeSimpleLinkedHashMapToFileAppend: is correct order of activity names = "
				+ Constant.areActivityNamesInCorrectOrder(map));
		
		// Constant.areActivityNamesInCorrectOrder(map);
		
		StringBuilder toWrite = new StringBuilder();
		
		try
		{
			File fileToWrite = new File(fileName);
			// fileToWrite.delete();
			// fileToWrite.createNewFile();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite, true));// ,true));
			
			// bw.write(headerKey+","+headerValue);
			bw.newLine();
			
			for (Map.Entry<String, ?> entry : map.entrySet())
			{
				// bw.write(entry.getKey()+","+entry.getValue());
				// bw.append(entry.getValue().toString() + ",");
				toWrite.append(entry.getValue().toString() + ",");
			}
			
			bw.append(toWrite.toString());
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Writes the following for All, Train and Test timelines ActivityCountsInGivenDayTimelines, ActivityDurationInGivenDayTimelines, ActivityOccPercentageOfTimelines,
	 * NumOfDistinctValidActivitiesPerDayInGivenDayTimelines and returns two maps (each sorted by decreasing order of values), one for baseline count and other for baseline
	 * duration for training timelines.
	 * 
	 * @param userName
	 * @param userAllDatesTimeslines
	 * @param userTrainingTimelines
	 * @param userTestTimelines
	 * @return linkedhashmap contain two maps for training timelines, one for baseline count and other for baseline duration. Here, map for baseline count contains
	 *         <ActivityNames,count over all days in training timelines>, similarly for count of baseline duration
	 */
	public static LinkedHashMap<String, LinkedHashMap<String, ?>> writeBasicActivityStatsAndGetBaselineMaps(String userName,
			LinkedHashMap<Date, UserDayTimeline> userAllDatesTimeslines, LinkedHashMap<Date, UserDayTimeline> userTrainingTimelines,
			LinkedHashMap<Date, UserDayTimeline> userTestTimelines)
	{
		String commonPath = Constant.getCommonPath();
		
		String timelinesSets[] = { "AllTimelines", "TrainingTimelines", "TestTimelines" };
		LinkedHashMap<Date, UserDayTimeline> timelinesCursor = null;
		
		// the thing to return, contains two hashmaps used for count and duration baselines
		LinkedHashMap<String, LinkedHashMap<String, ?>> resultsToReturn = new LinkedHashMap<String, LinkedHashMap<String, ?>>();
		
		// Needed for base line recommendations (based on training set only)
		LinkedHashMap<String, Long> activityNameCountPairsOverAllTrainingDays;
		// Needed for base line recommendations (based on training set only)
		LinkedHashMap<String, Long> activityNameDurationPairsOverAllTrainingDays;
		// not used currently
		LinkedHashMap<String, Double> activityNameOccPercentageOverAllTrainingDays;
		
		for (String timelinesSet : timelinesSets)
		{
			switch (timelinesSet)
			{
				case "AllTimelines":
					timelinesCursor = userAllDatesTimeslines;
					break;
				case "TrainingTimelines":
					timelinesCursor = userTrainingTimelines;
					break;
				case "TestTimelines":
					timelinesCursor = userTestTimelines;
					break;
				default:
					PopUps.showError("Error in org.activity.tests.RecommendationTestsDaywiseJan2016: Unrecognised timelinesSet");
					break;
			}
			
			if (timelinesSet.equals("TrainingTimelines"))
			{
				activityNameCountPairsOverAllTrainingDays =
						WritingToFile.writeActivityCountsInGivenDayTimelines(userName, timelinesCursor, timelinesSet);
				activityNameCountPairsOverAllTrainingDays =
						(LinkedHashMap<String, Long>) UtilityBelt.sortByValue(activityNameCountPairsOverAllTrainingDays);
				resultsToReturn.put("activityNameCountPairsOverAllTrainingDays", activityNameCountPairsOverAllTrainingDays);
				
				activityNameDurationPairsOverAllTrainingDays =
						WritingToFile.writeActivityDurationInGivenDayTimelines(userName, timelinesCursor, timelinesSet);
				activityNameDurationPairsOverAllTrainingDays =
						(LinkedHashMap<String, Long>) UtilityBelt.sortByValue(activityNameDurationPairsOverAllTrainingDays);
				resultsToReturn.put("activityNameDurationPairsOverAllTrainingDays", activityNameDurationPairsOverAllTrainingDays);
				
				activityNameOccPercentageOverAllTrainingDays =
						WritingToFile.writeActivityOccPercentageOfTimelines(userName, timelinesCursor, timelinesSet);
			}
			
			else
			{
				LinkedHashMap<String, Long> actCountRes1 =
						WritingToFile.writeActivityCountsInGivenDayTimelines(userName, timelinesCursor, timelinesSet);
				LinkedHashMap<String, Long> actDurationRes1 =
						WritingToFile.writeActivityDurationInGivenDayTimelines(userName, timelinesCursor, timelinesSet);
				LinkedHashMap<String, Double> actOccPercentageRes1 =
						WritingToFile.writeActivityOccPercentageOfTimelines(userName, timelinesCursor, timelinesSet);
				
				writeSimpleLinkedHashMapToFileAppend(actCountRes1, Constant.getCommonPath() + "ActivityCounts" + timelinesSet + ".csv",
						"dummy", "dummy");
				writeSimpleLinkedHashMapToFileAppend(actDurationRes1,
						Constant.getCommonPath() + "ActivityDurations" + timelinesSet + ".csv", "dummy", "dummy");
				writeSimpleLinkedHashMapToFileAppend(actOccPercentageRes1,
						Constant.getCommonPath() + "ActivityPerOccur" + timelinesSet + ".csv", "dummy", "dummy");
				// writeSimpleLinkedHashMapToFileAppend(LinkedHashMap<String, ?> map, String fileName, String headerKey,
				// String headerValue)
				// writeSimpleLinkedHashMapToFile(LinkedHashMap<String, ?> map, String absFileName, String headerKey, String headerValue)
			}
			WritingToFile.writeNumOfDistinctValidActivitiesPerDayInGivenDayTimelines(userName, timelinesCursor, timelinesSet);
		}
		
		return resultsToReturn;
	}
	
	public static void closeBufferWriters(ArrayList<BufferedWriter> list)
	{
		list.stream().close();
	}
	
	public static void writeToFile(LinkedHashMap<Integer, Pair<Timestamp, Integer>> map, String fullPath)
	{
		try
		{
			BufferedWriter bw = WritingToFile.getBufferedWriterForNewFile(fullPath);
			bw.append("User,TimestampOfRT,NumOfValidsAfterIt");
			bw.newLine();
			
			for (Map.Entry<Integer, Pair<Timestamp, Integer>> entry : map.entrySet())
			{
				// bw.write(entry.getKey()+","+entry.getValue());
				String s = entry.getKey() + "," + entry.getValue().getFirst() + "," + entry.getValue().getSecond();
				bw.append(s);// entry.getValue().toString() + ","\);
				bw.newLine();
			}
			
			bw.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	// /**
	// * Headerless
	// *
	// * @param tmap
	// * @param fullPathToFileName
	// */
	// public static void writeTripleLinkedHashMap(LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Double,
	// ArrayList<Double>>>> userLevel, String
	// fullPathToFileName)
	// {
	// commonPath = Constant.getCommonPath();//
	// try
	// {
	// String fileName = fullPathToFileName;
	//
	// File file = new File(fileName);
	//
	// file.delete();
	// if (!file.exists())
	// {
	// file.createNewFile();
	// }
	//
	// FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
	// BufferedWriter bw = new BufferedWriter(fw);
	//
	// for (Entry<Integer, LinkedHashMap<String, LinkedHashMap<Double, ArrayList<Double>>>> entryUserLevel :
	// userLevel.entrySet())
	// {
	// // String userser =" + entryUserLevel.getKey());
	// LinkedHashMap<String, LinkedHashMap<Double, ArrayList<Double>>> rtLevel = entryUserLevel.getValue();
	// for (Entry<String, LinkedHashMap<Double, ArrayList<Double>>> entryRTLevel : rtLevel.entrySet())
	// {
	// // System.out.println("RT =" + entryRTLevel.getKey());
	// LinkedHashMap<Double, ArrayList<Double>> muLevel = entryRTLevel.getValue();
	// for (Entry<Double, ArrayList<Double>> entryMULevel : muLevel.entrySet())
	// {
	// // System.out.println("MU =" + entryMULevel.getKey());
	// // System.out.println("Edit distance of cands =" + entryMULevel.getValue());
	//
	// }
	// }
	//
	// }
	//
	// for (Map.Entry<String, TreeMap<Timestamp, String>> entryForUser : mapOfMap.entrySet())
	// {
	//
	// String userName = entryForUser.getKey();
	// TreeMap<Timestamp, String> mapForEachUser = entryForUser.getValue();
	//
	// for (Map.Entry<Timestamp, String> entryInside : mapForEachUser.entrySet())
	// {
	// bw.write(userName + "," + entryInside.getKey() + "," + entryInside.getValue());
	// bw.newLine();
	// }
	// }
	// bw.close();
	// }
	//
	// catch (Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	
	// public static void writeSimpleLinkedHashMapToFileAppendDouble(LinkedHashMap<String, Double> map, String fileName,
	// String headerKey,
	// String headerValue)
	// {
	// commonPath = Constant.getCommonPath();//
	// try
	// {
	// File fileToWrite = new File(fileName);
	// // fileToWrite.delete();
	// // fileToWrite.createNewFile();
	//
	// BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite, true));// ,true));
	//
	// // bw.write(headerKey+","+headerValue);
	// bw.newLine();
	//
	// for (Map.Entry<String, Double> entry : map.entrySet())
	// {
	// // bw.write(entry.getKey()+","+entry.getValue());
	// bw.append(entry.getValue() + ",");
	//
	// }
	//
	// bw.close();
	// }
	// catch (Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	// public static void writeMessage(String fileName, String message)
	// {
	// try
	// {
	// File fileToWrite= new File(fileName);
	// fileToWrite.delete();
	// fileToWrite.createNewFile();
	//
	// BufferedWriter bw= new BufferedWriter(new FileWriter(fileToWrite));//,true));
	//
	// bw.write(headerKey+","+headerValue);
	// bw.newLine();
	//
	// for (Map.Entry<String, Integer > entry: map.entrySet())
	// {
	// bw.write(entry.getKey()+","+entry.getValue());
	// bw.newLine();
	// }
	//
	// bw.close();
	// }
	// catch(Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	
}
