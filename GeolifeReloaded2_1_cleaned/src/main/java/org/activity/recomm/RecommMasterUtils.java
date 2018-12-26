package org.activity.recomm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.activity.constants.Constant;
import org.activity.constants.Enums;
import org.activity.constants.Enums.CaseType;
import org.activity.constants.Enums.LookPastType;
import org.activity.constants.Enums.PrimaryDimension;
import org.activity.constants.VerbosityConstants;
import org.activity.evaluation.Evaluation;
import org.activity.objects.ActivityObject2018;
import org.activity.objects.Pair;
import org.activity.objects.Timeline;
import org.activity.objects.TimelineWithNext;
import org.activity.stats.StatsUtils;
import org.activity.ui.PopUps;
import org.activity.util.ComparatorUtils;

/**
 * Moved some static methods from RecommMaster to here, as these static methods could be shared amongst different recomm
 * masters.
 * 
 * @since 27 Nov 2017
 * 
 * @author gunjan
 *
 */
public class RecommMasterUtils
{

	static LinkedHashMap<String, String> extractCandUserIDs(LinkedHashMap<String, Timeline> candidateTimelines)
	{
		LinkedHashMap<String, String> candUserIDs = new LinkedHashMap<>();
		try
		{
			for (Entry<String, Timeline> candE : candidateTimelines.entrySet())
			{
				if (candE.getValue().isShouldBelongToSingleUser())
				{
					candUserIDs.put(candE.getKey(), candE.getValue().getUserID());
				}
				else
				{
					PopUps.printTracedErrorMsgWithExit("Error: not taking care of this case");
				}

			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return candUserIDs;
	}

	/**
	 * 
	 * @param nextActivityObjectsFromCands
	 * @param caseType
	 * @param similarityOfEndPointActObjCands
	 *            used for CaseBasedV1_3 approach
	 * @param lookPastType
	 * @param distancesSortedMap
	 *            used for closest time approach in which case it is {candid, Pair{act name of act obj of cloeset, abs
	 *            diff of st}}
	 * @param givenDimension
	 * @return
	 */
	static LinkedHashMap<String, Double> createRankedTopRecommendedActivityGDVals(
			LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActivityObjectsFromCands, CaseType caseType,
			LinkedHashMap<String, Double> similarityOfEndPointActObjCands, LookPastType lookPastType,
			LinkedHashMap<String, Pair<String, Double>> distancesSortedMap, PrimaryDimension givenDimension)
	{

		if (lookPastType.equals(Enums.LookPastType.ClosestTime))
		{
			return createRankedTopRecommendedActivityNamesClosestTime(distancesSortedMap,
					RecommendationMasterMar2017GenSeqNov2017.timeInSecsForRankScoreNormalisation);
		}
		else if ((lookPastType.equals(Enums.LookPastType.Daywise)) || (lookPastType.equals(Enums.LookPastType.NHours))
				|| (lookPastType.equals(Enums.LookPastType.NCount)) || (lookPastType.equals(Enums.LookPastType.NGram)))
		{
			switch (caseType)
			{
				case SimpleV3:
					// createRankedTopRecommendedPDValsSimpleV3_3
					return createRankedTopRecommendedGDValsSimpleV3_3(nextActivityObjectsFromCands, givenDimension);
				case CaseBasedV1:
					return createRankedTopRecommendedActivityNamesCaseBasedV1_3(nextActivityObjectsFromCands,
							similarityOfEndPointActObjCands);
				default:
					System.err.println(PopUps.getTracedErrorMsg("Error:unrecognised case type = " + caseType));
					return null;
			}
		}
		else
		{
			System.err.println(PopUps.getTracedErrorMsg("Error:unrecognised lookpast type = " + lookPastType));
			return null;
		}

	}

	/**
	 * Generates a ranked list of recommended Activity Objects and sets recommendedActivityNamesRankscorePairs
	 * 
	 * and calls the following setter methods setRecommendedActivityNamesWithRankscores
	 * setRankedRecommendedActivityNamesWithRankScores setRankedRecommendedActivityNamesWithoutRankScores
	 * 
	 * Is function with Constants Beta and rank scoring
	 * 
	 * @since IN VERSION 2 WE HAD MIN MAX NORMALISATION INSIDE THIS FUNCTION, IN THIS V3 WE WILL NOT HAVE NORMALISATION
	 *        OF EDIT DISTANCE INSIDE THIS FUNCTION AS THE NORMALISATION IS DONE BEFOREHAND IN THE METHOD WHICH FETCHED
	 *        THE NORMALISED EDIT DISTANCE FOR CANDIDATE TIMELINES
	 * 
	 * @param topNextActivityObjectsWithDistance
	 * @return
	 */
	public static LinkedHashMap<String, Double> createRankedTopRecommendedActivityNamesCaseBasedV1_3(
			LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActObjsWithDistExceptEnd,
			LinkedHashMap<String, Double> similarityOfEndPointActivityObjectCands)
	{
		System.out.println(
				"Debug inside createRankedTopRecommendedActivityNamesCaseBasedV1_3: numberOfTopNextActivityEvenst=numberOfTopNextActivityEvenst");

		// / for calculating their correlation
		// // do this in the main constructor now
		// ArrayList<Double> normEditSimilarity = new ArrayList<Double>();
		// ArrayList<Double> simEndActivityObjForCorr = new ArrayList<Double>();

		LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<String, Double>(); // <ActivityName,RankScore>

		///

		///

		for (Map.Entry<String, Pair<ActivityObject2018, Double>> nextActObj : nextActObjsWithDistExceptEnd.entrySet())
		{
			String candTimelineID = nextActObj.getKey();
			String nextActivityName = nextActObj.getValue().getFirst().getActivityName();
			double normEditDistanceValExceptEnd = nextActObj.getValue().getSecond();

			double simEndPointActivityObject = similarityOfEndPointActivityObjectCands.get(candTimelineID);

			Double simRankScore;// represents similarity

			// // do this in the main constructor now
			// normEditSimilarity.add(1 - normEditDistanceValExceptEnd);
			// simEndActivityObjForCorr.add(simEndPointActivityObject);

			if (Constant.rankScoring.trim().equalsIgnoreCase("product"))
			{
				simRankScore = (1d - normEditDistanceValExceptEnd) * simEndPointActivityObject;
				System.out.println("Prod RANK SCORE CALC=" + "(1-(" + normEditDistanceValExceptEnd + "))* ("
						+ simEndPointActivityObject + ")");
			}
			else
			{
				simRankScore = Constant.ALPHA * (1d - normEditDistanceValExceptEnd)
						+ (1 - Constant.ALPHA) * simEndPointActivityObject;
				System.out.println("Sum RANK SCORE CALC=" + Constant.ALPHA + "*(1-(" + normEditDistanceValExceptEnd
						+ "))" + "" + "+" + (1 - Constant.ALPHA) + "*(" + simEndPointActivityObject + ")");
			}

			if (recommendedActivityNamesRankscorePairs.containsKey(nextActivityName) == false)
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName, simRankScore);
			}
			else
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName,
						recommendedActivityNamesRankscorePairs.get(nextActivityName) + simRankScore);
			}
		}
		// // Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric
		// value of rank)
		recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
				.sortByValueDesc(recommendedActivityNamesRankscorePairs);

		if (recommendedActivityNamesRankscorePairs == null || recommendedActivityNamesRankscorePairs.size() == 0)
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: recommendedActivityNamesRankscorePairs.size() = ")
					+ recommendedActivityNamesRankscorePairs.size());
		}

		return recommendedActivityNamesRankscorePairs;
	}

	/**
	 * // used
	 * <p>
	 * Score (A<sub>O</sub>) = ∑ { 1- min( 1, |Stcand - RT|/60mins) }
	 * 
	 * @param closestActObjsWithSTDiffInSecs
	 * @return {nextActObj,rankScore}
	 */
	public static LinkedHashMap<String, Double> createRankedTopRecommendedActivityNamesClosestTime(
			LinkedHashMap<String, Pair<String, Double>> closestActObjsWithSTDiffInSecs,
			double timeInSecsForRankScoreNormalisation)
	// LinkedHashMap<Date, Triple<Integer, ActivityObject, Double>> startTimeDistanceSortedMap)
	{
		// System.out.println("Debug inside createRankedTopRecommendedActivityNamesClosestTime:");

		LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<>(); // <ActivityName,RankScore>

		for (Map.Entry<String, Pair<String, Double>> entry : closestActObjsWithSTDiffInSecs.entrySet())
		{
			String nextActivityName = entry.getValue().getFirst();
			Double rankScore = 1d - Math.min(1, (entry.getValue().getSecond()) / timeInSecsForRankScoreNormalisation);
			// 60 * 60);

			if (recommendedActivityNamesRankscorePairs.containsKey(nextActivityName) == false)
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName, rankScore);
			}
			else
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName,
						recommendedActivityNamesRankscorePairs.get(nextActivityName) + rankScore);
			}
		}

		recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
				.sortByValueDesc(recommendedActivityNamesRankscorePairs);
		// Sorted in descending order of ranked score: higher ranked score means higher value of rank

		return recommendedActivityNamesRankscorePairs;
	}

	/**
	 * Generates a ranked list of recommended Activity Objects and sets recommendedActivityNamesRankscorePairs
	 * 
	 * and calls the following setter methods setRecommendedActivityNamesWithRankscores
	 * setRankedRecommendedActivityNamesWithRankScores setRankedRecommendedActivityNamesWithoutRankScores
	 * 
	 * Is function with Constants Beta and rank scoring
	 * 
	 * @since IN VERSION 2 WE HAD MIN MAX NORMALISATION INSIDE THIS FUNCTION, IN THIS V3 WE WILL NOT HAVE NORMALISATION
	 *        OF EDIT DISTANCE INSIDE THIS FUNCTION AS THE NORMALISATION IS DONE BEFOREHAND IN THE METHOD WHICH FETCHED
	 *        THE NORMALISED EDIT DISTANCE FOR CANDIDATE TIMELINES
	 * @param topNextActivityObjectsWithDistance
	 * @return {ActivityName,Rankscore} sorted by descending order of rank score
	 */
	public static LinkedHashMap<String, Double> createRankedTopRecommendedActivityNamesSimpleV3_3(
			LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActivityObjectsWithDistance)
	{
		// $$System.out.println("\ninside createRankedTopRecommendedActivityNamesSimpleV3_3:");
		// <ActivityName,RankScore>
		LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<>();

		StringBuilder rankScoreCalc = new StringBuilder();

		for (Map.Entry<String, Pair<ActivityObject2018, Double>> nextActObj : nextActivityObjectsWithDistance.entrySet())
		{ // String candTimelineID = nextActObj.getKey();
			String nextActivityName = nextActObj.getValue().getFirst().getActivityName();
			double normEditDistanceVal = nextActObj.getValue().getSecond();

			// represents similarity
			double simRankScore = (1d - normEditDistanceVal);// * simEndPointActivityObject;
			rankScoreCalc.append("Simple RANK SCORE (1- normED) =" + "1-" + normEditDistanceVal + "\n");

			if (recommendedActivityNamesRankscorePairs.containsKey(nextActivityName) == false)
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName, simRankScore);
			}
			else
			{
				recommendedActivityNamesRankscorePairs.put(nextActivityName,
						recommendedActivityNamesRankscorePairs.get(nextActivityName) + simRankScore);
			}
		}

		if (VerbosityConstants.verboseRankScoreCalcToConsole)
		{
			System.out.println(rankScoreCalc.toString());
		}

		// Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric value
		// of rank)
		recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
				.sortByValueDesc(recommendedActivityNamesRankscorePairs);

		if (recommendedActivityNamesRankscorePairs == null || recommendedActivityNamesRankscorePairs.size() == 0)
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: recommendedActivityNamesRankscorePairs.size() = ")
					+ recommendedActivityNamesRankscorePairs.size());
		}
		return recommendedActivityNamesRankscorePairs;
	}

	/**
	 * TODO check correctness
	 * <p>
	 * Generates a ranked list of recommended Activity Objects and sets recommendedActivityNamesRankscorePairs
	 * 
	 * and calls the following setter methods setRecommendedActivityNamesWithRankscores
	 * setRankedRecommendedActivityNamesWithRankScores setRankedRecommendedActivityNamesWithoutRankScores
	 * 
	 * Is function with Constants Beta and rank scoring
	 * <p>
	 * Changed from primary dimension version to given dimension verion on 18 July 2018
	 * 
	 * @param nextActivityObjectsWithDistance
	 * @param givenDimension
	 *            added on 18 July 2018
	 * @return {ActivityName,Rankscore} sorted by descending order of rank score
	 * @since IN VERSION 2 WE HAD MIN MAX NORMALISATION INSIDE THIS FUNCTION, IN THIS V3 WE WILL NOT HAVE NORMALISATION
	 *        OF EDIT DISTANCE INSIDE THIS FUNCTION AS THE NORMALISATION IS DONE BEFOREHAND IN THE METHOD WHICH FETCHED
	 *        THE NORMALISED EDIT DISTANCE FOR CANDIDATE TIMELINES
	 */
	public static LinkedHashMap<String, Double> createRankedTopRecommendedGDValsSimpleV3_3(
			LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActivityObjectsWithDistance,
			PrimaryDimension givenDimension)
	{
		// $$System.out.println("\ninside createRankedTopRecommendedActivityNamesSimpleV3_3:");
		// <ActivityName,RankScore>
		LinkedHashMap<String, Double> recommendedActivityGDValsRankscorePairs = new LinkedHashMap<>();

		StringBuilder rankScoreCalc = new StringBuilder();

		for (Map.Entry<String, Pair<ActivityObject2018, Double>> nextActObj : nextActivityObjectsWithDistance.entrySet())
		{ // String candTimelineID = nextActObj.getKey();
			double normEditDistanceVal = nextActObj.getValue().getSecond();

			for (Integer gdVal : nextActObj.getValue().getFirst().getGivenDimensionVal(givenDimension))// .getPrimaryDimensionVal())
			{// if the next activity object is a merged one
				String nextActivityGDVal = gdVal.toString();

				// represents similarity
				double simRankScore = (1d - normEditDistanceVal);// * simEndPointActivityObject;
				rankScoreCalc.append("Simple RANK SCORE (1- normED) =" + "1-" + normEditDistanceVal + "\n");

				if (recommendedActivityGDValsRankscorePairs.containsKey(nextActivityGDVal) == false)
				{
					recommendedActivityGDValsRankscorePairs.put(nextActivityGDVal, simRankScore);
				}
				else
				{
					recommendedActivityGDValsRankscorePairs.put(nextActivityGDVal,
							recommendedActivityGDValsRankscorePairs.get(nextActivityGDVal) + simRankScore);
				}
			}

		}

		if (VerbosityConstants.verboseRankScoreCalcToConsole)
		{
			System.out.println(rankScoreCalc.toString());
		}

		// Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric value
		// of rank)
		recommendedActivityGDValsRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
				.sortByValueDesc(recommendedActivityGDValsRankscorePairs);

		if (recommendedActivityGDValsRankscorePairs == null || recommendedActivityGDValsRankscorePairs.size() == 0)
		{
			PopUps.printTracedErrorMsg("Error: recommendedActivityGDValsRankscorePairs.size() = "
					+ recommendedActivityGDValsRankscorePairs.size());
		}
		return recommendedActivityGDValsRankscorePairs;
	}

	/**
	 * Fetches the next Activity Objects with their edit distance from the candidate timelines (wrt Current Timeline)
	 * <p>
	 * <font color = orange> casts Timeline to TimelineWithNext, this should be the case for non-daywise,i.e. NCount and
	 * NHours approaches.
	 * 
	 * @param editDistanceSortedFullCand
	 * @param candidateTimelines
	 * @return TimelineID,Pair{Next Activity Object,edit distance}
	 */
	public static LinkedHashMap<String, Pair<ActivityObject2018, Double>> fetchNextActivityObjectsFromNext(
			LinkedHashMap<String, Pair<String, Double>> editDistanceSortedFullCand,
			LinkedHashMap<String, Timeline> candidateTimelines)
	{
		// TimelineID,Pair{Next Activity Object,edit distance}
		LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActObjs = new LinkedHashMap<>();
		// ArrayList<Triple<ActivityObject, Double, String>> topActivityObjects = new ArrayList<>();
		// Triple <Next Activity Object,edit distance, TimelineID>

		try
		{
			// Disabled on Aug 2 2017 as its just logging
			if (VerbosityConstants.verbose)
			{
				if (editDistanceSortedFullCand.size() < 5)
				{
					System.err.println("\nWarning: #cands = editDistanceSortedFullCand.size() ="
							+ editDistanceSortedFullCand.size() + "<5");
					// errorExists = true;
				}
			}

			for (Map.Entry<String, Pair<String, Double>> candDistEntry : editDistanceSortedFullCand.entrySet())
			{
				String candID = candDistEntry.getKey();
				Double editDistanceForCandidate = candDistEntry.getValue().getSecond();

				TimelineWithNext candidateTimeline = (TimelineWithNext) candidateTimelines.get(candID);
				ActivityObject2018 nextActivityObjectForCand = candidateTimeline.getNextActivityObject();

				if (candidateTimeline.size() <= 0)
				{
					System.err.println(PopUps.getTracedErrorMsg("Error :candID=" + candID
							+ " not found, thus candidateTimeline.size=" + candidateTimeline.size()));
				}
				else if (nextActivityObjectForCand == null)
				{
					System.err.println(PopUps.getTracedErrorMsg("Error nextActivityObjectForCand == null"));
				}

				nextActObjs.put(candID,
						new Pair<ActivityObject2018, Double>(nextActivityObjectForCand, editDistanceForCandidate));
				// topActivityObjects.add(new Triple<ActivityObject, Double, String>(
				// simCandidateTimeline.getNextActivityObject(), editDistanceForSimCandidate, simCandidateID));
				// take the next activity object (next activity object is the valid next activity object)
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		if (nextActObjs == null || nextActObjs.size() == 0)
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: nextActObjs.size() = ") + nextActObjs.size());
		}
		// this.topNextActivityObjects = topActivityObjects;
		return nextActObjs;
	}

	/**
	 * 
	 * @param activitiesGuidingRecomm
	 * @param distanceScoresSorted
	 * @param dayTimelinesForUser
	 * @return
	 */
	public static LinkedHashMap<String, Pair<ActivityObject2018, Double>> fetchNextActivityObjectsDaywise(
			LinkedHashMap<String, Pair<String, Double>> editDistanceSorted,
			LinkedHashMap<String, Timeline> candidateTimelines, LinkedHashMap<String, Integer> endPointIndices)
	{
		// System.out.println("\n-----------------Inside fetchNextActivityObjectsDaywise");
		LinkedHashMap<String, Pair<ActivityObject2018, Double>> nextActObjs = new LinkedHashMap<>();

		if (editDistanceSorted.size() < 5)
		{
			System.err.println("\nWarning: # candidate timelines =" + editDistanceSorted.size() + "<5");
		}

		try
		{
			for (Map.Entry<String, Pair<String, Double>> candDistEntry : editDistanceSorted.entrySet())
			{
				String timelineID = candDistEntry.getKey();
				Double distanceOfCandTimeline = candDistEntry.getValue().getSecond();

				Timeline candUserDayTimeline = candidateTimelines.get(timelineID);
				int endPointIndexInCand = endPointIndices.get(timelineID);
				// TimelineUtils.getUserDayTimelineByDateFromMap(dayTimelinesForUser, dateOfCandTimeline);
				if (candUserDayTimeline == null)
				{
					System.err.println(PopUps.getTracedErrorMsg("Error: candUserDayTimeline is null"));
					System.exit(-1);
				}
				if (!candUserDayTimeline.isShouldBelongToSingleDay())
				{
					System.err.println(PopUps.getTracedErrorMsg(
							"Error: for daytimeline candUserDayTimeline.isShouldBelongToSingleDay()= "
									+ candUserDayTimeline.isShouldBelongToSingleDay()));
					System.exit(-1);
				}
				if (endPointIndexInCand < 0)
				{
					System.err.println(PopUps
							.getTracedErrorMsg("Error: for daytimeline endPointIndexInCand=" + endPointIndexInCand));
					System.exit(-1);
				}

				ActivityObject2018 nextValidAO = candUserDayTimeline
						.getNextValidActivityAfterActivityAtThisPositionPD(endPointIndexInCand);
				nextActObjs.put(timelineID, new Pair<ActivityObject2018, Double>(nextValidAO, distanceOfCandTimeline));

				if (VerbosityConstants.verbose)
				{
					System.out.println("timelineID=" + timelineID + " endPointIndexInCand =" + endPointIndexInCand);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		if (nextActObjs == null || nextActObjs.size() == 0)
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: nextActObjs.size() = ") + nextActObjs.size());
		}

		// System.out.println("-------exiting fetchNextActivityObjectsDaywise\n");
		return nextActObjs;
	}

	/**
	 * Fetches the next Activity Objects with their edit distance from the candidate timelines (wrt Current Timeline)
	 * 
	 * 
	 * @param editDistanceSorted
	 * @param candidateTimelines
	 * @param lookPastType
	 * @param endPointIndicesForDaywise
	 *            only used for daywise approach
	 * @return TimelineID,Pair{Next Activity Object,edit distance}
	 */
	public static LinkedHashMap<String, Pair<ActivityObject2018, Double>> fetchNextActivityObjects(
			LinkedHashMap<String, Pair<String, Double>> editDistanceSorted,
			LinkedHashMap<String, Timeline> candidateTimelines, Enums.LookPastType lookPastType,
			LinkedHashMap<String, Integer> endPointIndicesForDaywise)
	{

		switch (lookPastType)
		{
			case Daywise:
				return fetchNextActivityObjectsDaywise(editDistanceSorted, candidateTimelines,
						endPointIndicesForDaywise);
			case NCount:
				return fetchNextActivityObjectsFromNext(editDistanceSorted, candidateTimelines);
			case NHours:
				return fetchNextActivityObjectsFromNext(editDistanceSorted, candidateTimelines);
			case ClosestTime:
				return null;
			case NGram:
				return fetchNextActivityObjectsFromNext(editDistanceSorted, candidateTimelines);
			default:
				System.err.println(PopUps.getTracedErrorMsg("Error:unrecognised lookPastType = " + lookPastType));
				return null;
		}

	}

	public static ArrayList<Integer> getIndicesOfEndPointActivityInDayButNotAsLast(String userDayActivitiesAsStringCode,
			String codeOfEndPointActivity)
	{
		// System.out.println("\nDebug getIndicesOfEndPointActivityInDayButNotAsLast:
		// userDayActivitiesAsStringCode="+userDayActivitiesAsStringCode+" and
		// codeOfEndPointActivity="+codeOfEndPointActivity);
		ArrayList<Integer> indicesOfEndPointActivityInDay = new ArrayList<Integer>();

		int index = userDayActivitiesAsStringCode.indexOf(codeOfEndPointActivity);

		while (index >= 0)
		{
			// System.out.println(index);
			if (index != (userDayActivitiesAsStringCode.length() - 1)) // not last index
			{
				indicesOfEndPointActivityInDay.add(index);
			}
			index = userDayActivitiesAsStringCode.indexOf(codeOfEndPointActivity, index + 1);
		}
		return indicesOfEndPointActivityInDay;
	}

	public static ArrayList<Integer> getIndicesOfEndPointActivityInTimeline(String userActivitiesAsStringCode,
			String codeOfEndPointActivity)
	{
		// System.out.println("\nDebug getIndicesOfEndPointActivityInDayButNotAsLast:
		// userDayActivitiesAsStringCode="+userDayActivitiesAsStringCode+" and
		// codeOfEndPointActivity="+codeOfEndPointActivity);
		ArrayList<Integer> indicesOfEndPointActivityInTimeline = new ArrayList<Integer>();

		int index = userActivitiesAsStringCode.indexOf(codeOfEndPointActivity);

		while (index >= 0)
		{
			indicesOfEndPointActivityInTimeline.add(index);
			index = userActivitiesAsStringCode.indexOf(codeOfEndPointActivity, index + 1);
		}
		return indicesOfEndPointActivityInTimeline;
	}

	/**
	 */
	public static long getSumOfActivityObjects(LinkedHashMap<Integer, Timeline> map)
	{
		long count = 0;

		for (Map.Entry<Integer, Timeline> entry : map.entrySet())
		{
			int a = entry.getValue().countNumberOfValidActivities();
			int b = entry.getValue().size();

			if (a != b)
			{
				PopUps.showError(
						"Error in getSumOfActivityObjects a should be equal to be since we removed invalid aos beforehand but a = "
								+ a + " and b=" + b);
			}
			count += a;
		}
		return count;
	}

	public static LinkedHashMap<String, Double> removeRecommPointActivityFromRankedRecomm(
			LinkedHashMap<String, Double> recommendedActivityNamesWithRankscores, String activityNameAtRecommPoint)
	{
		// String activityNameAtRecommPoint = activityAtRecommPoint.getActivityName();
		System.out.println("removeRecommPointActivityFromRankedRecomm called");
		Double d = recommendedActivityNamesWithRankscores.remove(activityNameAtRecommPoint);
		if (d == null)
		{
			System.out.println("Note: removeRecommPointActivityFromRankedRecomm: curr act not in recommendation");
		}
		return recommendedActivityNamesWithRankscores;
	}

	/**
	 * 
	 * @param wtscorerecommsbylocproximity
	 * @param recommendedActivityNamesWithRankscores
	 * @return
	 * @since Feb 20 2018
	 */
	public static LinkedHashMap<String, Double> reScoreRecommsIncludingLocationProximity(
			double wtscorerecommsbylocproximity, LinkedHashMap<String, Double> recommendedActivityNamesWithRankscores)
	{
		// TODO Auto-generated method stub
		return null;
	}

	public static String getStringCodeOfActivityObjects(ArrayList<ActivityObject2018> activityObjects)
	{
		StringBuilder code = new StringBuilder();
		for (ActivityObject2018 ao : activityObjects)
		{
			code.append(ao.getCharCodeFromActID());
		}
		return code.toString();
	}

	// /////////////////////////////////////////////////////////////////////
	/**
	 * Generate the string: '__recommendedActivityName1:simRankScore1__recommendedActivityName2:simRankScore2'
	 * 
	 * @param recommendedActivityPDValRankscorePairs
	 */
	protected static String getRankedRecommendedActivityPDvalsWithRankScoresString(
			LinkedHashMap<String, Double> recommendedActivityPDValRankscorePairs)
	{
		StringBuilder topRankedString = new StringBuilder();// String topRankedString= new String();
		StringBuilder msg = new StringBuilder();
		for (Map.Entry<String, Double> entry : recommendedActivityPDValRankscorePairs.entrySet())
		{
			String recommAct = entry.getKey();
			double roundedRankScore = Evaluation.round(entry.getValue(), 4);
			topRankedString.append("__" + recommAct + ":" + roundedRankScore);
			msg.append("recomm act:" + recommAct + ", rank score: " + roundedRankScore + "\n");
			// topRankedString+= "__"+entry.getKey()+":"+TestStats.round(entry.getValue(),4);
		}
		if (VerbosityConstants.verboseRankScoreCalcToConsole)
		{
			System.out.println(msg.toString() + "\n");
		}
		return topRankedString.toString();
	}

	// /////////////////////////////////////////////////////////////////////
	/**
	 * Generate string as '__recommendedActivityName1__recommendedActivityName2'
	 * 
	 * @param recommendedActivityNameRankscorePairs
	 * @return
	 */
	protected static String getRankedRecommendedActivityPDValsithoutRankScoresString(
			LinkedHashMap<String, Double> recommendedActivityNameRankscorePairs)
	{
		StringBuilder rankedRecommendationWithoutRankScores = new StringBuilder();
		for (Map.Entry<String, Double> entry : recommendedActivityNameRankscorePairs.entrySet())
		{
			rankedRecommendationWithoutRankScores.append("__" + entry.getKey());
		}
		return rankedRecommendationWithoutRankScores.toString();
	}

	/**
	 * 
	 * @param normalisedCandEditDistances
	 * @return
	 */
	public static LinkedHashMap<String, Pair<String, Double>> aggregatedFeatureWiseDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> normalisedCandEditDistances)
	{
		LinkedHashMap<String, Pair<String, Double>> aggregatedFeatureWiseDistances = new LinkedHashMap<>();
	
		for (Map.Entry<String, LinkedHashMap<String, Pair<String, Double>>> entry : normalisedCandEditDistances
				.entrySet()) // iterating over cands
		{
			String candID = entry.getKey();
			LinkedHashMap<String, Pair<String, Double>> normalisedFeatureWiseDistances = entry.getValue();
	
			int featureIndex = 0;
			double distanceAggregatedOverFeatures = 0;
	
			for (Map.Entry<String, Pair<String, Double>> distEntry : normalisedFeatureWiseDistances.entrySet())
			// iterating over distance for each feature
			{
				double normalisedDistanceValue = distEntry.getValue().getSecond();
				distanceAggregatedOverFeatures += normalisedDistanceValue;
				featureIndex++;
			}
	
			distanceAggregatedOverFeatures = StatsUtils
					.round(distanceAggregatedOverFeatures / Constant.getNumberOfFeatures(), 4);
			aggregatedFeatureWiseDistances.put(candID, new Pair("", distanceAggregatedOverFeatures));
		}
		return aggregatedFeatureWiseDistances;
	}

}