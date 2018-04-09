package org.activity.generator;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.activity.io.CSVUtils;
import org.activity.io.ReadXML;
import org.activity.io.ReadingFromFile;
import org.activity.io.Serializer;
import org.activity.io.WritingToFile;
import org.activity.objects.OpenStreetAddress;
import org.activity.objects.Pair;
import org.activity.objects.Triple;
import org.activity.spatial.SpatialUtils;
import org.activity.stats.StatsUtils;
import org.activity.util.ComparatorUtils;
import org.activity.util.HTTPUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class GowallaPreProcessorUtils
{

	public GowallaPreProcessorUtils()
	{
		// TODO Auto-generated constructor stub
	}

	public static void splitRawSpots1LocFile()
	{
		// Split files needed for fetching timezone for lats, longs in the datase:
		// include all locs in raw spots subset 1
		CSVUtils.splitCSVRowise("/home/gunjan/JupyterWorkspace/data/gowalla_spots_subset1_fromRaw28Feb2018.csv", ",",
				true, 10, "/home/gunjan/JupyterWorkspace/data/", "gowalla_spots_subset1_fromRaw28Feb2018smallerFile");

		long numOfLines = ReadingFromFile
				.getNumOfLines("/home/gunjan/JupyterWorkspace/data/gowalla_spots_subset1_fromRaw28Feb2018.csv");

		System.out.println("num of lines = " + numOfLines);
	}

	/**
	 * These are the locations for which OSM address has not been fetched yet
	 */
	public static void splitFilesWithRemainingLocsForFetchingOSMAddress()
	{
		String fileToRead = "/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/OSMAddressesCollected/targetLocsForWhichOSMAddressesNotCollectedWithTZ.csv";
		CSVUtils.splitCSVRowise(fileToRead, ",", true, 10,
				"/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/OSMAddressesCollected/targetLocsForWhichOSMAddressesNotCollectedWithTZSplitted/",
				"targetLocsForWhichOSMAddressesNotCollectedWithTZsmallerFile");

		long numOfLines = ReadingFromFile.getNumOfLines(fileToRead);

		System.out.println("num of lines = " + numOfLines);
	}

	/**
	 * Concatenate timezone collected in smaller files
	 * 
	 * @since Mar 20 2018
	 */
	public static void concatenateRawSpots1LocFileWithCollectedTZ()
	{
		// Split files needed for fetching timezone for lats, longs in the datase:
		// include all locs in raw spots subset 1

		// concatenate back the split files.
		String commonPath = "/home/gunjan/JupyterWorkspace/data/d10/gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZ";
		ArrayList<String> fileNamesToConcatenate = (ArrayList<String>) IntStream.rangeClosed(1, 10)
				.mapToObj(i -> commonPath + i + ".csv").collect(Collectors.toList());
		System.out.println("Files to concatenate:\n" + fileNamesToConcatenate.toString());
		CSVUtils.concatenateCSVFiles(fileNamesToConcatenate, true,
				"/home/gunjan/JupyterWorkspace/data/gowalla_spots_subset1_fromRaw28Feb2018TZAll.csv", ',');
		////
	}

	public static void main(String[] args)
	{
		// $$getGeoAddressForLocations();
		// getLocTimezoneMap();
		// $$slimProcessedData();//added earlier and then disabled on Mar 20 2018

		// $$consolidateAllFetchedOSMAddresses(); // added, used and disabled on Mar 20 2018
		// $$concatenateRawSpots1LocFileWithCollectedTZ();//
		// %% splitFilesWithRemainingLocsForFetchingOSMAddress();
		// 41.85003,-87.65005
		Triple<String, Double, Double> geoCoordinatesOfChicago = new Triple<>("Chicago", 41.836944, -87.684722);
		// ref:https://tools.wmflabs.org/geohack/geohack.php?pagename=Chicago&params=41_50_13_N_87_41_05_W_region:US-IL_type:city(2695598)
		// computeDistanceOfGeoCoordFromGivenGeoCoordinates(geoCoordinatesOfChicago,
		// "/home/gunjan/RWorkspace/GowallaRWorks/gw2CheckinsAllTargetUsersDatesOnly_ChicagoTZ_OnlyUsersWith_GTE75C_GTE54Pids_ByPids_uniquePid_Mar29.csv",
		// "/home/gunjan/RWorkspace/GowallaRWorks/gw2CheckinsAllTargetUsersDatesOnly_ChicagoTZ_OnlyUsersWith_GTE75C_GTE54Pids_ByPids_uniquePid_Mar29_DistFromChicago_4_SortWithShuffle.csv");

		String fileToRead = "/home/gunjan/RWorkspace/GowallaRWorks/gwCinsTarUDOnly_Merged_TarUDOnly_ChicagoTZ_TargetUsersDatesOnly_NVFUsers_ByPids_April6.csv";
		String fileToWrite = "/home/gunjan/RWorkspace/GowallaRWorks/gwCinsTarUDOnly_Merged_TarUDOnly_ChicagoTZ_TargetUsersDatesOnly_NVFUsers_ByPids_April6_DistFromChicago.csv";
		int colIndexOfLat = 1, colIndexOfLon = 2;
		computeDistanceOfGeoCoordFromGivenGeoCoordinates(geoCoordinatesOfChicago, fileToRead, fileToWrite,
				colIndexOfLat, colIndexOfLon);

	}

	/**
	 * 
	 * @param geoCoordinatesOfCity
	 * @param fileToReadGeoCoordinates
	 * @param absFileNameToWrite
	 * @param colIndexOfLat
	 * @param colIndexOfLon
	 * @return
	 */
	private static void computeDistanceOfGeoCoordFromGivenGeoCoordinates(
			Triple<String, Double, Double> geoCoordinatesOfCity, String fileToReadGeoCoordinates,
			String absFileNameToWrite, int colIndexOfLat, int colIndexOfLon)
	{
		// Map<Triple<Long, Double, Double>, Double> res = new LinkedHashMap<>();
		StringBuilder sb = new StringBuilder();

		try
		{
			List<List<String>> allLinesRead = ReadingFromFile.readLinesIntoListOfLists(fileToReadGeoCoordinates, ",");
			String header = (allLinesRead.get(0).stream().collect(Collectors.joining(","))) + ","
					+ geoCoordinatesOfCity.getFirst() + " in KM\n";
			sb.append(header);
			allLinesRead.remove(0);// remove header
			System.out.println("Num of lat lons read = " + allLinesRead.size());

			int lineCount = 0;
			for (List<String> lineRead : allLinesRead)
			{
				lineCount++;
				double latToCompare = Double.valueOf(lineRead.get(colIndexOfLat));
				double lonToCompare = Double.valueOf(lineRead.get(colIndexOfLon));

				Double distInKMs = StatsUtils
						.round(SpatialUtils.haversineFastMathV3NoRound(geoCoordinatesOfCity.getSecond(),
								geoCoordinatesOfCity.getThird(), latToCompare, lonToCompare), 7);

				// res.put(new Triple<>(placeID, latToCompare, lonToCompare), distInKMs);
				sb.append(lineRead.stream().collect(Collectors.joining(",")) + "," + distInKMs + "\n");
				// if (lineCount % 5000 == 0)
				// {
				// WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);
				// sb.setLength(0);
				// }
			}
			// WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);

			// res = ComparatorUtils.sortByValueDescNoShuffle(res);// TODO: WHY THIS IS NOT SORTING CORRECTLY.
			// res = ComparatorUtils.sortByValueDesc(res);
			// res.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey().getFirst() + "," +
			// e.getKey().getSecond()
			// + "," + e.getKey().getThird() + "," + e.getValue() + "\n"));
			WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		// return null;
	}

	private static Map<Triple<Long, Double, Double>, Double> computeDistanceOfGeoCoordFromGivenGeoCoordinates(
			Triple<String, Double, Double> geoCoordinatesOfCity, String fileToReadGeoCoordinates,
			String absFileNameToWrite)
	{
		Map<Triple<Long, Double, Double>, Double> res = new LinkedHashMap<>();
		StringBuilder sb = new StringBuilder("PlaceID,Lat,Lon,DistFrom" + geoCoordinatesOfCity.getFirst() + " in KM\n");
		try
		{
			List<List<String>> allLinesRead = ReadingFromFile.readLinesIntoListOfLists(fileToReadGeoCoordinates, ",");
			allLinesRead.remove(0);// remove header
			System.out.println("Num of lat lons read = " + allLinesRead.size());

			int lineCount = 0;
			for (List<String> lineRead : allLinesRead)
			{
				lineCount++;
				long placeID = Long.valueOf(lineRead.get(0));
				double latToCompare = Double.valueOf(lineRead.get(1));
				double lonToCompare = Double.valueOf(lineRead.get(2));

				Double distInKMs = StatsUtils
						.round(SpatialUtils.haversineFastMathV3NoRound(geoCoordinatesOfCity.getSecond(),
								geoCoordinatesOfCity.getThird(), latToCompare, lonToCompare), 7);

				res.put(new Triple<>(placeID, latToCompare, lonToCompare), distInKMs);
				sb.append(placeID + "," + latToCompare + "," + lonToCompare + "," + distInKMs + "\n");

				// if (lineCount % 5000 == 0)
				// {
				// WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);
				// sb.setLength(0);
				// }
			}
			// WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);

			// res = ComparatorUtils.sortByValueDescNoShuffle(res);// TODO: WHY THIS IS NOT SORTING CORRECTLY.
			res = ComparatorUtils.sortByValueDesc(res);
			res.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey().getFirst() + "," + e.getKey().getSecond()
					+ "," + e.getKey().getThird() + "," + e.getValue() + "\n"));
			WritingToFile.appendLineToFileAbsolute(sb.toString(), absFileNameToWrite);

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Consolidate OSM address fetched in multiple files (from multiple servers)
	 * 
	 * 
	 * @since Mar 20 2018
	 */
	public static void consolidateAllFetchedOSMAddresses()
	{
		String commonPathToRead = "/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/OSMAddressesCollected/";
		Set<Path> pathsOfFoundFiles = new TreeSet<>(); // filepaths matching the file name pattern
		StringBuilder res = new StringBuilder();
		String fileNamePatternToSearch = "targetLocsForWhichOSMAddressesNowCollectedWithTZsmallerFileAddress";
		// "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZAddress";
		Set<List<String>> allUniqueCompleteOSMAddressesRead = new LinkedHashSet<>();
		List<String> commonHeaderString = null;
		boolean headerAssigned = false;

		int totalLinesRead = 0, numOfLastIncompleteLinesRemoved = 0;
		Set<List<String>> incorrectLengthStrings = new LinkedHashSet<>();
		try
		{
			Stream<Path> allPaths = Files.walk(Paths.get(commonPathToRead), FileVisitOption.FOLLOW_LINKS);
			res.append("\n\n---   found files with names matching '" + fileNamePatternToSearch + "':\n ");
			// find filepaths matching the file name pattern
			pathsOfFoundFiles = allPaths.filter(e -> Files.isRegularFile(e))
					.filter(e -> e.toString().contains(fileNamePatternToSearch))
					.peek(e -> res.append("\t-" + e.toString() + "\n")).collect(Collectors.toSet());
			res.append("---   num of files matching " + fileNamePatternToSearch + " = " + pathsOfFoundFiles.size()
					+ " regular files.");

			System.out.println(res.toString());

			for (Path p : pathsOfFoundFiles)
			{
				List<List<String>> linesRead = ReadingFromFile.nColumnReaderStringLargeFile(Files.newInputStream(p),
						"|", true, true);

				if (linesRead.size() == 0)
				{
					System.out.println("Empty file: " + p.toString());
				}

				totalLinesRead += linesRead.size();

				if (!headerAssigned)
				{
					commonHeaderString = linesRead.get(0);
				}

				// if last line is incomplete then remove it
				if (linesRead.get(linesRead.size() - 1).size() != commonHeaderString.size())
				{
					numOfLastIncompleteLinesRemoved += 1;
					linesRead.remove(linesRead.size() - 1);// remove last element which is usually incomplete
				}

				linesRead.remove(0);// remove header

				for (List<String> line : linesRead)
				{
					if (line.size() != commonHeaderString.size())
					{
						incorrectLengthStrings.add(line);
					}
					else
					{
						allUniqueCompleteOSMAddressesRead.add(line);
					}
				}
				// allUniqueCompleteOSMAddressesRead.addAll(linesRead);
			}

			System.out.println("totalLinesRead = " + totalLinesRead);

			System.out.println("numOfLastIncompleteLinesRemoved = " + numOfLastIncompleteLinesRemoved);
			System.out.println("incorrectLengthStrings.size() = " + incorrectLengthStrings.size());

			System.out.println("allUniqueCompleteOSMAddressesRead = " + allUniqueCompleteOSMAddressesRead.size());

			int forSanityCheck = allUniqueCompleteOSMAddressesRead.size() + pathsOfFoundFiles.size()
					+ numOfLastIncompleteLinesRemoved + incorrectLengthStrings.size();
			System.out.println("addressesStored+numOfHeaders+numOfLastLinesRemoved+numOfIncorrectLengthStrings= "
					+ forSanityCheck + " Sanity check pass: " + (forSanityCheck == totalLinesRead));

			System.out.println("num of incorrect length string = " + incorrectLengthStrings.size());
			StringBuilder sbt1 = new StringBuilder("Incorrect length strings: \n");
			incorrectLengthStrings.stream().forEach(s -> sbt1.append(s.toString() + "\n"));
			System.out.println(sbt1.toString());

			StringBuilder sbToWrite = new StringBuilder();
			sbToWrite.append(commonHeaderString.stream().collect(Collectors.joining("|")) + "\n");
			for (List<String> osmAddress : allUniqueCompleteOSMAddressesRead)
			{
				sbToWrite.append(osmAddress.stream().collect(Collectors.joining("|")) + "\n");
			}
			WritingToFile.writeToNewFile(sbToWrite.toString(), commonPathToRead + "ConsolidatedOSMAddress29March.csv");

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

	/**
	 * @since 16 Mar 2018
	 */
	public static void slimProcessedData()
	{
		boolean verboseReading = true;
		String fileToRead = "/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/Mar15/DataGeneration/processedCheckIns.csv";
		String fileToWrite = "/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/Mar15/DataGeneration/processedCheckInsSlimmedForR.csv";
		int[] columnIndicesToSelect = new int[] { 0, 1, 4, 7, 11 };
		// Files.newInputStream(fileToRead);
		// List<List<String>> raw = new ArrayList<>();
		StringBuilder sbToWrite = new StringBuilder();
		String splitLiteral = Pattern.quote(",");
		BufferedWriter bwToWrite = WritingToFile.getBWForNewFile(fileToWrite);

		if (verboseReading)
		{
			System.out.println("nColumnReaderStringLargeFile --");
		}
		try
		{
			LineIterator it = IOUtils.lineIterator(new FileInputStream(fileToRead), "UTF-8");
			long countOfLinesRead = 0;

			while (it.hasNext())
			{
				countOfLinesRead += 1;
				String line = it.nextLine();
				String[] splittedString = (line.split(splitLiteral));
				// int indexToSelect : columnIndicesToSelect)
				for (int i = 0; i < (columnIndicesToSelect.length - 1); i++)
				{
					sbToWrite.append(splittedString[columnIndicesToSelect[i]]).append(",");
				}

				sbToWrite.append(splittedString[columnIndicesToSelect[columnIndicesToSelect.length - 1]]).append("\n");

				if (verboseReading && (countOfLinesRead % 25000 == 0))
				{
					System.out.println("Lines read: " + countOfLinesRead);
				}

				if (countOfLinesRead % 70870 == 0)// 48260 == 0) // 24130 find divisors of 36001960 using
				{
					bwToWrite.write(sbToWrite.toString());
					sbToWrite.setLength(0);
				}
			}

			if (sbToWrite.length() > 0)
			{
				System.out.println("Writing leftovers ... ");
				bwToWrite.write(sbToWrite.toString());
				sbToWrite.setLength(0);
				// bw2.write(toWriteInBatch2.toString());
				// toWriteInBatch2.setLength(0);
			}

			System.out.println("Total Lines read: " + countOfLinesRead);
			it.close();
			bwToWrite.close();
		}
		catch (IOException e)
		{
			System.err.println("Exception reading file from : " + fileToRead);
			e.printStackTrace();
		}

		// System.out.println("raw=\n" + raw);
		// return raw;
	}

	public static Map<Long, TimeZone> getLocTimezoneMap()
	{
		String commonPath = "/run/media/gunjan/BackupVault/GOWALLA/GowallaDataWorks/Mar15/";
		// Path fileToRead = Paths.get(commonPath,
		// "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZAllConcatenated.csv");// seagate
		String fileToRead = commonPath
				+ "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZAllConcatenated.csv";// seagate

		Map<Long, TimeZone> locIDTimezoneMap = new HashMap<>();
		List<Long> locIDsWithNoTimezone = new ArrayList<>();
		List<String> locIDsWithNoTimezoneWithLatLon = new ArrayList<>();
		try
		{
			List<List<String>> linesRead = ReadingFromFile.readLinesIntoListOfLists(fileToRead, ",");
			// ReadingFromFile.nColumnReaderStringLargeFileSelectedColumns(
			// Files.newInputStream(fileToRead), ",", true, false, new int[] { 1, 4 });
			System.out.println("locations read = " + linesRead.size());

			int count = 0;
			for (List<String> line : linesRead)
			{
				if (++count == 1)
				{
					continue;// skipe header
				}
				Long locID = Long.valueOf(line.get(1));
				if (line.size() < 5)
				{
					locIDsWithNoTimezoneWithLatLon.add(line.get(1) + "," + line.get(2) + "," + line.get(3));
					locIDsWithNoTimezone.add(locID);
				}
				else
				{
					locIDTimezoneMap.put(locID, TimeZone.getTimeZone(line.get(4)));
				}
			}

			// linesRead.stream().skip(1)
			// .forEachOrdered(l -> locIDTimezoneMap.put(Long.valueOf(l.get(0)), TimeZone.getTimeZone(l.get(1))));

			System.out.println("locIDTimezoneMap.size=" + locIDTimezoneMap.size());
			System.out.println("locIDsWithNoTimezone.size=" + locIDsWithNoTimezone.size());
			WritingToFile.writeToNewFile(locIDsWithNoTimezoneWithLatLon.stream().collect(Collectors.joining("\n")),
					commonPath + "locIDsWithNoTimezone.csv");

			Serializer.kryoSerializeThis(locIDTimezoneMap, commonPath + "locIDTimezoneMap.kryo");
			Serializer.kryoSerializeThis(locIDsWithNoTimezone, commonPath + "locIDsWithNoTimezone.kryo");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return locIDTimezoneMap;
	}

	public static void addTimezoneToSpotsSubset1RawData()
	{
		try
		{

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// https://maps.google.com/maps/api/geocode/xml?address=Malvern+City+of+Stonnington
	public static void getGeoAddressForLocations()
	{
		Map<Integer, OpenStreetAddress> locIDAllAddressMap = new LinkedHashMap<>();
		String commonPathToRead = "./dataToRead/Mar19/targetLocsForWhichOSMAddressesNotCollectedWithTZSplitted/";// "./dataToRead/Mar12/";
		// String commonPathToWrite = commonPathToRead;
		long totalNumOfEmptyAddresses = 0, totalNumOfNeitherCityTownOrVillage = 0;

		WritingToFile.writeToNewFile("", commonPathToRead + "SampleOSMNeitherCityTownOrVillage.xml");
		Map<String, Long> allTagsCount = new LinkedHashMap<>();

		try
		{
			for (int iteratorID = 1; iteratorID <= 2; iteratorID++)
			{
				// String fileNameToReadPhrase = "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZ"
				// + iteratorID + ".csv";
				// String fileNameToWritePhrase =
				// "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZAddress"
				// + iteratorID + ".csv";

				String fileNameToReadPhrase = "targetLocsForWhichOSMAddressesNotCollectedWithTZsmallerFile" + iteratorID
						+ ".csv";
				String fileNameToWritePhrase = "targetLocsForWhichOSMAddressesNowCollectedWithTZsmallerFileAddress"
						+ iteratorID + ".csv";

				Triple<Map<Integer, OpenStreetAddress>, Long, Map<String, Long>> locIDAddressMapRes = getAddressesForLatLonForRawFile(
						commonPathToRead, fileNameToReadPhrase, fileNameToWritePhrase);

				Map<Integer, OpenStreetAddress> locIDAddressMap = locIDAddressMapRes.getFirst();
				// Serializer.kryoSerializeThis(locIDAddressMap, fileNameToWritePhrase + ".kryo");

				long numOfNeitherCityTownOrVillage = locIDAddressMapRes.getSecond();

				Map<String, Long> tagsCount = locIDAddressMapRes.getThird();
				for (Entry<String, Long> entry : tagsCount.entrySet())
				{
					long oldVal = 0;
					if (allTagsCount.containsKey(entry.getKey()))
					{
						oldVal = allTagsCount.get(entry.getKey());
					}
					allTagsCount.put(entry.getKey(), oldVal + entry.getValue());
				}

				if (numOfNeitherCityTownOrVillage > 0)
				{
					// StringBuilder sb = new StringBuilder();
					// locIDAddressMap.entrySet().stream()
					// .forEachOrdered(e -> sb.append(e.getKey() + ": " + e.getValue().toString('|') + "\n"));
					// System.out.println(sb.toString() + "\n locIDAddressMap.size= " + locIDAddressMap.size());
				}
				long numOfNonEmptyAdress = locIDAddressMap.entrySet().stream()
						.mapToLong(e -> e.getValue().isEmptyAddress() == false ? 1 : 0).sum();

				long numOfEmptyAddresses = (locIDAddressMap.size() - numOfNonEmptyAdress);
				totalNumOfEmptyAddresses += numOfEmptyAddresses;
				totalNumOfNeitherCityTownOrVillage += numOfNeitherCityTownOrVillage;
				// System.out.println("Num of empty adresses = " + numOfEmptyAddresses);
				// System.out.println("numOfNeitherCityTownOrVillage = " + numOfNeitherCityTownOrVillage);

				// Thread.sleep(1000 * 1);
			}

			StringBuilder sb3 = new StringBuilder();
			allTagsCount.entrySet().stream().forEachOrdered(e -> sb3.append(e.getKey() + "," + e.getValue() + "\n"));
			WritingToFile.writeToNewFile(sb3.toString(), commonPathToRead + "allAddressTagCount.csv");
			System.out.println("totalNumOfEmptyAddresses = " + totalNumOfEmptyAddresses);

			System.out.println("totalNumOfNeitherCityTownOrVillage = " + totalNumOfNeitherCityTownOrVillage);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param commonPathToRead
	 * @param fileNameToReadPhrase
	 * @param fileNameToWritePhrase
	 * @return
	 */
	public static Triple<Map<Integer, OpenStreetAddress>, Long, Map<String, Long>> getAddressesForLatLonForRawFile(
			String commonPathToRead, String fileNameToReadPhrase, String fileNameToWritePhrase)
	{
		Map<Integer, OpenStreetAddress> locIDAddressMap = new LinkedHashMap<>();
		Map<String, Long> allAddressTagCount = new LinkedHashMap<>();
		long numOfNeitherCityTownOrVillage = 0;
		// String commonPathToRead = "/home/gunjan/JupyterWorkspace/data/d10/";
		// // String commonPathToWrite = commonPathToRead;
		// String fileNameToReadPhrase = "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZ1.csv";
		// String fileNameToWritePhrase =
		// "gowalla_spots_subset1_fromRaw28Feb2018smallerFileWithSampleWithTZAddress1.csv";

		try
		{
			// BufferedWriter bwToWrite = WritingToFile.getBWForNewFile(commonPathToRead + fileNameToWritePhrase);
			BufferedWriter bwToWriteDebugSampleOSMAddress = WritingToFile
					.getBufferedWriterForExistingFile(commonPathToRead + "SampleOSMNeitherCityTownOrVillage.xml");
			// bwToWrite.write("id|lng|lat|TZ|road|cityOrTownOrVillage|county|state|postcode|country|country_code\n");
			WritingToFile.writeToNewFile(
					"id|lng|lat|TZ|road|cityOrTownOrVillage|county|state|postcode|country|country_code\n",
					commonPathToRead + fileNameToWritePhrase);

			List<List<String>> allLines = ReadingFromFile.nColumnReaderStringLargeFile(
					new FileInputStream(commonPathToRead + fileNameToReadPhrase), ",", true, false);

			int count = 0;
			List<String> header = new ArrayList<>();

			for (List<String> line : allLines)
			{
				count += 1;
				if (count == 1)
				{
					header = line;
					continue;
				}
				if (count % 1000 == 0)
				{
					Thread.sleep(1000 * 2);
					// break;
				}

				// if (count > 5)
				// {
				// break;
				// }
				// String lat = "40.774269";
				// String lon = "-89.603806";
				// int id = 1;
				String lat = line.get(3);
				String lon = line.get(2);
				int id = Integer.valueOf(line.get(1));

				HTTPUtils httpUtils = new HTTPUtils(1000 * 2);
				ReadXML readXML = new ReadXML();

				// Map<String, String> allVals = getAddressOSA(lat, lon, httpUtils, readXML);
				// StringBuilder sb = new StringBuilder();
				// allVals.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey() + ": " + e.getValue() + "\n"));
				// System.out.println(sb.toString() + "\n----------------------------");

				// Get OSM Address Data
				Pair<Map<String, String>, String> allValsRes = getAddressV2(lat, lon, httpUtils, readXML);

				// Start of count all address tags
				Set<String> addressTags = allValsRes.getFirst().keySet();
				for (String addressTag : addressTags)
				{
					if (allAddressTagCount.containsKey(addressTag))
					{
						allAddressTagCount.put(addressTag, allAddressTagCount.get(addressTag) + 1);
					}
					else
					{
						allAddressTagCount.put(addressTag, new Long(1));
					}
				}
				// End of count all address tags

				// COnvert to Address Object
				Triple<OpenStreetAddress, Boolean, String> addressRes = toOpenStreetAddressObjectV2(allValsRes);

				if (addressRes.getSecond() == true)
				{
					numOfNeitherCityTownOrVillage += 1;
					// System.out.println("Response for neitherCityTownOrVillage true is:\n "
					// + ReadXML.prettyPrint(addressRes.getThird()));

					bwToWriteDebugSampleOSMAddress.append(ReadXML.prettyPrint(addressRes.getThird()) + "\n");
				}

				OpenStreetAddress address = addressRes.getFirst();
				locIDAddressMap.put(id, address);

				StringBuilder sb2 = new StringBuilder();
				char delimiter = '|';
				sb2.append(line.stream().skip(1).collect(Collectors.joining(String.valueOf(delimiter))).toString()
						+ delimiter);
				sb2.append(address.toString(delimiter) + "\n");
				// bwToWrite.append(sb2.toString());
				WritingToFile.appendLineToFileAbsolute(sb2.toString(), commonPathToRead + fileNameToWritePhrase);

			}

			// bwToWrite.close();
			bwToWriteDebugSampleOSMAddress.close();

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return new Triple<>(locIDAddressMap, numOfNeitherCityTownOrVillage, allAddressTagCount);
	}

	/**
	 * Calls getAddressV2 to fetch OSM Address, created
	 * 
	 * @param lat
	 * @param lon
	 * @param httpUtil
	 * @param readXML
	 * @return
	 * @return Triple{OpenStreetAddress, neitherCityTownOrVillageOrHamlet, Pair{httpResponse, Map{tagName, tagCounts}>>>
	 */
	public static Triple<OpenStreetAddress, Boolean, String> toOpenStreetAddressObjectV2(
			Pair<Map<String, String>, String> allXMLRes)
	{
		Map<String, String> allXMLTagVals = allXMLRes.getFirst();
		String response = allXMLRes.getSecond();

		String cityTownVillHamLocCityDisCityNamedCountySuburb = "", roadOrPedestrian = "", stateOrRegion = "";

		boolean neitherCityTownVillHamletLocalityCityNamedCounty = false, neitherRoadOrPedestrian = false,
				neitherStateNorRegion = false;

		// StringBuilder sb = new StringBuilder();
		// allVals.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey() + "-" + e.getValue() + "\n"));
		// System.out.println("What we got is:--" + sb.toString() + "\n");

		if (allXMLTagVals.get("city") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("city");
		}
		else if (allXMLTagVals.get("town") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("town");
		}
		else if (allXMLTagVals.get("village") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("village");
		}
		else if (allXMLTagVals.get("hamlet") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("hamlet");
			// System.out.println("This is a hamlet");
		}
		else if (allXMLTagVals.get("locality") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("locality");
		}
		else if (allXMLTagVals.get("city_district") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("city_district");
		}
		// County with "City" in its name, such as, City of Ekurhuleni Metropolitan Municipality,City of Stonnington
		else if ((allXMLTagVals.get("county") != null)
				&& ((allXMLTagVals.get("county").contains("City")) || (allXMLTagVals.get("county").contains("city"))))
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("county");
			System.out.println("CityNamedCounty= " + cityTownVillHamLocCityDisCityNamedCountySuburb);
		}
		else if (allXMLTagVals.get("suburb") != null)// != null)
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = allXMLTagVals.get("suburb");
		}
		else
		{
			cityTownVillHamLocCityDisCityNamedCountySuburb = "";
			neitherCityTownVillHamletLocalityCityNamedCounty = true;
			// System.out.println("neitherCityTownOrVillageOrHamlet= " + neitherCityTownOrVillageOrHamlet);
		}

		if (allXMLTagVals.get("road") != null)// != null)
		{
			roadOrPedestrian = allXMLTagVals.get("road");
		}
		else if (allXMLTagVals.get("pedestrian") != null)// != null)
		{
			roadOrPedestrian = allXMLTagVals.get("pedestrian");
		}
		else
		{
			roadOrPedestrian = "";
			neitherRoadOrPedestrian = true;
		}

		if (allXMLTagVals.get("state") != null)// != null)
		{
			stateOrRegion = allXMLTagVals.get("state");
		}
		else if (allXMLTagVals.get("region") != null)// != null)
		{
			stateOrRegion = allXMLTagVals.get("region");
		}
		else
		{
			stateOrRegion = "";
			neitherStateNorRegion = true;
		}

		if (neitherCityTownVillHamletLocalityCityNamedCounty)
		{
			System.out.println("neitherCityTownOrVillageOrHamlet= " + neitherCityTownVillHamletLocalityCityNamedCounty);
		}

		// System.out.println("cityOrTownOrVillageOrHamlet=" + cityOrTownOrVillageOrHamlet);
		return new Triple<>(
				new OpenStreetAddress(/* allVals.get("house_number"), */ roadOrPedestrian,
						cityTownVillHamLocCityDisCityNamedCountySuburb, allXMLTagVals.get("county"), stateOrRegion,
						allXMLTagVals.get("postcode"), allXMLTagVals.get("country"), allXMLTagVals.get("country_code")),
				neitherCityTownVillHamletLocalityCityNamedCounty, response);

	}

	/**
	 * Calls getAddressV2 to fetch OSM Address, created
	 * 
	 * @param lat
	 * @param lon
	 * @param httpUtil
	 * @param readXML
	 * @return
	 * @return Triple{OpenStreetAddress, neitherCityTownOrVillageOrHamlet, Pair{httpResponse, Map{tagName, tagCounts}>>>
	 */
	public static Triple<OpenStreetAddress, Boolean, String> getAddressOSA(String lat, String lon, HTTPUtils httpUtil,
			ReadXML readXML)
	{
		// Triple<Map<String, String>, String, Map<String, Long>> allValsRes = getAddress(lat, lon, httpUtil, readXML,
		// true);
		Pair<Map<String, String>, String> allValsRes = getAddressV2(lat, lon, httpUtil, readXML);

		Map<String, String> allVals = allValsRes.getFirst();
		String response = allValsRes.getSecond();

		String cityOrTownOrVillageOrHamlet = "", roadOrPedestrian = "", stateOrRegion = "";

		boolean neitherCityTownOrVillageOrHamlet = false, neitherRoadOrPedestrian = false,
				neitherStateNorRegion = false;

		// StringBuilder sb = new StringBuilder();
		// allVals.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey() + "-" + e.getValue() + "\n"));
		// System.out.println("What we got is:--" + sb.toString() + "\n");

		if (allVals.get("city").trim().length() != 0)// != null)
		{
			cityOrTownOrVillageOrHamlet = allVals.get("city");
		}
		else if (allVals.get("town").trim().length() != 0)// != null)
		{
			cityOrTownOrVillageOrHamlet = allVals.get("town");
		}
		else if (allVals.get("village").trim().length() != 0)// != null)
		{
			cityOrTownOrVillageOrHamlet = allVals.get("village");
		}
		else if (allVals.get("hamlet").trim().length() != 0)// != null)
		{
			cityOrTownOrVillageOrHamlet = allVals.get("hamlet");
			// System.out.println("This is a hamlet");
		}
		else
		{
			cityOrTownOrVillageOrHamlet = "";
			neitherCityTownOrVillageOrHamlet = true;
			// System.out.println("neitherCityTownOrVillageOrHamlet= " + neitherCityTownOrVillageOrHamlet);
		}

		if (allVals.get("road").trim().length() != 0)// != null)
		{
			roadOrPedestrian = allVals.get("road");
		}
		else if (allVals.get("pedestrian").trim().length() != 0)// != null)
		{
			roadOrPedestrian = allVals.get("pedestrian");
		}
		else
		{
			roadOrPedestrian = "";
			neitherRoadOrPedestrian = true;
		}

		if (allVals.get("state").trim().length() != 0)// != null)
		{
			stateOrRegion = allVals.get("state");
		}
		else if (allVals.get("region").trim().length() != 0)// != null)
		{
			stateOrRegion = allVals.get("region");
		}
		else
		{
			stateOrRegion = "";
			neitherStateNorRegion = true;
		}

		if (neitherCityTownOrVillageOrHamlet)
		{
			System.out.println("neitherCityTownOrVillageOrHamlet= " + neitherCityTownOrVillageOrHamlet);
		}

		// System.out.println("cityOrTownOrVillageOrHamlet=" + cityOrTownOrVillageOrHamlet);
		return new Triple<>(new OpenStreetAddress(/* allVals.get("house_number"), */ roadOrPedestrian,
				cityOrTownOrVillageOrHamlet, allVals.get("county"), stateOrRegion, allVals.get("postcode"),
				allVals.get("country"), allVals.get("country_code")), neitherCityTownOrVillageOrHamlet, response);

	}

	/**
	 * 
	 * @param lat
	 * @param lon
	 * @param httpUtil
	 * @param readXML
	 * @return
	 */
	public static Triple<Map<String, String>, String, Map<String, Long>> getAddress(String lat, String lon,
			HTTPUtils httpUtil, ReadXML readXML, boolean countAddressTags)
	{
		Map<String, String> allVals = new LinkedHashMap<>();
		Map<String, Long> tagCounts = new TreeMap<>();
		String response = "";
		try
		{
			// String response = httpUtil.getResponse("https://nominatim.openstreetmap.org/reverse?format=xml&lat=" +
			// lat
			// + "&lon=" + lon + "&zoom=18&addressdetails=1");
			//
			response = httpUtil.getResponse(
					"https://nominatim.openstreetmap.org/reverse?email=gunjanthesystem@gmail.com&format=xml&lat=" + lat
							+ "&lon=" + lon + "&zoom=18&addressdetails=1&accept-language=en");

			// $$ System.out.println(response);// enable to see actual response with all details

			Document doc = readXML.loadXMLFromString(response);
			// optional, but recommended
			// read this -
			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			// $System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
			String[] listOfTags = { /* "house_number", */ "road", "pedestrian", "city", "town", "village", "hamlet",
					"county", "state", "region", "postcode", "country", "country_code" };

			// find all the unique tags in the addresses START
			if (countAddressTags)
			{
				NodeList nl = doc.getElementsByTagName("*");
				for (int i = 0; i < nl.getLength(); i++)
				{
					String nodeName = nl.item(i).getNodeName();
					if (tagCounts.containsKey(nodeName))
					{
						tagCounts.put(nodeName, tagCounts.get(nodeName) + 1);
					}
					else
					{
						tagCounts.put(nodeName, new Long(1));
					}
					// System.out.println("name is : " + nl.item(i).getNodeName());
				}
			}
			// find all the unique tags in the addresses END

			for (String tagName : listOfTags)
			{
				String val = "";
				// System.out.println("tagName=" + tagName);

				if ((doc.getElementsByTagName(tagName) == null) || (doc.getElementsByTagName(tagName).item(0) == null)
						|| (doc.getElementsByTagName(tagName).item(0).getTextContent() == null))
				{
					// tags allowed empty individually
					if ((tagName.equals("city") == false) && (tagName.equals("town") == false)
							&& (tagName.equals("village") == false) && (tagName.equals("hamlet") == false)
							&& (tagName.equals("pedestrian") == false) && (tagName.equals("road") == false)
							&& (tagName.equals("county") == false) && (tagName.equals("state") == false)
							&& (tagName.equals("region") == false) && (tagName.equals("postcode") == false))
					{
						System.out.println("null for=" + tagName + " response was:\n" + response);
					}
				}
				else
				// ((doc.getElementsByTagName(tagName) != null)
				// && (doc.getElementsByTagName(tagName).item(0).getTextContent() != null))
				{
					val = doc.getElementsByTagName(tagName).item(0).getTextContent();
				}

				allVals.put(tagName, val);
			}
			// // NodeList nList = doc.getElementsByTagName("house_number");
			// StringBuilder sb = new StringBuilder();
			// allVals.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey() + "-" + e.getValue() + "\n"));
			// System.out.println(sb.toString() + "\n----------------------------");
		}
		catch (

		Exception e)
		{
			e.printStackTrace();
		}

		return new Triple<>(allVals, response, tagCounts);
	}

	/**
	 * Contais all tags in the xml response and not just the selected ones
	 * <p>
	 * Fork of getAddress
	 * <p>
	 * 
	 * @param lat
	 * @param lon
	 * @param httpUtil
	 * @param readXML
	 * @return Pair{Map{AddressTagsFoundInXML, AddressTagContent}, httpXMLResponse}
	 * @since 13 Mar 2018
	 */
	public static Pair<Map<String, String>, String> getAddressV2(String lat, String lon, HTTPUtils httpUtil,
			ReadXML readXML)
	{
		Map<String, String> allVals = new LinkedHashMap<>();
		String response = "";
		try
		{
			// String response = httpUtil.getResponse("https://nominatim.openstreetmap.org/reverse?format=xml&lat=" +
			// lat+ "&lon=" + lon + "&zoom=18&addressdetails=1");
			response = httpUtil.getResponse("https://nominatim.openstreetmap.org/reverse?format=xml&lat=" + lat
					+ "&lon=" + lon + "&zoom=18&addressdetails=1&accept-language=en");

			// $$ System.out.println(response);// enable to see actual response with all details

			Document doc = readXML.loadXMLFromString(response);
			// optional, but recommended
			// read this -
			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			// $System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
			// String[] listOfTags = { /* "house_number", */ "road", "pedestrian", "city", "town", "village", "hamlet",
			// "county", "state", "region", "postcode", "country", "country_code" };

			// find all the unique tags in the addresses
			NodeList nl = doc.getElementsByTagName("*");
			for (int i = 0; i < nl.getLength(); i++)
			{
				String tagName = nl.item(i).getNodeName();
				// $$System.out.println("tagName=" + tagName);
				allVals.put(tagName, doc.getElementsByTagName(tagName).item(0).getTextContent());
			}
			// // NodeList nList = doc.getElementsByTagName("house_number");
			// StringBuilder sb = new StringBuilder();
			// allVals.entrySet().stream().forEachOrdered(e -> sb.append(e.getKey() + "-" + e.getValue() + "\n"));
			// System.out.println(sb.toString() + "\n----------------------------");
		}
		catch (

		Exception e)
		{
			e.printStackTrace();
		}

		return new Pair<>(allVals, response);
	}

}