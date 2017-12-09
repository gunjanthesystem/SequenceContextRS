package org.activity.recomm;

//import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.activity.constants.Constant;
import org.activity.constants.Enums;
import org.activity.constants.Enums.CaseType;
import org.activity.constants.Enums.LookPastType;
import org.activity.constants.Enums.TypeOfThreshold;
import org.activity.constants.VerbosityConstants;
import org.activity.distances.AlignmentBasedDistance;
import org.activity.distances.FeatureWiseEditDistance;
import org.activity.distances.FeatureWiseWeightedEditDistance;
import org.activity.distances.HJEditDistance;
import org.activity.distances.OTMDSAMEditDistance;
import org.activity.evaluation.Evaluation;
import org.activity.io.EditDistanceMemorizer;
import org.activity.io.WritingToFile;
import org.activity.objects.ActivityObject;
import org.activity.objects.Pair;
import org.activity.objects.Timeline;
import org.activity.objects.TimelineWithNext;
import org.activity.objects.Triple;
import org.activity.spmf.SeqPredictor;
import org.activity.stats.StatsUtils;
import org.activity.ui.PopUps;
import org.activity.util.ComparatorUtils;
import org.activity.util.DateTimeUtils;
import org.activity.util.RegexUtils;
import org.activity.util.StringUtils;
import org.activity.util.TimelineExtractors;
import org.activity.util.TimelineTransformers;
import org.activity.util.TimelineUtils;
import org.activity.util.UtilityBelt;

/**
 * 
 * <p>
 * Fork of org.activity.recomm.RecommendationMasterMar2017GenSeq.RecommendationMasterMar2017GenSeq() for implementing
 * alternative sequence prediction algorithms
 * <p>
 * ALERT!: DO NOT USER THRESHOLDING WITH THIS CLASS since here the edit distances are normalised before thredholding,
 * however, they should have been normalised after thresholding as was the case in iiWAS version of the code This
 * generates recommendations using the matching-unit (MU) approach. Timelines are not viewed as Day Timeline but rather
 * as continuous timelines. The matching-unit can be specified as 'hours' or 'counts'.
 * 
 * Timelines are sets of Activity Objects, (and they do not necessarily belong to one day.) (the matching unit used here
 * HJ version) In this version we calculate edit distance of the full candidate and not the least distant subcandidate
 * 
 * @since 02 May, 2017
 * @author gunjan
 */
public class RecommendationMasterMar2017AltAlgoSeq implements RecommendationMasterI// IRecommenderMaster
{
	private double matchingUnitInCountsOrHours;
	private double reductionInMatchingUnit = 0;

	// private Timeline trainingTimeline;
	// private Timeline testTimeline;
	// in case of MU approach {String, TimelineWithNext}, in case if daywise approach {Date as String, Timeline}
	private LinkedHashMap<String, Timeline> candidateTimelines;
	// here key is the TimelineID, which is already a class variable of Value,
	// So we could have used ArrayList but we used LinkedHashMap purely for search performance reasons

	private Date dateAtRecomm;
	private Time timeAtRecomm;
	private String userAtRecomm;
	private String userIDAtRecomm;
	private LinkedHashMap<String, String> candUserIDs;
	/**
	 * Current Timeline sequence of activity objects happening from the recomm point back until the matching unit
	 */
	private TimelineWithNext currentTimeline; // =current timelines
	private ArrayList<ActivityObject> activitiesGuidingRecomm; // Current Timeline ,
	private ActivityObject activityObjectAtRecommPoint; // current Activity Object
	// private String activityNameAtRecommPoint;// current Activity Name
	private ArrayList<Integer> primaryDimensionValAtRecommPoint;// when activity is primary dimension, this is an
																// activity id, when
	// location is the primary dimension, this is a list of location ids
	// (>1 if this is a merged object)
	/**
	 * {Cand TimelineID, Pair{Trace,Edit distance}} this LinkedHashMap is sorted by the value of edit distance in
	 * ascending order://formerly: editDistancesSortedMapFullCand
	 */
	private LinkedHashMap<String, Pair<String, Double>> distancesSortedMap;

	/**
	 * Relevant for daywise approach: the edit distance is computed for least distance subsequence in case current
	 * activity name occurs at multiple times in a day timeline {Cand TimelineID, End point index of least distant
	 * subsequence}
	 */
	private LinkedHashMap<String, Integer> endPointIndicesConsideredInCands;

	// private LinkedHashMap<String, ActivityObject> endPointActivityObjectsInCands;

	/*
	 * List of of top next activity objects with their edit distances and the timeline id of the candidate producing
	 * them. Triple <Next Activity Object,edit distance, TimelineID> formerly ArrayList<Triple<ActivityObject, Double,
	 * String>> nextActivityObjectsFromCands;// topNextActivityObjects
	 */
	/**
	 * List of of top next activity objects with their edit distances and the timeline id of the candidate producing
	 * them. {TimelineID, {Next Activity Object,edit distance}}
	 */
	private LinkedHashMap<String, Pair<ActivityObject, Double>> nextActivityObjectsFromCands;

	/**
	 * This is only relevant when case type is 'CaseBasedV1' (Cand TimeineId, Edit distance of the end point activity
	 * object of this candidate timeline with the current activity object(activity at recomm point))
	 * <p>
	 * <font color = orange>currently its a similarity and not edit distance</font>
	 */
	private LinkedHashMap<String, Double> similarityOfEndPointActivityObjectCand;

	/**
	 * Recommended Activity names with their rank score
	 */
	private LinkedHashMap<String, Double> recommendedActivityNamesWithRankscores;
	private String rankedRecommendedActNamesWithRankScoresStr;
	private String rankedRecommendedActNamesWithoutRankScoresStr;

	private boolean hasCandidateTimelines, hasCandidateTimelinesBelowThreshold;

	private boolean nextActivityJustAfterRecommPointIsInvalid;
	private double thresholdAsDistance = Double.MAX_VALUE;

	// private int fullCandOrSubCand; //0 means full cand .... 1 means subcand
	private boolean thresholdPruningNoEffect;

	public Enums.LookPastType lookPastType;// String
	// public int totalNumberOfProbableCands; // public int numCandsRejectedDueToNoCurrentActivityAtNonLast;
	// public int numCandsRejectedDueToNoNextActivity;
	// candidateTimelinesStatus; //1 for has candidate timelines, -1 for no candidate timelines because no past timeline
	// with current act, -2 for no candodate timelines because

	Enums.CaseType caseType; // String 'SimpleV3' for no case case, 'CaseBasedV1' for first cased based implementation

	// public static String commonPath ;//= Constant.commonPath;

	private boolean errorExists;

	private ArrayList<Double> normEditSimilarity, simEndActivityObjForCorr;
	// public double percentageDistanceThresh;
	/*
	 * threshold for choosing candidate timelines, those candidate timelines whose distance from the 'activity objects
	 * guiding recommendations' is higher than the cost of replacing 'percentageDistanceThresh' % of Activity Objects in
	 * the activities guiding recommendation are pruned out from set of candidate timelines
	 */

	HJEditDistance hjEditDistance = null;
	AlignmentBasedDistance alignmentBasedDistance = null;
	FeatureWiseEditDistance featureWiseEditDistance = null;
	FeatureWiseWeightedEditDistance featureWiseWeightedEditDistance = null;
	OTMDSAMEditDistance OTMDSAMEditDistance = null;

	/**
	 * Score (A<sub>O</sub>) = ∑ { 1- min( 1, |Stcand - RT|/60mins) }
	 **/
	private static final double timeInSecsForRankScoreNormalisation = 60 * 60; // 60 mins

	/**
	 * NOT BEING USED AT THE MOMENT
	 */
	EditDistanceMemorizer editDistancesMemorizer;

	/**
	 * 
	 * @return
	 */
	private final int initialiseDistancesUsed(String dname)
	{
		alignmentBasedDistance = new AlignmentBasedDistance(); // used for case based similarity
		//
		switch (dname)
		{
			case "HJEditDistance":
				hjEditDistance = new HJEditDistance();
				break;

			case "FeatureWiseEditDistance":
				featureWiseEditDistance = new FeatureWiseEditDistance();
				break;

			case "FeatureWiseWeightedEditDistance":
				featureWiseWeightedEditDistance = new FeatureWiseWeightedEditDistance();
				break;

			case "OTMDSAMEditDistance":
				OTMDSAMEditDistance = new OTMDSAMEditDistance();
				break;

			default:
				PopUps.showError(
						"Error in org.activity.recomm.RecommendationMasterMU.initialiseDistanceUsed(): Unknown distance specified:"
								+ dname);
				System.err.println(PopUps.getTracedErrorMsg(
						"Error in org.activity.recomm.RecommendationMasterMU.initialiseDistanceUsed(): Unknown distance specified:"
								+ dname));
				System.exit(-1);
		}
		return 0;
	}

	public LinkedHashMap<String, String> getCandUserIDs()
	{
		return candUserIDs;
	}

	/**
	 * 
	 * @param trainingTimelines
	 * @param testTimelines
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param userAtRecomm
	 * @param thresholdVal
	 * @param typeOfThreshold
	 * @param dummy
	 * @param actObjsToAddToCurrentTimeline
	 * @param trainTestTimelinesForAllUsers
	 * @param trainTimelinesAllUsersContinuous
	 */
	public RecommendationMasterMar2017AltAlgoSeq(LinkedHashMap<Date, Timeline> trainingTimelines,
			LinkedHashMap<Date, Timeline> testTimelines, String dateAtRecomm, String timeAtRecomm, int userAtRecomm,
			double thresholdVal, Enums.TypeOfThreshold typeOfThreshold,
			/*
			 * double matchingUnitInCountsOrHours, *Enums.CaseType caseType
			 */ Enums.LookPastType lookPastType, boolean dummy, ArrayList<ActivityObject> actObjsToAddToCurrentTimeline,
			LinkedHashMap<String, List<LinkedHashMap<Date, Timeline>>> trainTestTimelinesForAllUsers,
			LinkedHashMap<String, Timeline> trainTimelinesAllUsersContinuous)
	{
		// PopUps.showMessage("called RecommendationMasterMar2017GenSeq");
		try
		{
			System.out.println(
					"\n-----------Starting RecommendationMasterMar2017AltAlgoSeq " + lookPastType + "-------------");

			String performanceFileName = Constant.getCommonPath() + "Performance.csv";
			long recommMasterT0 = System.currentTimeMillis();

			// initialiseDistancesUsed(Constant.getDistanceUsed());
			// editDistancesMemorizer = new EditDistanceMemorizer();
			this.lookPastType = lookPastType;
			// this.caseType = caseType;

			// if (!lookPastType.equals(LookPastType.Daywise) && !lookPastType.equals(LookPastType.ClosestTime))
			// {
			// this.matchingUnitInCountsOrHours = matchingUnitInCountsOrHours;
			// }
			// else if (lookPastType.equals(LookPastType.NGram))
			// {
			// this.matchingUnitInCountsOrHours = 0;
			// }

			errorExists = false;

			this.hasCandidateTimelines = true;
			this.nextActivityJustAfterRecommPointIsInvalid = false;

			// dd/mm/yyyy // okay java.sql.Date with no hidden time
			this.dateAtRecomm = DateTimeUtils.getDateFromDDMMYYYY(dateAtRecomm, RegexUtils.patternForwardSlash);
			this.timeAtRecomm = Time.valueOf(timeAtRecomm);
			this.userAtRecomm = Integer.toString(userAtRecomm);
			this.userIDAtRecomm = Integer.toString(userAtRecomm);
			System.out.println("	User at Recomm = " + this.userAtRecomm + "\tDate at Recomm = " + this.dateAtRecomm
					+ "\tTime at Recomm = " + this.timeAtRecomm + "\n");// this.matchingUnitInCountsOrHours="
			// + this.matchingUnitInCountsOrHours);/

			//////
			Pair<TimelineWithNext, Double> extCurrTimelineRes = null;

			if (actObjsToAddToCurrentTimeline.size() > 0)
			{
				extCurrTimelineRes = TimelineExtractors.extractCurrentTimelineSeq(testTimelines, lookPastType,
						this.dateAtRecomm, this.timeAtRecomm, this.userIDAtRecomm, this.matchingUnitInCountsOrHours,
						actObjsToAddToCurrentTimeline);
			}
			else
			{
				extCurrTimelineRes = TimelineExtractors.extractCurrentTimeline(testTimelines, lookPastType,
						this.dateAtRecomm, this.timeAtRecomm, this.userIDAtRecomm, this.matchingUnitInCountsOrHours);
			}

			this.currentTimeline = extCurrTimelineRes.getFirst();
			this.reductionInMatchingUnit = extCurrTimelineRes.getSecond();
			//////

			this.activitiesGuidingRecomm = currentTimeline.getActivityObjectsInTimeline(); // CURRENT TIMELINE

			// if (actObjsToAddToCurrentTimeline.size() > 0 && this.lookPastType == LookPastType.NCount)
			// {
			// if (reductionInMatchingUnit == 0 && activitiesGuidingRecomm.size() <= matchingUnitInCountsOrHours)
			// {
			// System.err.println("Error: actsGuidingRecomm.size():" + this.activitiesGuidingRecomm.size() + "<=MU"
			// + matchingUnitInCountsOrHours + " (even without reduced mu");
			// }
			// else if (activitiesGuidingRecomm.size() <= matchingUnitInCountsOrHours)
			// {
			// System.out.println("Warning: actsGuidingRecomm.size():" + this.activitiesGuidingRecomm.size()
			// + "<=MU" + matchingUnitInCountsOrHours + " (with mu reduction =" + reductionInMatchingUnit
			// + ")");
			// }
			// }

			this.activityObjectAtRecommPoint = activitiesGuidingRecomm.get(activitiesGuidingRecomm.size() - 1);
			// this.activityNameAtRecommPoint = this.activityAtRecommPoint.getActivityName();
			this.primaryDimensionValAtRecommPoint = this.activityObjectAtRecommPoint.getPrimaryDimensionVal();

			// sanity check start
			if (actObjsToAddToCurrentTimeline.size() > 0)
			{
				if (primaryDimensionValAtRecommPoint.equals(actObjsToAddToCurrentTimeline
						.get(actObjsToAddToCurrentTimeline.size() - 1).getPrimaryDimensionVal()) == false)
				{
					// System.err.println("Error act name of actAtRecommPoint and last act in acts to add do not match:
					// activityNameAtRecommPoint= " + activityNameAtRecommPoint + " last act in acts to add = "
					// + actObjsToAddToCurrentTimeline.get(actObjsToAddToCurrentTimeline.size() - 1)
					// .getActivityName());
					System.err.println(
							"Error primary dimension vals of actAtRecommPoint and last act in acts to add do not match: primaryDimensionValAtRecommPoint= "
									+ primaryDimensionValAtRecommPoint + " last act in acts to add = "
									+ actObjsToAddToCurrentTimeline.get(actObjsToAddToCurrentTimeline.size() - 1)
											.getPrimaryDimensionVal());
				}
			}
			// sanity check end
			// //////////////////////////
			long recommMasterT1 = System.currentTimeMillis();

			this.candidateTimelines = TimelineExtractors.extractCandidateTimelinesV2(trainingTimelines, lookPastType,
					this.dateAtRecomm, /* this.timeAtRecomm, */ this.userIDAtRecomm, matchingUnitInCountsOrHours,
					this.activityObjectAtRecommPoint, trainTestTimelinesForAllUsers, trainTimelinesAllUsersContinuous);

			// if (VerbosityConstants.verbose)
			{
				System.out.println("Inside recomm master :trainTestTimelinesForAllUsers.size()= "
						+ trainTestTimelinesForAllUsers.size() + " trainTimelinesAllUsersContinuous.size()="
						+ trainTimelinesAllUsersContinuous.size() + "candidateTimelines.size()= "
						+ candidateTimelines.size());
			}

			long recommMasterT2 = System.currentTimeMillis();
			long timeTakenToFetchCandidateTimelines = recommMasterT2 - recommMasterT1;

			candUserIDs = extractCandUserIDs(candidateTimelines);
			// ///////////////////////////
			if (VerbosityConstants.verbose)
			{
				System.out.println("activitiesGuidingRecomm.size()=" + activitiesGuidingRecomm.size()
						+ " matchingUnitInCountsOrHours=" + matchingUnitInCountsOrHours + " activityAtRecommPoint = "
						+ activityObjectAtRecommPoint.getActivityName());

				System.out
						.println("Current timeline: " + currentTimeline.getActivityObjectNamesWithTimestampsInSequence()
								+ "; activitiesGuidingRecomm.size =" + this.activitiesGuidingRecomm.size());
				// System.out.println("\nActivity at Recomm point (Current Activity) =" + activityNameAtRecommPoint);
				System.out.println("\nprimaryDimensionValAtRecommPoint at Recomm point (Current Activity) ="
						+ primaryDimensionValAtRecommPoint);

				System.out.println("Number of candidate timelines =" + candidateTimelines.size());
				System.out.println("the candidate timelines are as follows:");
				candidateTimelines.entrySet().stream()
						.forEach(t -> System.out.println(t.getValue().getPrimaryDimensionValsInSequence()));
				// getActivityObjectNamesInSequence()));
			}

			if (candidateTimelines.size() == 0)
			{
				System.out.println("Warning: not making recommendation for " + userAtRecomm + " on date:" + dateAtRecomm
						+ " at time:" + timeAtRecomm + "  because there are no candidate timelines");
				// this.singleNextRecommendedActivity = null;
				this.hasCandidateTimelines = false;
				// this.topNextActivities =null;
				this.nextActivityObjectsFromCands = null;
				this.thresholdPruningNoEffect = true;
				return;
			}
			else
			{
				this.hasCandidateTimelines = true;
			}
			// System.out.println("\nDebug note192_223: getActivityNamesGuidingRecommwithTimestamps() " +
			// getActivityNamesGuidingRecommwithTimestamps() +
			// " size of current timeline="
			// + currentTimeline.getActivityObjectsInTimeline().size());
			// /////////////////////
			// TODO CHECK: HOW THE EFFECT OF THIS DIFFERS FROM THE EXPERIMENTS DONE FOR IIWAS: in iiWAS normalisation
			// was
			// after thresholding (correct), here
			// normalisation is before thresholding which should be changed
			long recommMasterT3 = System.currentTimeMillis();
			Pair<LinkedHashMap<String, Pair<String, Double>>, LinkedHashMap<String, Integer>> normalisedDistFromCandsRes = null;
			// Curtain not relevant 1 start
			// = getNormalisedDistancesForCandidateTimelines(
			// candidateTimelines, activitiesGuidingRecomm, caseType, this.userIDAtRecomm, this.dateAtRecomm,
			// this.timeAtRecomm, Constant.getDistanceUsed(), this.lookPastType, this.hjEditDistance,
			// this.featureWiseEditDistance, this.featureWiseWeightedEditDistance, this.OTMDSAMEditDistance,
			// this.editDistancesMemorizer);
			// Curtain not relevant 1 end
			// editDistancesMemorizer.serialise(this.userIDAtRecomm);

			LinkedHashMap<String, Pair<String, Double>> distancesMapUnsorted = null;
			// curtain not relevant: normalisedDistFromCandsRes.getFirst();
			this.endPointIndicesConsideredInCands = null;// curtain not
															// relevant:normalisedDistFromCandsRes.getSecond();

			long recommMasterT4 = System.currentTimeMillis();
			long timeTakenToComputeNormEditDistances = recommMasterT4 - recommMasterT3;

			// System.out.println("\nDebug note192_229: getActivityNamesGuidingRecommwithTimestamps() " +
			// getActivityNamesGuidingRecommwithTimestamps() +" size of current timeline=" +
			// currentTimeline.getActivityObjectsInTimeline().size());
			// ########Sanity check
			// curtain not relevant: start
			// if (distancesMapUnsorted.size() != candidateTimelines.size())
			// {
			// if (Constant.filterTopCands > 0) // not expected when filtering is to be done
			// {
			// System.out.println("Alert: editDistancesMapUnsorted.size() (" + distancesMapUnsorted.size()
			// + ") != candidateTimelines.size() (" + candidateTimelines.size() + ")");
			// }
			// else
			// {
			// PopUps.printTracedErrorMsg("editDistancesMapUnsorted.size() (" + distancesMapUnsorted.size()
			// + ") != candidateTimelines.size() (" + candidateTimelines.size() + ")");
			// String distancesMapUnsortedAsString = distancesMapUnsorted.entrySet().stream()
			// .map(e -> e.getKey() + " - " + e.getValue().getFirst() + "_" + e.getValue().getSecond())
			// .collect(Collectors.joining("\n"));
			// String candidateTimelinesAsString = candidateTimelines.entrySet().stream().map(
			// e -> e.getKey() + " - " + e.getValue().getActivityObjectPDValsWithTimestampsInSequence())
			// // .getActivityObjectNamesWithTimestampsInSequence())
			// .collect(Collectors.joining("\n"));
			//
			// WritingToFile.appendLineToFileAbsolute(
			// "User = " + this.userIDAtRecomm + "\ndistancesMapUnsortedAsString =\n"
			// + distancesMapUnsortedAsString + "\n\n candidateTimelinesAsString =\n"
			// + candidateTimelinesAsString,
			// Constant.getCommonPath() + "ErrorLog376distancesMapUnsorted.txt");
			// errorExists = true;
			// }
			// }
			// curtain not relevant: end
			// ##############

			// curtain not relevant 2: start
			// // /// REMOVE candidate timelines which are above the distance THRESHOLD. (actually here we remove the
			// entry
			// // for such candidate timelines from the distance scores map. // no pruning for baseline closest ST
			// if (this.lookPastType.equals(Enums.LookPastType.ClosestTime) == false && Constant.useThreshold == true)
			// {// changed from "Constant.useThreshold ==false)" on May 10 but should not affect result since we were
			// not
			// // doing thresholding anyway
			// Triple<LinkedHashMap<String, Pair<String, Double>>, Double, Boolean> prunedRes = pruneAboveThreshold(
			// distancesMapUnsorted, typeOfThreshold, thresholdVal, activitiesGuidingRecomm);
			// distancesMapUnsorted = prunedRes.getFirst();
			// this.thresholdAsDistance = prunedRes.getSecond();
			// this.thresholdPruningNoEffect = prunedRes.getThird();
			// }
			// // curtain not relevant: end
			// // ////////////////////////////////
			//
			// if (distancesMapUnsorted.size() == 0)
			// {
			// System.out.println("Warning: No candidate timelines below threshold distance");
			// hasCandidateTimelinesBelowThreshold = false;
			// return;
			// }
			// else
			// {
			hasCandidateTimelinesBelowThreshold = true;
			// }
			//
			// // Is this sorting necessary?
			// // Disabling on Aug 3
			// if (Constant.filterTopCands <= 0) // because otherwise already sorted while filtering
			// {
			// distancesSortedMap = (LinkedHashMap<String, Pair<String, Double>>) ComparatorUtils
			// .sortByValueAscendingStrStrDoub(distancesMapUnsorted);
			// }
			// else
			// {// because already sorted while filtering
			// distancesSortedMap = distancesMapUnsorted;
			// }
			// if (caseType.equals(Enums.CaseType.CaseBasedV1))
			// {
			// System.out.println("this is CaseBasedV1");
			// this.similarityOfEndPointActivityObjectCand = getCaseSimilarityEndPointActivityObjectCand(
			// candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm,
			// this.dateAtRecomm.toString(), this.timeAtRecomm.toString(), alignmentBasedDistance);//
			// getDistanceScoresforCandidateTimelines(candidateTimelines,activitiesGuidingRecomm);
			// }
			//
			// this.nextActivityObjectsFromCands = fetchNextActivityObjects(distancesSortedMap, candidateTimelines,
			// this.lookPastType, endPointIndicesConsideredInCands);
			//
			// if (!this.lookPastType.equals(Enums.LookPastType.ClosestTime))
			// {
			// if (!Sanity.eq(this.nextActivityObjectsFromCands.size(), distancesSortedMap.size(),
			// "Error at Sanity 349 (RecommenderMaster: this.topNextActivityObjects.size()"
			// + nextActivityObjectsFromCands.size() + "!= distancesSortedMap.size():"
			// + distancesSortedMap.size()))
			// {
			// errorExists = true;
			// }
			// // Disabled logging for performance
			// // System.out.println("this.nextActivityObjectsFromCands.size()= " +
			// // this.nextActivityObjectsFromCands.size()
			// // + "\ndistancesSortedMap.size()=" + distancesSortedMap.size()
			// // + "\nthis.candidateTimelines.size()=" + this.candidateTimelines.size());
			//
			// // this will not be true when thresholding
			// if (this.thresholdPruningNoEffect)
			// {
			// if (Constant.filterTopCands <= 0) // this sanity check is only valid when not filtering cands
			// {
			// if (!Sanity.eq(distancesSortedMap.size(), this.candidateTimelines.size(),
			// "Error at Sanity 349 (RecommenderMaster: editDistancesSortedMapFullCand.size()==
			// this.candidateTimelines.size() not satisfied"))
			// {
			// errorExists = true;
			// }
			// }
			// }
			//
			// }
			// else if (this.lookPastType.equals(Enums.LookPastType.ClosestTime))
			// {
			// this.nextActivityObjectsFromCands = new LinkedHashMap<>();
			// }
			// // curtain not relevant 2: end
			// //Start of not relevant curtain
			// if (VerbosityConstants.verbose)
			// {
			// System.out.println("---------editDistancesSortedMap.size()=" + distancesSortedMap.size());
			//
			// StringBuilder sbToWrite1 = new StringBuilder(
			// ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" + "\n lookPastType:" + lookPastType
			// + "\n The candidate timelines in increasing order of distance are:\n");
			// distancesSortedMap.entrySet().stream()
			// .forEach(e -> sbToWrite1.append("candID:" + e.getKey() + " dist:" + e.getValue().getSecond()
			// + "\n acts:" + candidateTimelines.get(e.getKey()).getPrimaryDimensionValsInSequence()
			// // .getActivityObjectNamesInSequence()
			// + "\n"));
			// sbToWrite1.append("Top next activities are:\n");// +this.topNextRecommendedActivities);
			// nextActivityObjectsFromCands.entrySet().stream()
			// .forEach(e -> sbToWrite1.append(" >>" + e.getValue().getFirst().getPrimaryDimensionVal()//
			// .getActivityName()
			// + ":" + e.getValue().getSecond()));
			// System.out.println(sbToWrite1.toString());
			//
			// System.out.println("\nDebug note192_end: getActivityNamesGuidingRecommwithTimestamps() "
			// + getActivityNamesGuidingRecommwithTimestamps() + " size of current timeline="
			// + currentTimeline.getActivityObjectsInTimeline().size());
			// }
			//
			// if (VerbosityConstants.WriteEditDistancePerRtPerCand)
			// {
			// WritingToFile.writeEditDistancesPerRtPerCand(this.userAtRecomm, this.dateAtRecomm, this.timeAtRecomm,
			// this.distancesSortedMap, this.candidateTimelines, this.nextActivityObjectsFromCands,
			// this.activitiesGuidingRecomm, activityObjectAtRecommPoint,
			// VerbosityConstants.WriteCandInEditDistancePerRtPerCand,
			// VerbosityConstants.WriteEditOperatationsInEditDistancePerRtPerCand,
			// this.endPointIndicesConsideredInCands, Constant.primaryDimension);
			// }
			// //End of not relevant curtain
			//////// Create ranked recommended act names

			// Non relevant Curtain start
			// this.recommendedActivityNamesWithRankscores = createRankedTopRecommendedActivityPDVals(
			// this.nextActivityObjectsFromCands, this.caseType, similarityOfEndPointActivityObjectCand,
			// this.lookPastType, this.distancesSortedMap);
			// Non relevant Curtain end

			this.recommendedActivityNamesWithRankscores = getTopPredictedAKOMActivityPDVals(
					this.activitiesGuidingRecomm, this.caseType, this.lookPastType, this.candidateTimelines, 1, false,
					Constant.AKOMHighestOrder);

			this.rankedRecommendedActNamesWithRankScoresStr = getRankedRecommendedActivityPDvalsWithRankScoresString(
					this.recommendedActivityNamesWithRankscores);

			if (VerbosityConstants.verbose)
			{
				System.out.println(
						"rankedRecommendedActNamesWithRankScoresStr= " + rankedRecommendedActNamesWithRankScoresStr);
			}
			this.rankedRecommendedActNamesWithoutRankScoresStr = getRankedRecommendedActivityPDValsithoutRankScoresString(
					this.recommendedActivityNamesWithRankscores);
			//
			// // Non relevant Curtain start
			// this.normEditSimilarity = (ArrayList<Double>) this.nextActivityObjectsFromCands.entrySet().stream()
			// .map(e -> e.getValue().getSecond()).collect(Collectors.toList());
			//
			//
			// if (this.caseType.equals(Enums.CaseType.CaseBasedV1))
			// {
			// this.simEndActivityObjForCorr = (ArrayList<Double>) this.nextActivityObjectsFromCands.entrySet()
			// .stream().map(nActObj -> similarityOfEndPointActivityObjectCand.get(nActObj.getKey()))
			// .collect(Collectors.toList());
			// }
			// // Non relevant Curtain end
			//
			if (VerbosityConstants.verbose)
			{
				System.out.println("Debug: rankedRecommendedActNamesWithRankScoresStr= "
						+ rankedRecommendedActNamesWithRankScoresStr);
				System.out.println("Debug: rankedRecommendedActNamesWithoutRankScoresStr= "
						+ rankedRecommendedActNamesWithoutRankScoresStr);
				System.out.println("Constant.removeCurrentActivityNameFromRecommendations = "
						+ Constant.removeCurrentActivityNameFromRecommendations);
			}
			////////

			if (Constant.removeCurrentActivityNameFromRecommendations)
			{/*
				 * IMPORTANT: If the next activity after the current activity object in the current timeline is an
				 * invalid activity, then we can include the current activity in the list of recommended activities,
				 * otherwise the current activity has to be removed from the list of recommended activities
				 */
				if (currentTimeline.getImmediateNextActivityInvalid() == 0) // not invalid
				{
					// TODO
					// this.recommendedActivityNamesWithRankscores = removeRecommPointActivityFromRankedRecomm(
					// recommendedActivityNamesWithRankscores, activityNameAtRecommPoint);
					System.out.println("removing recomm point activity (Current Activity) from list of recommendation");
				}
			}

			// System.out.println("Debug note192_2: current timeline " +
			// currentTimeline.getActivityObjectNamesInSequence());

			////////
			long recommMasterTEnd = System.currentTimeMillis();

			// start of curtain for performance string writing
			// String performanceString = this.userIDAtRecomm + "," + this.dateAtRecomm + "," + this.timeAtRecomm + ","
			// + matchingUnitInCountsOrHours + "," + trainingTimelines.size() + "," + this.trainingTimeline.size() + ","
			// + this.activitiesGuidingRecomm.size() + "," + this.candidateTimelines.size() + ","
			// + getSumOfActivityObjects(candidateTimelines) + "," + (recommMasterTEnd - recommMasterT0) + ","
			// + timeTakenToFetchCandidateTimelines + "," + timeTakenToComputeNormEditDistances + "\n";
			// WritingToFile.appendLineToFileAbsolute(performanceString, performanceFileName);
			// end of curtain for performance string writing
			//////////////
		}

		catch (

		Exception e)
		{
			e.printStackTrace();
			PopUps.getTracedErrorMsg("Exception in recommendation master");
		}

		System.out.println("\n^^^^^^^^^^^^^^^^Exiting Recommendation Master");
	}

	/**
	 * 
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param lookPastType
	 * @param candidateTimelines
	 * @param constantValScore
	 * @param verbose
	 * @param highestOrder
	 * @return
	 */
	private LinkedHashMap<String, Double> getTopPredictedAKOMActivityPDVals(
			ArrayList<ActivityObject> activitiesGuidingRecomm, CaseType caseType, LookPastType lookPastType,
			LinkedHashMap<String, Timeline> candidateTimelines, double constantValScore, boolean verbose,
			int highestOrder)
	{

		LinkedHashMap<String, Double> res = new LinkedHashMap<>();

		// System.out.println("Current timeline:");
		// Convert current timeline to a seq of integers
		ArrayList<Integer> currSeq = TimelineTransformers.timelineToSeqOfActIDs(activitiesGuidingRecomm, false);

		// Convert cand timeline to a list of seq of integers
		ArrayList<ArrayList<Integer>> candTimelinesAsSeq = new ArrayList<>();

		// System.out.println("Cand timelines:");
		for (Entry<String, Timeline> candT : candidateTimelines.entrySet())
		{
			candTimelinesAsSeq.add(
					TimelineTransformers.timelineToSeqOfActIDs(candT.getValue().getActivityObjectsInTimeline(), false));
		}

		// System.out.println("predictedNextSymbol = ");
		// TimelineTransformers.timelineToSeqOfActIDs(timeline, delimiter)
		if (lookPastType.equals(Enums.LookPastType.Daywise))
		{

			SeqPredictor p = new SeqPredictor(candTimelinesAsSeq, currSeq, verbose);
			int predSymbol = p.AKOMSeqPredictor(highestOrder, verbose);

			// System.out.println("predictedNextSymbol = " +
			// SeqPredictor p = new SeqPredictor(candTimelinesAsSeq, currSeq, highestOrder, verbose);
			Integer predictedActID = Integer.valueOf(predSymbol);
			System.out.println(" = " + predictedActID.toString());

			res.put(predictedActID.toString(), constantValScore);
			return res;
			// return createRankedTopRecommendedActivityNamesClosestTime(distancesSortedMap);
		}
		// else if ((lookPastType.equals(Enums.LookPastType.Daywise)) ||
		// (lookPastType.equals(Enums.LookPastType.NHours))
		// || (lookPastType.equals(Enums.LookPastType.NCount)) || (lookPastType.equals(Enums.LookPastType.NGram)))
		// {
		// switch (caseType)
		// {
		// case SimpleV3:
		// // createRankedTopRecommendedPDValsSimpleV3_3
		// return createRankedTopRecommendedPDValsSimpleV3_3(nextActivityObjectsFromCands);
		// case CaseBasedV1:
		// return createRankedTopRecommendedActivityNamesCaseBasedV1_3(nextActivityObjectsFromCands,
		// similarityOfEndPointActObjCands);
		// default:
		// System.err.println(PopUps.getTracedErrorMsg("Error:unrecognised case type = " + caseType));
		// return null;
		// }
		// }
		else
		{
			System.err.println(PopUps.getTracedErrorMsg("Error:unrecognised lookpast type = " + lookPastType));
			return null;
		}

	}

	private static LinkedHashMap<String, String> extractCandUserIDs(LinkedHashMap<String, Timeline> candidateTimelines)
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
	 * @param distancesMapUnsorted
	 * @param typeOfThreshold
	 * @param thresholdVal
	 * @param activitiesGuidingRecomm
	 * @return Triple{prunedDistancesMap,thresholdAsDistance,thresholdPruningNoEffect}
	 */
	private static Triple<LinkedHashMap<String, Pair<String, Double>>, Double, Boolean> pruneAboveThreshold(
			LinkedHashMap<String, Pair<String, Double>> distancesMapUnsorted, TypeOfThreshold typeOfThreshold,
			double thresholdVal, ArrayList<ActivityObject> activitiesGuidingRecomm)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Inside pruneAboveThreshold:\n");

		double thresholdAsDistance = -1;
		if (typeOfThreshold.equals(Enums.TypeOfThreshold.Global))/// IgnoreCase("Global"))
		{
			thresholdAsDistance = thresholdVal / 100;
		}
		else if (typeOfThreshold.equals(Enums.TypeOfThreshold.Percent))// IgnoreCase("Percent"))
		{
			double maxEditDistance = (new AlignmentBasedDistance()).maxEditDistance(activitiesGuidingRecomm);
			thresholdAsDistance = maxEditDistance * (thresholdVal / 100);
		}
		else
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: type of threshold unknown in recommendation master"));
			// errorExists = true;
			System.exit(-2);
		}
		sb.append("thresholdAsDistance=" + thresholdAsDistance + "\n before pruning distancesMapUnsorted =\n");
		for (Entry<String, Pair<String, Double>> e : distancesMapUnsorted.entrySet())
		{
			sb.append(e.getKey() + "--" + e.getValue().toString() + "\n");
		}

		int countCandBeforeThresholdPruning = distancesMapUnsorted.size();// distanceScoresSorted.size();
		distancesMapUnsorted = TimelineUtils.removeAboveThreshold4SSD(distancesMapUnsorted, thresholdAsDistance);//
		int countCandAfterThresholdPruning = distancesMapUnsorted.size();

		sb.append("After pruning distancesMapUnsorted =\n");
		for (Entry<String, Pair<String, Double>> e : distancesMapUnsorted.entrySet())
		{
			sb.append(e.getKey() + "--" + e.getValue().toString() + "\n");
		}

		sb.append("thresholdAsDistance=" + thresholdAsDistance + " countCandBeforeThresholdPruning="
				+ countCandBeforeThresholdPruning + "countCandAfterThresholdPruning=" + countCandAfterThresholdPruning
				+ "\n");
		if (VerbosityConstants.verbose)
		{
			System.out.println(sb.toString());
		}
		boolean thresholdPruningNoEffect = (countCandBeforeThresholdPruning == countCandAfterThresholdPruning);

		if (!thresholdPruningNoEffect)
		{
			// System.out.println("Ohh..threshold pruning is happening. Are you sure you wanted this?");// +msg);
			// PopUps.showMessage("Ohh..threshold pruning is happening. Are you sure you wanted this?");// +msg);
			if (!Constant.useiiWASThreshold)
			{
				System.err.println("Error: threshold pruning is happening.");// +msg);
			}
		}
		return new Triple<LinkedHashMap<String, Pair<String, Double>>, Double, Boolean>(distancesMapUnsorted,
				thresholdAsDistance, thresholdPruningNoEffect);
	}

	/**
	 * 
	 * @param nextActivityObjectsFromCands
	 * @param caseType
	 * @param similarityOfEndPointActObjCands
	 *            used for CaseBasedV1_3 approach
	 * @param distancesSortedMap
	 *            used for closest time approach in which case it is {candid, Pair{act name of act obj of cloeset, abs
	 *            diff of st}}
	 * @return
	 */
	private static LinkedHashMap<String, Double> createRankedTopRecommendedActivityPDVals(
			LinkedHashMap<String, Pair<ActivityObject, Double>> nextActivityObjectsFromCands, CaseType caseType,
			LinkedHashMap<String, Double> similarityOfEndPointActObjCands, LookPastType lookPastType,
			LinkedHashMap<String, Pair<String, Double>> distancesSortedMap)
	{

		if (lookPastType.equals(Enums.LookPastType.ClosestTime))
		{
			return createRankedTopRecommendedActivityNamesClosestTime(distancesSortedMap);
		}
		else if ((lookPastType.equals(Enums.LookPastType.Daywise)) || (lookPastType.equals(Enums.LookPastType.NHours))
				|| (lookPastType.equals(Enums.LookPastType.NCount)) || (lookPastType.equals(Enums.LookPastType.NGram)))
		{
			switch (caseType)
			{
				case SimpleV3:
					// createRankedTopRecommendedPDValsSimpleV3_3
					return createRankedTopRecommendedPDValsSimpleV3_3(nextActivityObjectsFromCands);
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
	 * <p>
	 * NOT NORMALISING FOR CLOSEST TIME APPROACH
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userIDAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param distanceUsed
	 * @param lookPastType
	 * @param hjEditDistance
	 * @param featureWiseEditDistance
	 * @param featureWiseWeightedEditDistance
	 * @param OTMDSAMEditDistance
	 * @return {CandID,Trace,EditDist} for MU and Daywise, {CandID,ActName of act obj with closest st,avs time diff in
	 *         secs} for closest st time.... Pair{{},{candID,indexOfEndPointConsideredInCand}}
	 *         <p>
	 *         The second element of the result pair is:
	 *         <p>
	 *         - for SeqNCount and SeqNHours approach, tne end point index considered in the candidate is the last
	 *         activity object in that cand
	 *         <p>
	 *         - for Daywise approach: {Date of CandidateTimeline as string, End point index of least distant
	 *         subsequence}}
	 *         <p>
	 *         - for ClosesetTime approach: {Date of CandidateTimeline as string, End point index of least distant
	 *         subsequence}}
	 */

	// @SuppressWarnings("unused")
	private static Pair<LinkedHashMap<String, Pair<String, Double>>, LinkedHashMap<String, Integer>> getNormalisedDistancesForCandidateTimelines(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			CaseType caseType, String userIDAtRecomm, Date dateAtRecomm, Time timeAtRecomm, String distanceUsed,
			LookPastType lookPastType, HJEditDistance hjEditDistance, FeatureWiseEditDistance featureWiseEditDistance,
			FeatureWiseWeightedEditDistance featureWiseWeightedEditDistance, OTMDSAMEditDistance OTMDSAMEditDistance,
			EditDistanceMemorizer editDistancesMemorizer)
	{
		// {CandID,Trace,EditDist}
		LinkedHashMap<String, Pair<String, Double>> normalisedDistanceForCandTimelines = null;

		// {CandID, EndIndexOfLeastDistantSubsequene} //this is relevant for daywise as curr act can occur multiple
		// times in same cand
		LinkedHashMap<String, Integer> endIndexSubseqConsideredInCand = null;

		if (lookPastType.equals(Enums.LookPastType.Daywise))
		{
			Pair<LinkedHashMap<String, Pair<String, Double>>, LinkedHashMap<String, Integer>> editDistancesRes = TimelineUtils
					.getEditDistancesForDaywiseCandidateTimelines(candidateTimelines, activitiesGuidingRecomm,
							userIDAtRecomm, dateAtRecomm.toString(), timeAtRecomm.toString(),
							Constant.hasInvalidActivityNames, Constant.INVALID_ACTIVITY1, Constant.INVALID_ACTIVITY2,
							distanceUsed, hjEditDistance);

			LinkedHashMap<String, Pair<String, Double>> candEditDistances = editDistancesRes.getFirst();
			normalisedDistanceForCandTimelines = normalisedDistancesOverTheSet(candEditDistances, userIDAtRecomm,
					dateAtRecomm.toString(), timeAtRecomm.toString());

			endIndexSubseqConsideredInCand = editDistancesRes.getSecond();

		}
		else if (lookPastType.equals(Enums.LookPastType.NCount) || lookPastType.equals(Enums.LookPastType.NHours))
		{
			normalisedDistanceForCandTimelines = getNormalisedDistancesForCandidateTimelinesFullCand(candidateTimelines,
					activitiesGuidingRecomm, caseType, userIDAtRecomm, dateAtRecomm.toString(), timeAtRecomm.toString(),
					distanceUsed, hjEditDistance, featureWiseEditDistance, featureWiseWeightedEditDistance,
					OTMDSAMEditDistance, editDistancesMemorizer);

			// for SeqNCount and SeqNHours approach, tne end point index considered in the candidate is the last
			// activity object in that cand
			// endIndexSubseqConsideredInCand = (LinkedHashMap<String, Integer>) candidateTimelines.entrySet().stream()
			// .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().size() - 1));

			endIndexSubseqConsideredInCand = candidateTimelines.entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().size() - 1, (v1, v2) -> v1,
							LinkedHashMap<String, Integer>::new));
		}
		///
		else if (lookPastType.equals(Enums.LookPastType.NGram))
		{
			LinkedHashMap<String, Pair<String, Double>> candEditDistances = new LinkedHashMap<>();
			for (String candID : candidateTimelines.keySet())
			{
				candEditDistances.put(candID, new Pair<>("", Double.valueOf(0)));// assigning dist of 0 so, sim score
																					// will be 1.
			}
			normalisedDistanceForCandTimelines = candEditDistances;

			// for SeqNCount and SeqNHours approach, tne end point index considered in the candidate is the last
			// activity object in that cand TODO
			// endIndexSubseqConsideredInCand = (LinkedHashMap<String, Integer>) candidateTimelines.entrySet().stream()
			// .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().size() - 1));
			endIndexSubseqConsideredInCand = candidateTimelines.entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().size() - 1, (v1, v2) -> v1,
							LinkedHashMap<String, Integer>::new));
		}
		///

		else if (lookPastType.equals(Enums.LookPastType.ClosestTime))
		{
			Pair<LinkedHashMap<String, Pair<String, Double>>, LinkedHashMap<String, Integer>> editDistancesRes = null;

			if (Constant.ClosestTimeAllowSpillOverDays == false)
			{
				editDistancesRes = TimelineUtils.getClosestTimeDistancesForDaywiseCandidateTimelines(candidateTimelines,
						activitiesGuidingRecomm, userIDAtRecomm, dateAtRecomm.toString(), timeAtRecomm.toString(),
						Constant.hasInvalidActivityNames, Constant.INVALID_ACTIVITY1, Constant.INVALID_ACTIVITY2,
						distanceUsed);
			}
			else
			{
				// Curtain start 15 Aug
				// editDistancesRes = TimelineUtils.getClosestTimeDistancesForCandidateTimelinesColl(candidateTimelines,
				// activitiesGuidingRecomm, userIDAtRecomm, dateAtRecomm.toString(), timeAtRecomm.toString(),
				// Constant.hasInvalidActivityNames, Constant.INVALID_ACTIVITY1, Constant.INVALID_ACTIVITY2,
				// distanceUsed, false);
				// Curtain end 15 Aug

				editDistancesRes = TimelineUtils.getClosestTimeDistsForCandTimelinesColl1CandPerNeighbour(
						candidateTimelines, activitiesGuidingRecomm, userIDAtRecomm, dateAtRecomm.toString(),
						timeAtRecomm.toString(), Constant.hasInvalidActivityNames, Constant.INVALID_ACTIVITY1,
						Constant.INVALID_ACTIVITY2, distanceUsed, /* Constant.ClosestTimeDiffThresholdInSecs * 1000, */
						false);

			}

			LinkedHashMap<String, Pair<String, Double>> candEditDistances = editDistancesRes.getFirst();
			normalisedDistanceForCandTimelines = candEditDistances; // 15 Aug 2017, NOT NORMALISING TO PRESERVE THE
																	// ACTUAL TIME DIFF
			// Aug 15, 2017: Noticed that: for closest time approach, i was normalising the distance (time difference)
			// over each set of candidate timelines (like the Ncount approach). And then while computing the score i am
			// using score = 1-min(1,timeDiff/60mins). THIS IS INCORRECT since the timediff at this stage is not the
			// actual timediff but normalised time diff, hence should not compared with 60 mins. Hence, time difference
			// should not be normalised.
			// see: createRankedTopRecommendedActivityNamesClosestTime()

			// Start of Disabled on Aug 15 2017
			// normalisedDistancesOverTheSet(candEditDistances, userIDAtRecomm,
			// dateAtRecomm.toString(), timeAtRecomm.toString());
			// Start of Disabled on Aug 15 2017
			endIndexSubseqConsideredInCand = editDistancesRes.getSecond();
		}

		else
		{
			System.err.println(PopUps.getTracedErrorMsg("Error: Unrecognised lookPastType "));
			System.exit(-1);
		}

		if (normalisedDistanceForCandTimelines == null || normalisedDistanceForCandTimelines.size() == 0)
		{
			PopUps.printTracedErrorMsg(
					"Error: normalisedDistanceForCandTimelines.size=" + normalisedDistanceForCandTimelines.size());
		}

		return new Pair<LinkedHashMap<String, Pair<String, Double>>, LinkedHashMap<String, Integer>>(
				normalisedDistanceForCandTimelines, endIndexSubseqConsideredInCand);
	}

	/**
	 * Take all training day timelines as candidate timelines, i.e., no filtering of candidate timelines for closest
	 * time approach
	 * 
	 * @param userIDAtRecomm
	 * @param trainTestTimelinesForAllUsers
	 * @return Map{userID__DateAsString,cand timeline} where cand timelines are all the training day timelines from
	 *         other users except current user
	 */
	// private static LinkedHashMap<String, Timeline> extractCandClosestTimeColl2(String userIDAtRecomm,
	// LinkedHashMap<String, List<LinkedHashMap<Date, Timeline>>> trainTestTimelinesForAllUsers)
	// {
	//
	// }

	/**
	 * 
	 */
	public Date getDateAtRecomm()
	{
		return dateAtRecomm;
	}

	/**
	 * 
	 */
	public String getActivityNamesGuidingRecomm()
	{
		StringBuilder res = new StringBuilder();

		for (ActivityObject ae : activitiesGuidingRecomm)
		{
			res = StringUtils.fCat(res, ">>", ae.getActivityName());
			// res.append(">>" + ae.getActivityName());
		}
		return res.toString();
	}

	// $$start here

	// // /
	// /**
	// * Generates a ranked list of recommended Activity Objects and sets recommendedActivityNamesRankscorePairs
	// *
	// * and calls the following setter methods setRecommendedActivityNamesWithRankscores
	// * setRankedRecommendedActivityNamesWithRankScores setRankedRecommendedActivityNamesWithoutRankScores
	// *
	// * Is function with Constants Beta and rank scoring
	// *
	// * @param topNextActivityObjectsWithDistance
	// */
	// public void createRankedTopRecommendedActivityNamesCaseBasedV1_2(
	// ArrayList<Triple<ActivityObject, Double, Integer>> topNextActivityObjectsWithDistance,
	// LinkedHashMap<Integer, Double> similarityOfEndPointActivityObjectCand)
	// // we might remove these arguments as these are already member variables of this class
	// {
	// String topRankedActivityNamesWithScore, topRankedActivityNamesWithoutScore;
	//
	// int numberOfTopNextActivityObjects = topNextActivityObjectsWithDistance.size();
	//
	// // System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
	// // topRecommendationsWithDistance="+topRecommendationsWithDistance);
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects:
	// numberOfTopNextActivityEvenst=numberOfTopNextActivityEvenst");
	// // System.out.print("Debug inside createRankedTopRecommendedActivityObjects: the read next activity objects are:
	// // ");
	// // / for calculating their correlation
	// ArrayList<Double> normEditSimilarity = new ArrayList<Double>();
	// ArrayList<Double> simEndActivityObjForCorr = new ArrayList<Double>();
	// LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<String, Double>(); //
	// <ActivityName,RankScore>
	// // /
	// Double maxEditDistanceValExceptEnd = 0d, minEditDistanceValExceptEnd = 10000d;
	//
	// int numberOfNextAOsAtMaxDistance = 0;
	// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
	// {
	// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
	// if (editDistanceVal > maxEditDistanceValExceptEnd)
	// {
	// maxEditDistanceValExceptEnd = editDistanceVal;
	// numberOfNextAOsAtMaxDistance = 1;
	// }
	//
	// else if (editDistanceVal == maxEditDistanceValExceptEnd)
	// {
	// numberOfNextAOsAtMaxDistance += 1;
	// }
	//
	// if (editDistanceVal < minEditDistanceValExceptEnd)
	// {
	// minEditDistanceValExceptEnd = editDistanceVal;
	// // numberOfNextAOsAtMaxDistance = 1;
	// }
	// // else if (editDistanceVal == maxEditDistanceValExceptEnd)
	// // {
	// // numberOfNextAOsAtMaxDistance += 1;
	// // }
	// }
	//
	// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
	// {
	// String topNextActivityName = topNextActivityObjectsWithDistance.get(i).getFirst().getActivityName();
	// Double editDistanceValExceptEnd = topNextActivityObjectsWithDistance.get(i).getSecond();
	//
	// Integer candTimelineID = topNextActivityObjectsWithDistance.get(i).getThird();
	//
	// Double simEndPointActivityObject = similarityOfEndPointActivityObjectCand.get(candTimelineID);
	//
	// Double simRankScore;// represents similarity
	//
	// double normEditDistanceValExceptEnd = StatsUtils.minMaxNorm(editDistanceValExceptEnd,
	// maxEditDistanceValExceptEnd, minEditDistanceValExceptEnd);
	//
	// normEditSimilarity.add(1 - normEditDistanceValExceptEnd);
	// simEndActivityObjForCorr.add(simEndPointActivityObject);
	//
	// if (Constant.rankScoring.trim().equalsIgnoreCase("product"))
	// {
	// simRankScore = (1d - normEditDistanceValExceptEnd) * simEndPointActivityObject;
	// // simRankScore = (1d - (editDistanceValExceptEnd / maxEditDistanceValExceptEnd)) *
	// // simEndPointActivityObject;
	// // System.out.println("Prod RANK SCORE CALC=" + "(1-(" + editDistanceValExceptEnd + "/" +
	// // maxEditDistanceValExceptEnd + "))* (" +
	// // simEndPointActivityObject + ")");
	// System.out.println("Prod RANK SCORE CALC=" + "(1-(" + normEditDistanceValExceptEnd + "))* ("
	// + simEndPointActivityObject + ")");
	// }
	//
	// else
	// {
	// simRankScore = Constant.ALPHA * (1d - normEditDistanceValExceptEnd)
	// + (1 - Constant.ALPHA) * simEndPointActivityObject;
	// System.out.println("Sum RANK SCORE CALC=" + Constant.ALPHA + "*(1-(" + normEditDistanceValExceptEnd
	// + "))" + "" + "+" + (1 - Constant.ALPHA) + "*(" + simEndPointActivityObject + ")");
	// // simRankScore = Constant.BETA * (1d - (editDistanceValExceptEnd / maxEditDistanceValExceptEnd)) + (1 -
	// // Constant.BETA) *
	// // simEndPointActivityObject;
	// // System.out.println("Sum RANK SCORE CALC=" + Constant.BETA + "*(1-(" + editDistanceValExceptEnd + "/"
	// // + maxEditDistanceValExceptEnd + "))" +
	// // "" + "+" + (1 - Constant.BETA) +
	// // "*("
	// // + simEndPointActivityObject + ")");
	// }
	//
	// if (recommendedActivityNamesRankscorePairs.containsKey(topNextActivityName) == false)
	// {
	// recommendedActivityNamesRankscorePairs.put(topNextActivityName, simRankScore);
	// }
	//
	// else
	// {
	// recommendedActivityNamesRankscorePairs.put(topNextActivityName,
	// recommendedActivityNamesRankscorePairs.get(topNextActivityName) + simRankScore);
	// }
	// }
	//
	// System.out.println("Num of top next activity objects at Max distance=" + numberOfNextAOsAtMaxDistance);
	// if (numberOfNextAOsAtMaxDistance > 5)
	// {
	// System.out.println("Warning: num of top next aos at max distance(>5) =" + numberOfNextAOsAtMaxDistance);
	// }
	//
	// recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
	// .sortByValueDesc(recommendedActivityNamesRankscorePairs);
	// // Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric value
	// // of rank)
	//
	// // ///////////IMPORTANT //////////////////////////////////////////////////////////
	// this.setRecommendedActivityNamesWithRankscores(recommendedActivityNamesRankscorePairs);
	//
	// this.setRankedRecommendedActivityNamesWithRankScores(recommendedActivityNamesRankscorePairs);
	// this.setRankedRecommendedActivityNamesWithoutRankScores(recommendedActivityNamesRankscorePairs);
	//
	// this.setNormEditSimilarity(normEditSimilarity);
	// this.setSimEndActivityObjForCorr(simEndActivityObjForCorr);
	// // /////////////////////////////////////////////////////////////////////
	//
	// topRankedActivityNamesWithScore = getRankedRecommendedActivityNamesWithRankScores();
	// topRankedActivityNamesWithoutScore = getRankedRecommendedActivityNamesWithoutRankScores();
	//
	// if (VerbosityConstants.verbose)
	// {
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithScore= "
	// + topRankedActivityNamesWithScore);
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithoutScore= "
	// + topRankedActivityNamesWithoutScore);
	// }
	// // return topRankedString;
	// }

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
			LinkedHashMap<String, Pair<ActivityObject, Double>> nextActObjsWithDistExceptEnd,
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

		for (Map.Entry<String, Pair<ActivityObject, Double>> nextActObj : nextActObjsWithDistExceptEnd.entrySet())
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
			LinkedHashMap<String, Pair<String, Double>> closestActObjsWithSTDiffInSecs)
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

	// private void setNormEditSimilarity(ArrayList<Double> arr)
	// {
	// this.normEditSimilarity = arr;
	// }
	//
	// private void setSimEndActivityObjForCorr(ArrayList<Double> arr)
	// {
	// this.simEndActivityObjForCorr = arr;
	// }

	// public double getAvgRestSimilarity()
	// {
	// if (normEditSimilarity == null)
	// {
	// if (Constant.caseType.equals("CaseBasedV1"))
	// {
	// System.err.println("Error: Norm edit similarity is null (not set)");
	// }
	//
	// // else
	// // {
	// // System.out.println("Alert: Norm edit similarity is null for caseType =" + Constant.caseType);
	// // }
	//
	// return Double.NaN;// -99999999;
	// }
	//
	// // PearsonsCorrelation pc = new PearsonsCorrelation();
	// Mean mean = new Mean();
	// return (mean.evaluate(UtilityBelt.toPrimitive(normEditSimilarity), 0, normEditSimilarity.size()));//
	// pc.correlation(UtilityBelt.toPrimitive(normEditSimilarity),
	// }

	// public double getAvgEndSimilarity()
	// {
	// if (simEndActivityObjForCorr == null)
	// {
	// if (Constant.caseType.equals("CaseBasedV1"))
	// {
	// System.err.println("Error: sim end activity object is null (not set)");
	// }
	//
	// // else
	// // {
	// // System.out.println("Alert: sim end activity object is null for caseType =" + Constant.caseType);
	// // }
	// // System.err.println("Error: sim end activity object are null (not set)");
	//
	// return Double.NaN;// -99999999;
	// }
	//
	// // PearsonsCorrelation pc = new PearsonsCorrelation();
	// Mean mean = new Mean();
	// return (mean.evaluate(UtilityBelt.toPrimitive(simEndActivityObjForCorr), 0, simEndActivityObjForCorr.size()));//
	// pc.correlation(UtilityBelt.toPrimitive(normEditSimilarity),
	// // UtilityBelt.toPrimitive(simEndActivityObjForCorr)));
	//
	// }

	// public static double getSDRestSimilarity()
	// {
	// if (normEditSimilarity == null)
	// {
	// if (Constant.caseType.equals("CaseBasedV1"))
	// {
	// System.err.println("Error: normEditSimilarity is null (not set)");
	// }
	//
	// // else
	// // {
	// // System.out.println("Alert: normEditSimilarity is null for caseType =" + Constant.caseType);
	// // }
	// // System.err.println("Error: Norm edit similarity and sim end activity object are null (not set)");
	// return Double.NaN;// -99999999;
	// }
	//
	// // PearsonsCorrelation pc = new PearsonsCorrelation();
	// StandardDeviation sd = new StandardDeviation();
	// return (sd.evaluate(UtilityBelt.toPrimitive(normEditSimilarity)));
	//
	// }

	/**
	 * Generates a ranked list of recommended Activity Objects and sets recommendedActivityNamesRankscorePairs
	 * 
	 * and calls the following setter methods setRecommendedActivityNamesWithRankscores
	 * setRankedRecommendedActivityNamesWithRankScores setRankedRecommendedActivityNamesWithoutRankScores
	 * 
	 * Is function with Constants Beta and rank scoring
	 * 
	 * @param topNextActivityObjectsWithDistance
	 */
	// public void createRankedTopRecommendedActivityNamesSimpleV3_2(
	// ArrayList<Triple<ActivityObject, Double, Integer>> topNextActivityObjectsWithDistance, String userAtRecomm,
	// String dateAtRecomm, String timeAtRecomm)
	// // LinkedHashMap<Integer, Double> similarityOfEndPointActivityObjectCand) // we might remove these arguments as
	// // these are already member variables of this
	// // class
	// {
	// String topRankedActivityNamesWithScore, topRankedActivityNamesWithoutScore;
	//
	// int numberOfTopNextActivityObjects = topNextActivityObjectsWithDistance.size();
	//
	// // System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
	// // topRecommendationsWithDistance="+topRecommendationsWithDistance);
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects:
	// numberOfTopNextActivityEvenst=numberOfTopNextActivityEvenst");
	// // System.out.print("Debug inside createRankedTopRecommendedActivityObjects: the read next activity objects are:
	// // ");
	//
	// LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<String, Double>(); //
	// <ActivityName,RankScore>
	//
	// Double maxEditDistanceVal = 0d, minEditDistanceVal = 10000d;
	//
	// int numberOfNextAOsAtMaxDistance = 0;
	// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
	// {
	// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
	// if (editDistanceVal > maxEditDistanceVal)
	// {
	// maxEditDistanceVal = editDistanceVal;
	// numberOfNextAOsAtMaxDistance = 1;
	// }
	//
	// else if (editDistanceVal == maxEditDistanceVal)
	// {
	// numberOfNextAOsAtMaxDistance += 1;
	// }
	//
	// if (editDistanceVal < minEditDistanceVal)
	// {
	// minEditDistanceVal = editDistanceVal;
	// // numberOfNextAOsAtMaxDistance = 1;
	// }
	// // else if (editDistanceVal == maxEditDistanceValExceptEnd)
	// // {
	// // numberOfNextAOsAtMaxDistance += 1;
	// // }
	// }
	// StringBuilder editDistances = new StringBuilder(), normalisedEditDistances = new StringBuilder();
	//
	// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
	// {
	// String topNextActivityName = topNextActivityObjectsWithDistance.get(i).getFirst().getActivityName();
	// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
	//
	// Integer candTimelineID = topNextActivityObjectsWithDistance.get(i).getThird();
	//
	// // Double simEndPointActivityObject = similarityOfEndPointActivityObjectCand.get(candTimelineID);
	//
	// Double simRankScore;// represents similarity
	//
	// double normEditDistanceVal = StatsUtils.minMaxNorm(editDistanceVal, maxEditDistanceVal, minEditDistanceVal);
	//
	// if (VerbosityConstants.WriteNormalisation)
	// {
	// editDistances.append("_" + editDistanceVal);
	// normalisedEditDistances.append("_" + normEditDistanceVal);
	// }
	//
	// simRankScore = (1d - normEditDistanceVal);// * simEndPointActivityObject;
	// // simRankScore = (1d - (editDistanceValExceptEnd / maxEditDistanceValExceptEnd)) *
	// // simEndPointActivityObject;
	// // System.out.println("Prod RANK SCORE CALC=" + "(1-(" + editDistanceValExceptEnd + "/" +
	// // maxEditDistanceValExceptEnd + "))* (" +
	// // simEndPointActivityObject + ")");
	// System.out.println("Simple RANK SCORE CALC=" + "(1-(" + normEditDistanceVal + "))");// * (" +
	// // simEndPointActivityObject
	// // + ")");
	//
	// if (recommendedActivityNamesRankscorePairs.containsKey(topNextActivityName) == false)
	// {
	// recommendedActivityNamesRankscorePairs.put(topNextActivityName, simRankScore);
	// }
	//
	// else
	// {
	// recommendedActivityNamesRankscorePairs.put(topNextActivityName,
	// recommendedActivityNamesRankscorePairs.get(topNextActivityName) + simRankScore);
	// }
	// }
	//
	// System.out.println("Num of top next activity objects at Max distance=" + numberOfNextAOsAtMaxDistance);
	// if (numberOfNextAOsAtMaxDistance > 5)
	// {
	// System.out.println("Warning: num of top next aos at max distance(>5) =" + numberOfNextAOsAtMaxDistance);
	// }
	//
	// recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
	// .sortByValueDesc(recommendedActivityNamesRankscorePairs);
	// // Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric value
	// // of rank)
	//
	// // ///////////IMPORTANT //////////////////////////////////////////////////////////
	// this.setRecommendedActivityNamesWithRankscores(recommendedActivityNamesRankscorePairs);
	//
	// this.setRankedRecommendedActivityNamesWithRankScores(recommendedActivityNamesRankscorePairs);
	// this.setRankedRecommendedActivityNamesWithoutRankScores(recommendedActivityNamesRankscorePairs);
	//
	// // /////////////////////////////////////////////////////////////////////
	//
	// topRankedActivityNamesWithScore = getRankedRecommendedActivityNamesWithRankScores();
	// topRankedActivityNamesWithoutScore = getRankedRecommendedActivityNamesWithoutRankScores();
	//
	// if (VerbosityConstants.verbose)
	// {
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithScore= "
	// + topRankedActivityNamesWithScore);
	// System.out.println(
	// "Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithoutScore= "
	// + topRankedActivityNamesWithoutScore);
	// }
	//
	// if (VerbosityConstants.WriteNormalisation)
	// {
	// String toWrite = userAtRecomm + "," + dateAtRecomm + "," + timeAtRecomm + "," + editDistances + ","
	// + normalisedEditDistances + "\n";
	// WritingToFile.appendLineToFileAbsolute(toWrite, Constant.getCommonPath() + "NormalisationDistances.csv");
	// }
	// // return topRankedString;
	// }

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
			LinkedHashMap<String, Pair<ActivityObject, Double>> nextActivityObjectsWithDistance)
	{
		// $$System.out.println("\ninside createRankedTopRecommendedActivityNamesSimpleV3_3:");
		// <ActivityName,RankScore>
		LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<>();

		StringBuilder rankScoreCalc = new StringBuilder();

		for (Map.Entry<String, Pair<ActivityObject, Double>> nextActObj : nextActivityObjectsWithDistance.entrySet())
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
	 * 
	 * @since IN VERSION 2 WE HAD MIN MAX NORMALISATION INSIDE THIS FUNCTION, IN THIS V3 WE WILL NOT HAVE NORMALISATION
	 *        OF EDIT DISTANCE INSIDE THIS FUNCTION AS THE NORMALISATION IS DONE BEFOREHAND IN THE METHOD WHICH FETCHED
	 *        THE NORMALISED EDIT DISTANCE FOR CANDIDATE TIMELINES
	 * @param topNextActivityObjectsWithDistance
	 * @return {ActivityName,Rankscore} sorted by descending order of rank score
	 */
	public static LinkedHashMap<String, Double> createRankedTopRecommendedPDValsSimpleV3_3(
			LinkedHashMap<String, Pair<ActivityObject, Double>> nextActivityObjectsWithDistance)
	{
		// $$System.out.println("\ninside createRankedTopRecommendedActivityNamesSimpleV3_3:");
		// <ActivityName,RankScore>
		LinkedHashMap<String, Double> recommendedActivityPDValsRankscorePairs = new LinkedHashMap<>();

		StringBuilder rankScoreCalc = new StringBuilder();

		for (Map.Entry<String, Pair<ActivityObject, Double>> nextActObj : nextActivityObjectsWithDistance.entrySet())
		{ // String candTimelineID = nextActObj.getKey();
			double normEditDistanceVal = nextActObj.getValue().getSecond();

			for (Integer pdVal : nextActObj.getValue().getFirst().getPrimaryDimensionVal())
			{// if the next activity object is a merged one
				String nextActivityPDVal = pdVal.toString();

				// represents similarity
				double simRankScore = (1d - normEditDistanceVal);// * simEndPointActivityObject;
				rankScoreCalc.append("Simple RANK SCORE (1- normED) =" + "1-" + normEditDistanceVal + "\n");

				if (recommendedActivityPDValsRankscorePairs.containsKey(nextActivityPDVal) == false)
				{
					recommendedActivityPDValsRankscorePairs.put(nextActivityPDVal, simRankScore);
				}
				else
				{
					recommendedActivityPDValsRankscorePairs.put(nextActivityPDVal,
							recommendedActivityPDValsRankscorePairs.get(nextActivityPDVal) + simRankScore);
				}
			}

		}

		if (VerbosityConstants.verboseRankScoreCalcToConsole)
		{
			System.out.println(rankScoreCalc.toString());
		}

		// Sorted in descending order of ranked score: higher ranked score means more top in rank (larger numeric value
		// of rank)
		recommendedActivityPDValsRankscorePairs = (LinkedHashMap<String, Double>) ComparatorUtils
				.sortByValueDesc(recommendedActivityPDValsRankscorePairs);

		if (recommendedActivityPDValsRankscorePairs == null || recommendedActivityPDValsRankscorePairs.size() == 0)
		{
			PopUps.printTracedErrorMsg("Error: recommendedActivityPDValsRankscorePairs.size() = "
					+ recommendedActivityPDValsRankscorePairs.size());
		}
		return recommendedActivityPDValsRankscorePairs;
	}

	// /////////////////////////////////////////////////////////////////////
	// /**
	// * Set Map of <recommended Activity Name, sim rank score>
	// *
	// * @param recommendedActivityNamesRankscorePairs
	// */
	// public void setRecommendedActivityNamesWithRankscores(
	// LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs)
	// {
	// this.recommendedActivityNamesWithRankscores = recommendedActivityNamesRankscorePairs;
	// }

	public LinkedHashMap<String, Double> getRecommendedActivityNamesWithRankscores()
	{
		return this.recommendedActivityNamesWithRankscores;
	}

	// /////////////////////////////////////////////////////////////////////

	// /////////////////////////////////////////////////////////////////////
	/**
	 * Generate the string: '__recommendedActivityName1:simRankScore1__recommendedActivityName2:simRankScore2'
	 * 
	 * @param recommendedActivityPDValRankscorePairs
	 */
	private static String getRankedRecommendedActivityPDvalsWithRankScoresString(
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

	// /////////////////////////////////////////////////////////////////////
	/**
	 * Generate string as '__recommendedActivityName1__recommendedActivityName2'
	 * 
	 * @param recommendedActivityNameRankscorePairs
	 * @return
	 */
	private static String getRankedRecommendedActivityPDValsithoutRankScoresString(
			LinkedHashMap<String, Double> recommendedActivityNameRankscorePairs)
	{
		StringBuilder rankedRecommendationWithoutRankScores = new StringBuilder();
		for (Map.Entry<String, Double> entry : recommendedActivityNameRankscorePairs.entrySet())
		{
			rankedRecommendationWithoutRankScores.append("__" + entry.getKey());
		}
		return rankedRecommendationWithoutRankScores.toString();
	}

	// /////////////////////////////////////////////////////////////////////

	/*
	 * public String getSingleNextRecommendedActivity() { return this.singleNextRecommendedActivity; }
	 */
	/*
	 * public String getTopRecommendedActivities() { return this.topNextRecommendedActivities; }
	 * 
	 * public String getTopRecommendedActivitiesWithoutDistance() { String result=""; String
	 * topActivities=this.topNextRecommendedActivities; String[] splitted1= topActivities.split("__");
	 * 
	 * for(int i=1;i<splitted1.length;i++) { String[] splitted2=splitted1[i].split(":");
	 * result=result+"__"+splitted2[0]; }
	 * 
	 * return result; }
	 */
	// //

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
	public static LinkedHashMap<String, Pair<ActivityObject, Double>> fetchNextActivityObjectsFromNext(
			LinkedHashMap<String, Pair<String, Double>> editDistanceSortedFullCand,
			LinkedHashMap<String, Timeline> candidateTimelines)
	{
		// TimelineID,Pair{Next Activity Object,edit distance}
		LinkedHashMap<String, Pair<ActivityObject, Double>> nextActObjs = new LinkedHashMap<>();
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
				ActivityObject nextActivityObjectForCand = candidateTimeline.getNextActivityObject();

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
						new Pair<ActivityObject, Double>(nextActivityObjectForCand, editDistanceForCandidate));
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
	public static LinkedHashMap<String, Pair<ActivityObject, Double>> fetchNextActivityObjectsDaywise(
			LinkedHashMap<String, Pair<String, Double>> editDistanceSorted,
			LinkedHashMap<String, Timeline> candidateTimelines, LinkedHashMap<String, Integer> endPointIndices)
	{
		// System.out.println("\n-----------------Inside fetchNextActivityObjectsDaywise");
		LinkedHashMap<String, Pair<ActivityObject, Double>> nextActObjs = new LinkedHashMap<>();

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

				ActivityObject nextValidAO = candUserDayTimeline
						.getNextValidActivityAfterActivityAtThisPositionPD(endPointIndexInCand);
				nextActObjs.put(timelineID, new Pair<ActivityObject, Double>(nextValidAO, distanceOfCandTimeline));

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
	 * @param editDistanceSorted
	 * @param candidateTimelines
	 * @return TimelineID,Pair{Next Activity Object,edit distance}
	 */
	public static LinkedHashMap<String, Pair<ActivityObject, Double>> fetchNextActivityObjects(
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

	// ////////
	/*
	 * Added: Oct 5, 2014: for IMPORTANT POINT: THE CANDIDATE TIMELINE IS THE DIRECT CANDIDATE TIMELINE AND NOT THE
	 * LEAST DISTANT SUBCANDIDATE.
	 */
	/**
	 * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the edit distance of
	 * that candidate timeline with the current timeline.
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 *            can be 'SimpleV3' or 'CaseBasedV1'
	 * 
	 * @param userAtRecomm
	 *            used only for writing to file
	 * @param dateAtRecomm
	 *            used only for writing to file
	 * @param timeAtRecomm
	 *            used only for writing to file
	 * @param hjEditDistance
	 * 
	 * @return {CanditateTimelineID, Pair{Trace,Edit distance of this candidate}}
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getHJEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			HJEditDistance hjEditDistance)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, Pair<String, Double>> candEditDistances = new LinkedHashMap<>();

		for (Map.Entry<String, Timeline> entry : candidateTimelines.entrySet())
		{
			Pair<String, Double> editDistanceForThisCandidate = null;
			String candidateTimelineId = entry.getKey();

			switch (caseType)
			{
				case CaseBasedV1:
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
					// invalids are already expunged, no need to expunge again
					{
						editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceWithoutEndCurrentActivity(
								entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
								dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					else
					{
						editDistanceForThisCandidate = hjEditDistance
								.getHJEditDistanceWithoutEndCurrentActivityInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm,
										userAtRecomm, dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					break;

				case SimpleV3:// "SimpleV3":
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
					{
						editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceWithTrace(
								entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
								dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					else
					{
						editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceInvalidsExpunged(
								entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
								dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					break;

				default:
					System.err.println(PopUps.getTracedErrorMsg(
							"Error in getEditDistancesForCandidateTimelineFullCand: unidentified case type"
									+ caseType));
					break;
			}

			candEditDistances.put(candidateTimelineId, editDistanceForThisCandidate);
			// System.out.println("now we put "+entry.getKey()+" and score="+score);
		}
		return candEditDistances;
	}

	/// Start of added on 9 Aug 2017
	// ////////
	/*
	 * Fork of getHJEditDistancesForCandidateTimelinesFullCand() <p> Added: Aug 9, 2017: for better performance
	 * (parallel) and memorising edit distance computations<p>
	 * 
	 * IMPORTANT POINT: THE CANDIDATE TIMELINE IS THE DIRECT CANDIDATE TIMELINE AND NOT THE LEAST DISTANT
	 * SUBCANDIDATE.<p> Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the
	 * edit distance of that candidate timeline with the current timeline.
	 * 
	 * @param candidateTimelines
	 * 
	 * @param activitiesGuidingRecomm
	 * 
	 * @param caseType can be 'SimpleV3' or 'CaseBasedV1'
	 * 
	 * @param userAtRecomm used only for writing to file
	 * 
	 * @param dateAtRecomm used only for writing to file
	 * 
	 * @param timeAtRecomm used only for writing to file
	 * 
	 * @param hjEditDistance
	 * 
	 * @return {CanditateTimelineID, Pair{Trace,Edit distance of this candidate}}
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getHJEditDistsForCandsFullCandParallelWithMemory(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			HJEditDistance hjEditDistance, EditDistanceMemorizer editDistancesMemorizer)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, Pair<String, Double>> candEditDistances = new LinkedHashMap<>();

		candEditDistances = candidateTimelines.entrySet().parallelStream().collect(Collectors.toMap(
				e -> (String) e.getKey(),
				e -> (Pair<String, Double>) getEditDistances(e.getValue(), activitiesGuidingRecomm, userAtRecomm,
						dateAtRecomm, timeAtRecomm, e.getKey(), caseType, hjEditDistance, editDistancesMemorizer),
				(oldValue, newValue) -> newValue, LinkedHashMap::new));

		if (Constant.memorizeEditDistance)
		{
			String currentTimelineID = Timeline.getTimelineIDFromAOs(activitiesGuidingRecomm);
			// System.out.println("activitiesGuidingRecomm.size()=" + activitiesGuidingRecomm.size());
			// long t1 = System.currentTimeMillis();
			// for (Entry<String, Pair<String, Double>> candEditDist : candEditDistances.entrySet())
			// {
			// Constant.addToEditDistanceMemorizer(candEditDist.getKey(), currentTimelineID, candEditDist.getValue());
			// }
			// long t2 = System.currentTimeMillis();

			// Start of 10 Aug temp curtain 1
			candEditDistances.entrySet().stream()
					.forEach(e -> Constant.addToEditDistanceMemorizer(e.getKey(), currentTimelineID, e.getValue()));
			// End of 10 Aug temp curtain 1
			// long t3 = System.currentTimeMillis();
		}
		// System.out.println("Iter: " + (t2 - t1));
		// System.out.println("Stre: " + (t3 - t2));
		return candEditDistances;
	}

	/**
	 * Created to facilitate parallel computation of edit distances
	 * 
	 * @param candTimeline
	 * @param activitiesGuidingRecomm
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param candTimelineID
	 * @param hjEditDistance
	 * @param caseType
	 * @return
	 * @since 9 Aug 2017
	 */
	public static Pair<String, Double> getEditDistances(Timeline candTimeline,
			ArrayList<ActivityObject> activitiesGuidingRecomm, String userAtRecomm, String dateAtRecomm,
			String timeAtRecomm, String candTimelineID, CaseType caseType, HJEditDistance hjEditDistance,
			EditDistanceMemorizer editDistancesMemorizer)
	{
		Pair<String, Double> editDistanceForThisCandidate = null;

		switch (caseType)
		{
			case CaseBasedV1:
				if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
				// invalids are already expunged, no need to expunge again
				{
					editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceWithoutEndCurrentActivity(
							candTimeline.getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
							dateAtRecomm, timeAtRecomm, candTimeline.getTimelineID());
				}
				else
				{
					editDistanceForThisCandidate = hjEditDistance
							.getHJEditDistanceWithoutEndCurrentActivityInvalidsExpunged(
									candTimeline.getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
									dateAtRecomm, timeAtRecomm, candTimeline.getTimelineID());
				}
				break;

			case SimpleV3:// "SimpleV3":
				if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
				{
					editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceWithTrace(
							candTimeline.getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
							dateAtRecomm, timeAtRecomm, candTimeline.getTimelineID());
				}
				else
				{
					editDistanceForThisCandidate = hjEditDistance.getHJEditDistanceInvalidsExpunged(
							candTimeline.getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
							dateAtRecomm, timeAtRecomm, candTimeline.getTimelineID());
				}
				break;

			default:
				System.err.println(PopUps.getTracedErrorMsg(
						"Error in getEditDistancesForCandidateTimelineFullCand: unidentified case type" + caseType));
				break;
		}

		// editDistancesMemorizer.addToMemory(candTimelineID, Timeline.getTimelineIDFromAOs(activitiesGuidingRecomm),
		// editDistanceForThisCandidate);

		// Constant.addToEditDistanceMemorizer(candTimelineID, Timeline.getTimelineIDFromAOs(activitiesGuidingRecomm),
		// editDistanceForThisCandidate);

		return editDistanceForThisCandidate;
	}

	/// End of added on 9 Aug 2017

	/**
	 * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the edit distance of
	 * that candidate timeline with the current timeline.
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 *            activities from the current timeline
	 * @param caseType
	 *            can be 'SimpleV3' or 'CaseBasedV1'
	 * 
	 * @param userAtRecomm
	 *            used only for writing to file
	 * @param dateAtRecomm
	 *            used only for writing to file
	 * @param timeAtRecomm
	 *            used only for writing to file
	 * @return <CanditateTimelineID, <FeatureName,>Pair<Trace,Edit distance of this candidate>>>
	 */
	public static LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> getFeatureWiseEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			FeatureWiseEditDistance featureWiseEditDistance)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> candEditDistancesFeatureWise = new LinkedHashMap<>();

		for (Map.Entry<String, Timeline> entry : candidateTimelines.entrySet())
		{
			LinkedHashMap<String, Pair<String, Double>> featureWiseEditDistancesForThisCandidate = null;
			String candidateTimelineId = entry.getKey();

			switch (caseType)
			{
				case CaseBasedV1:// "CaseBasedV1":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS) // invalids are already expunged, no need to
																		// expunge
																		// again
					{
						featureWiseEditDistancesForThisCandidate = featureWiseEditDistance
								.getFeatureWiseEditDistanceWithoutEndCurrentActivity(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					else
					{
						featureWiseEditDistancesForThisCandidate = featureWiseEditDistance
								.getFeatureWiseEditDistanceWithoutEndCurrentActivityInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					break;

				case SimpleV3:// "SimpleV3":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithTrace(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
					{
						featureWiseEditDistancesForThisCandidate = featureWiseEditDistance
								.getFeatureWiseEditDistanceWithTrace(entry.getValue().getActivityObjectsInTimeline(),
										activitiesGuidingRecomm);// ,
																	// userAtRecomm,
																	// dateAtRecomm,
																	// timeAtRecomm,
																	// candidateTimelineId);
					}
					else
					{
						featureWiseEditDistancesForThisCandidate = featureWiseEditDistance
								.getFeatureWiseEditDistanceInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);// ,
																													// userAtRecomm,
																													// dateAtRecomm,
																													// timeAtRecomm,
																													// candidateTimelineId);
					}
					break;

				default:
					System.err.println("Error in getEditDistancesForCandidateTimelineFullCand: unidentified case type");
					break;
			}
			/*
			 * if(caseType.equals("CaseBasedV1")) { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),
			 * activitiesGuidingRecomm); } else //SimpleV3 { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistance(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
			 * }
			 */
			candEditDistancesFeatureWise.put(candidateTimelineId, featureWiseEditDistancesForThisCandidate);
			// System.out.println("now we put "+entry.getKey()+" and score="+score);
		}
		return candEditDistancesFeatureWise;
	}

	/**
	 * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the OTMDSAM edit
	 * distance of that candidate timeline with the current timeline.
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 *            can be 'SimpleV3' or 'CaseBasedV1'
	 * 
	 * @param userAtRecomm
	 *            used only for writing to file
	 * @param dateAtRecomm
	 *            used only for writing to file
	 * @param timeAtRecomm
	 *            used only for writing to file
	 * @param OTMDSAMEditDistance
	 * @return <CanditateTimelineID, Pair<Trace,Edit distance of this candidate>>
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getOTMDSAMEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			OTMDSAMEditDistance OTMDSAMEditDistance)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, Pair<String, Double>> candEditDistances = new LinkedHashMap<>();

		for (Map.Entry<String, Timeline> entry : candidateTimelines.entrySet())
		{
			// EditSimilarity editSimilarity = new EditSimilarity();
			Pair<String, Double> editDistanceForThisCandidate = null;
			String candidateTimelineId = entry.getKey();

			switch (caseType)
			{
				case CaseBasedV1:// "CaseBasedV1":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS) // invalids are already expunged, no need to
																		// expunge
																		// again
					{
						editDistanceForThisCandidate = OTMDSAMEditDistance
								.getOTMDSAMEditDistanceWithoutEndCurrentActivity(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm,
										userAtRecomm, dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					else
					{
						editDistanceForThisCandidate = OTMDSAMEditDistance
								.getOTMDSAMEditDistanceWithoutEndCurrentActivityInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm,
										userAtRecomm, dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					break;

				case SimpleV3:// "SimpleV3":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithTrace(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
					{
						editDistanceForThisCandidate = OTMDSAMEditDistance.getOTMDSAMEditDistanceWithTrace(
								entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
								dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					else
					{
						editDistanceForThisCandidate = OTMDSAMEditDistance.getOTMDSAMEditDistanceInvalidsExpunged(
								entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm, userAtRecomm,
								dateAtRecomm, timeAtRecomm, candidateTimelineId);
					}
					break;

				default:
					System.err.println("Error in getEditDistancesForCandidateTimelineFullCand: unidentified case type");
					break;
			}
			/*
			 * if(caseType.equals("CaseBasedV1")) { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),
			 * activitiesGuidingRecomm); } else //SimpleV3 { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistance(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
			 * }
			 */
			candEditDistances.put(candidateTimelineId, editDistanceForThisCandidate);
			// System.out.println("now we put "+entry.getKey()+" and score="+score);
		}
		return candEditDistances;
	}

	/**
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param featureWiseWeightedEditDistance
	 * @return
	 */
	public static LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> getFeatureWiseWeightedEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			FeatureWiseWeightedEditDistance featureWiseWeightedEditDistance)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> candEditDistancesFeatureWise = new LinkedHashMap<>();

		for (Map.Entry<String, Timeline> entry : candidateTimelines.entrySet())
		{
			LinkedHashMap<String, Pair<String, Double>> featureWiseWeightedEditDistancesForThisCandidate = null;
			String candidateTimelineId = entry.getKey();

			switch (caseType)
			{
				case CaseBasedV1:// "CaseBasedV1":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS) // invalids are already expunged, no need to
																		// expunge
																		// again
					{
						featureWiseWeightedEditDistancesForThisCandidate = featureWiseWeightedEditDistance
								.getFeatureWiseWeightedEditDistanceWithoutEndCurrentActivity(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					else
					{
						featureWiseWeightedEditDistancesForThisCandidate = featureWiseWeightedEditDistance
								.getFeatureWiseWeightedEditDistanceWithoutEndCurrentActivityInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					break;

				case SimpleV3:// "SimpleV3":
					// editDistanceForThisCandidate =
					// editSimilarity.getEditDistanceWithTrace(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
					if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
					{
						featureWiseWeightedEditDistancesForThisCandidate = featureWiseWeightedEditDistance
								.getFeatureWiseWeightedEditDistanceRawValsWithTrace(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					else
					{
						featureWiseWeightedEditDistancesForThisCandidate = featureWiseWeightedEditDistance
								.getFeatureWiseWeightedEditDistanceInvalidsExpunged(
										entry.getValue().getActivityObjectsInTimeline(), activitiesGuidingRecomm);
					}
					break;

				default:
					System.err.println("Error in getEditDistancesForCandidateTimelineFullCand: unidentified case type");
					break;
			}
			/*
			 * if(caseType.equals("CaseBasedV1")) { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistanceWithoutEndCurrentActivity(entry.getValue().getActivityObjectsInTimeline(),
			 * activitiesGuidingRecomm); } else //SimpleV3 { editDistanceForThisCandidate =
			 * editSimilarity.getEditDistance(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
			 * }
			 */
			candEditDistancesFeatureWise.put(candidateTimelineId, featureWiseWeightedEditDistancesForThisCandidate);
			// System.out.println("now we put "+entry.getKey()+" and score="+score);
		}
		return candEditDistancesFeatureWise;
	}

	/**
	 * Checks for the distance used and calls the appropriate method for getting normalised distance for candidate
	 * timelines.
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userIDAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param distanceUsed
	 * @param hjEditDistance
	 * @param featureWiseEditDistance
	 * @param featureWiseWeightedEditDistance
	 * @param OTMDSAMEditDistance
	 * @return {CanditateTimelineID, Pair{Trace,Edit distance of this candidate}}
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getNormalisedDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userIDAtRecomm, String dateAtRecomm, String timeAtRecomm,
			String distanceUsed, HJEditDistance hjEditDistance, FeatureWiseEditDistance featureWiseEditDistance,
			FeatureWiseWeightedEditDistance featureWiseWeightedEditDistance, OTMDSAMEditDistance OTMDSAMEditDistance,
			EditDistanceMemorizer editDistancesMemorizer)
	{

		switch (distanceUsed)
		{
			case "HJEditDistance":
				return getNormalisedHJEditDistancesForCandidateTimelinesFullCand(candidateTimelines,
						activitiesGuidingRecomm, caseType, userIDAtRecomm, dateAtRecomm.toString(),
						timeAtRecomm.toString(), hjEditDistance, editDistancesMemorizer);
			case "FeatureWiseEditDistance":
				return getNormalisedFeatureWiseEditDistancesForCandidateTimelinesFullCand(candidateTimelines,
						activitiesGuidingRecomm, caseType, userIDAtRecomm, dateAtRecomm.toString(),
						timeAtRecomm.toString(), featureWiseEditDistance);

			case "FeatureWiseWeightedEditDistance":
				return getNormalisedFeatureWiseWeightedEditDistancesForCandidateTimelinesFullCand(candidateTimelines,
						activitiesGuidingRecomm, caseType, userIDAtRecomm, dateAtRecomm.toString(),
						timeAtRecomm.toString(), featureWiseWeightedEditDistance);

			case "OTMDSAMEditDistance":
				return getNormalisedOTMDSAMEditDistancesForCandidateTimelinesFullCand(candidateTimelines,
						activitiesGuidingRecomm, caseType, userIDAtRecomm, dateAtRecomm.toString(),
						timeAtRecomm.toString(), OTMDSAMEditDistance);
			default:
				PopUps.showError(
						"Error in org.activity.recomm.RecommendationMasterMU.getNormalisedDistancesForCandidateTimelinesFullCand():Unknown distance specified:"
								+ distanceUsed);
				System.err.println(PopUps.getTracedErrorMsg(
						"Error in org.activity.recomm.RecommendationMasterMU.getNormalisedDistancesForCandidateTimelinesFullCand(): Unknown distance specified:"
								+ distanceUsed));
				// throw new Exception("Error in org.activity.util.Constant.setDistanceUsed(String): Unknown distance
				// specified:" + dname);
				System.exit(-1);
		}
		System.err.println(PopUps.getTracedErrorMsg(
				"Error in org.activity.recomm.RecommendationMasterMU.getNormalisedDistancesForCandidateTimelinesFullCand()"
						+ " reaching unreachable code"));
		System.exit(-2);
		return null;
	}

	/**
	 * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the edit distance of
	 * that candidate timeline with the current timeline.
	 * 
	 * DOING THE NORMALISATION HERE ITSELF AND SEE IF IT GIVES DIFFERENT RESULT THAN DOING NORMALISATION WHILE
	 * CALCULATING SCORE. CHECKED: SAME RESULTS, NORMALISATION CORRECT
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 *            can be 'SimpleV3' or 'CaseBasedV1'
	 * 
	 * @param userAtRecomm
	 *            used only for writing to file
	 * @param dateAtRecomm
	 *            used only for writing to file
	 * @param timeAtRecomm
	 *            used only for writing to file
	 * @param hjEditDistance
	 * @return {CanditateTimelineID, Pair{Trace,Edit distance of this candidate}}
	 */

	public static LinkedHashMap<String, Pair<String, Double>> getNormalisedHJEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			HJEditDistance hjEditDistance, EditDistanceMemorizer editDistancesMemorizer)
	{
		// {CanditateTimelineID, Pair{Trace,Edit distance of this candidate}}

		// CurtainA start
		// long t1 = System.currentTimeMillis();
		// LinkedHashMap<String, Pair<String, Double>> candEditDistances =
		// getHJEditDistancesForCandidateTimelinesFullCand(
		// candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, dateAtRecomm, timeAtRecomm,
		// hjEditDistance);
		// long t2 = System.currentTimeMillis();
		//// CurtainA end

		// long t3 = System.currentTimeMillis();
		LinkedHashMap<String, Pair<String, Double>> candEditDistances/* Parallel */ = getHJEditDistsForCandsFullCandParallelWithMemory(
				candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, dateAtRecomm, timeAtRecomm,
				hjEditDistance, editDistancesMemorizer);
		// long t4 = System.currentTimeMillis();

		// Start Sanity check
		// System.out.println("Debug Aug 8 :1");
		// System.out.println("getHJEditDistancesForCandidateTimelinesFullCand = \t" + (t2 - t1) + " ms");
		// System.out.println("getHJEditDistsForCandsFullCandParallelWithMemory = \t" + (t4 - t3) + " ms");
		//
		// System.out.println("candEditDistances.size() = \t" + candEditDistances.size());
		// System.out.println("candEditDistancesParallel.size() = \t" + candEditDistancesParallel.size());
		//
		// System.out.println("candEditDistances.equals(candEditDistancesParallel) =\t"
		// + candEditDistances.equals(candEditDistancesParallel));
		//
		// if (candEditDistances.equals(candEditDistancesParallel) == false)
		// {
		// PopUps.printTracedErrorMsg("candEditDistances.equals(candEditDistancesParallel)==false");
		// }
		// // StringBuilder sbTemp1 = new StringBuilder();
		// // sbTemp1.append("candEditDistances:\n");
		// // candEditDistances.entrySet().stream()
		// // .forEachOrdered(e -> sbTemp1.append(e.getKey() + "--" + e.getValue() + "\n"));
		// // sbTemp1.append("candEditDistancesParallel:\n");
		// // candEditDistancesParallel.entrySet().stream()
		// // .forEachOrdered(e -> sbTemp1.append(e.getKey() + "--" + e.getValue() + "\n"));
		// // System.out.println(sbTemp1.toString());
		// End Sanity check
		System.out.println("before filter candEditDistances.size():" + candEditDistances.size());
		if (Constant.nearestNeighbourCandEDThreshold > 0)
		{
			System.out.print("... filtering");
			LinkedHashMap<String, Pair<String, Double>> candEditDistancesSorted = (LinkedHashMap<String, Pair<String, Double>>) ComparatorUtils
					.sortByValueAscendingStrStrDoub(candEditDistances);

			LinkedHashMap<String, Pair<String, Double>> candEditDistancesSortedFiltered = new LinkedHashMap<>();

			int c = 0;
			for (Entry<String, Pair<String, Double>> candEntry : candEditDistancesSorted.entrySet())
			{
				c++;
				if (c > Constant.nearestNeighbourCandEDThreshold)
				{
					break;
				}
				candEditDistancesSortedFiltered.put(candEntry.getKey(), candEntry.getValue());
			}
			candEditDistances = candEditDistancesSortedFiltered;
		}

		System.out.println("after filter candEditDistances.size():" + candEditDistances.size());
		LinkedHashMap<String, Pair<String, Double>> normalisedCandEditDistances = normalisedDistancesOverTheSet(
				candEditDistances, userAtRecomm, dateAtRecomm, timeAtRecomm);

		return normalisedCandEditDistances;
	}

	/**
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param featureWiseEditDistance
	 * @return
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getNormalisedFeatureWiseEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			FeatureWiseEditDistance featureWiseEditDistance)
	{
		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> candEditDistancesFeatureWise = getFeatureWiseEditDistancesForCandidateTimelinesFullCand(
				candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, dateAtRecomm, timeAtRecomm,
				featureWiseEditDistance);

		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> normalisedCandEditDistances = normalisedFeatureWiseDistancesOverTheSet(
				candEditDistancesFeatureWise);
		LinkedHashMap<String, Pair<String, Double>> aggregatedNormalisedCandEditDistances = aggregatedFeatureWiseDistancesForCandidateTimelinesFullCand(
				normalisedCandEditDistances);

		if (VerbosityConstants.verboseNormalisation)
		{
			UtilityBelt.traverseStringStringPair(normalisedCandEditDistances,
					" Normalised Feature wise Edit Distances");
			UtilityBelt.traverseStringPair(aggregatedNormalisedCandEditDistances,
					"Aggregated Normalised  Feature wise Edit Distances");
		}
		return aggregatedNormalisedCandEditDistances;
	}

	/**
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param OTMDSAMEditDistance
	 * @return
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getNormalisedOTMDSAMEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			OTMDSAMEditDistance OTMDSAMEditDistance)
	{
		LinkedHashMap<String, Pair<String, Double>> candEditDistances = getOTMDSAMEditDistancesForCandidateTimelinesFullCand(
				candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, dateAtRecomm, timeAtRecomm,
				OTMDSAMEditDistance);

		LinkedHashMap<String, Pair<String, Double>> normalisedCandEditDistances = normalisedDistancesOverTheSet(
				candEditDistances, userAtRecomm, dateAtRecomm, timeAtRecomm);

		return normalisedCandEditDistances;
	}

	/**
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 * @param userAtRecomm
	 * @param dateAtRecomm
	 * @param timeAtRecomm
	 * @param featureWiseWeightedEditDistance
	 * @return
	 */
	public static LinkedHashMap<String, Pair<String, Double>> getNormalisedFeatureWiseWeightedEditDistancesForCandidateTimelinesFullCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, String userAtRecomm, String dateAtRecomm, String timeAtRecomm,
			FeatureWiseWeightedEditDistance featureWiseWeightedEditDistance)
	{
		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> candEditDistancesFeatureWise = getFeatureWiseWeightedEditDistancesForCandidateTimelinesFullCand(
				candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, dateAtRecomm, timeAtRecomm,
				featureWiseWeightedEditDistance);

		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> normalisedCandEditDistances = normalisedFeatureWiseDistancesOverTheSet(
				candEditDistancesFeatureWise);
		LinkedHashMap<String, Pair<String, Double>> aggregatedNormalisedCandEditDistances = aggregatedFeatureWiseDistancesForCandidateTimelinesFullCand(
				normalisedCandEditDistances);

		if (VerbosityConstants.verboseNormalisation)
		{
			UtilityBelt.traverseStringStringPair(normalisedCandEditDistances, " Normalised Weighted Edit Distances");
			UtilityBelt.traverseStringPair(aggregatedNormalisedCandEditDistances,
					"Aggregated Normalised Weighted Edit Distances");
		}
		return aggregatedNormalisedCandEditDistances;
	}

	/**
	 * 
	 * @param setOfFeatureWiseDistances
	 * @return
	 */
	public static LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> normalisedFeatureWiseDistancesOverTheSet(
			LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> setOfFeatureWiseDistances)
	{

		LinkedHashMap<String, LinkedHashMap<String, Pair<String, Double>>> normalisedDistancesPerCand = new LinkedHashMap<>();

		int numOfFeatures = Constant.getNumberOfFeatures();

		double[] maxs = new double[numOfFeatures];// maxs for each feature
		double[] mins = new double[numOfFeatures];

		for (int i = 0; i < numOfFeatures; i++)
		{
			maxs[i] = Constant.minForNorm;
			mins[i] = Constant.maxForNorm;
		}

		for (Map.Entry<String, LinkedHashMap<String, Pair<String, Double>>> entry : setOfFeatureWiseDistances
				.entrySet()) // iterating over cands
		{// String candID = entry.getKey();
			LinkedHashMap<String, Pair<String, Double>> featureWiseDistances = entry.getValue();

			int featureIndex = 0;

			// iterating over distance for each feature
			for (Map.Entry<String, Pair<String, Double>> distEntry : featureWiseDistances.entrySet())
			{
				String featureName = distEntry.getKey();
				double distanceValue = distEntry.getValue().getSecond();
				if (VerbosityConstants.verboseNormalisation)
					System.out.println("reading:" + featureName + "  distance:" + distanceValue);

				if (distanceValue > maxs[featureIndex])
				{
					maxs[featureIndex] = distanceValue;
					// if (Constant.verboseNormalisation)
					// System.out.println("maxs[" + featureIndex + "] = " + distanceValue);
				}
				// else
				// {
				// if (Constant.verboseNormalisation)
				// System.out.println("no effect max");
				// }

				if (distanceValue < mins[featureIndex])
				{
					mins[featureIndex] = distanceValue;
					// if (Constant.verboseNormalisation)
					// System.out.println("mins[" + featureIndex + "] = " + distanceValue);
				}

				// else
				// {
				// if (Constant.verboseNormalisation)
				// System.out.println("no effect min");
				// }
				featureIndex++;
			}
		}
		System.out.print("Before normalisation:\n");
		if (VerbosityConstants.verboseNormalisation)
		{// iterating over cands
			for (Map.Entry<String, LinkedHashMap<String, Pair<String, Double>>> entry : setOfFeatureWiseDistances
					.entrySet())
			{
				System.out.print("Cand id:" + entry.getKey() + "-");
				LinkedHashMap<String, Pair<String, Double>> featureWiseDistances = entry.getValue();

				// iterating over distance for each feature
				for (Map.Entry<String, Pair<String, Double>> distEntry : featureWiseDistances.entrySet())
				{
					System.out.print(distEntry.getKey() + ":" + distEntry.getValue().getSecond() + " ");
				}
				System.out.println();
			}

			for (int k = 0; k < numOfFeatures; k++)
			{
				System.out.println(" max for " + (k + 1) + "th feature=" + maxs[k]);
				System.out.println(" min for " + (k + 1) + "th feature=" + mins[k]);
			}
		}

		// LinkedHashMap<Integer, LinkedHashMap<String, Pair<String, Double>>> normalisedDistancesPerCand

		for (Map.Entry<String, LinkedHashMap<String, Pair<String, Double>>> entry : setOfFeatureWiseDistances
				.entrySet()) // iterating over cands
		{
			String candID = entry.getKey();
			LinkedHashMap<String, Pair<String, Double>> featureWiseDistances = entry.getValue();
			LinkedHashMap<String, Pair<String, Double>> normalisedFeatureWiseDistances = new LinkedHashMap<String, Pair<String, Double>>();

			int featureIndex = 0;
			for (Map.Entry<String, Pair<String, Double>> distEntry : featureWiseDistances.entrySet())
			// iterating over distance for each feature
			{
				String featureName = distEntry.getKey();
				double distanceValue = distEntry.getValue().getSecond();
				double normalisedDistanceValue = StatsUtils.minMaxNorm(distanceValue, maxs[featureIndex],
						mins[featureIndex]);
				normalisedFeatureWiseDistances.put(featureName,
						new Pair<String, Double>(distEntry.getValue().getFirst(), normalisedDistanceValue));
				featureIndex++;
			}
			normalisedDistancesPerCand.put(candID, normalisedFeatureWiseDistances);
		}

		return normalisedDistancesPerCand;
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

	/**
	 * Normalises the given edit distance over the candidates, i.e., does a min-max normalisation over the set of edit
	 * distance passed to it.
	 * 
	 * @param setOfDistances
	 * @param userAtRecomm
	 *            just for writing to file
	 * @param dateAtRecomm
	 *            just for writing to file
	 * @param timeAtRecomm
	 *            just for writing to file
	 * @return distances normalised over the set.
	 */
	public static LinkedHashMap<String, Pair<String, Double>> normalisedDistancesOverTheSet(
			LinkedHashMap<String, Pair<String, Double>> setOfDistances, String userAtRecomm, String dateAtRecomm,
			String timeAtRecomm)
	{
		LinkedHashMap<String, Pair<String, Double>> normalisedDistances = new LinkedHashMap<>();

		double min = Double.MAX_VALUE, max = Double.MIN_VALUE;

		int numOfValsAtMax = 0, numOfValsAtMin = 0;
		// StringBuilder editDistancesLog = new StringBuilder();// , normalisedEditDistancesLog = new StringBuilder();
		ArrayList<Double> editDistancesLogList = new ArrayList<>();
		ArrayList<Double> normalisedEditDistancesLogList = new ArrayList<>();

		// find the max and min and how many at max and min
		int i = 0;
		for (Map.Entry<String, Pair<String, Double>> distEntry : setOfDistances.entrySet())
		{
			// Integer candTimelineID = distEntry.getKey();
			Double editDistanceVal = distEntry.getValue().getSecond();

			if (VerbosityConstants.WriteNormalisation)
			{
				// editDistancesLog.append("_" + editDistanceVal);
				editDistancesLogList.add(editDistanceVal);
			}
			if (editDistanceVal < min)
			{
				min = editDistanceVal;
				numOfValsAtMin = 1;
			}
			else if (editDistanceVal == min)
			{
				numOfValsAtMin++;
			}

			if (editDistanceVal > max)
			{
				max = editDistanceVal;
				numOfValsAtMax = 1;
			}
			else if (editDistanceVal == max)
			{
				numOfValsAtMax++;
			}
			i++;
		}

		for (Map.Entry<String, Pair<String, Double>> distEntry : setOfDistances.entrySet())
		{
			Double normalisedEditDistanceVal = Double
					.valueOf(StatsUtils.minMaxNorm(distEntry.getValue().getSecond(), max, min));

			// if (normalisedEditDistanceVal == 0)
			// {
			// System.out.println("Debug: normalisedEditDistanceVal=" + normalisedEditDistanceVal
			// + " distEntry.getValue().getSecond()= " + distEntry.getValue().getSecond() + " max=" + max
			// + " min=" + min);
			// }

			if (VerbosityConstants.WriteNormalisation)
			{
				// normalisedEditDistancesLog.append("_" + normalisedEditDistanceVal);
				normalisedEditDistancesLogList.add(normalisedEditDistanceVal);
			}
			normalisedDistances.put(distEntry.getKey(),
					new Pair<String, Double>(distEntry.getValue().getFirst(), normalisedEditDistanceVal));
		}

		if (VerbosityConstants.WriteNormalisation && !VerbosityConstants.WriteNormalisationsSeparateLines)
		{
			Collections.sort(normalisedEditDistancesLogList);
			Collections.sort(editDistancesLogList);
			String toWrite = userAtRecomm + "||" + dateAtRecomm + "||" + timeAtRecomm + "||" + editDistancesLogList
					+ "||" + normalisedEditDistancesLogList + "\n";
			WritingToFile.appendLineToFileAbsolute(toWrite, Constant.getCommonPath() + "NormalisationDistances.csv");
		}

		if (VerbosityConstants.WriteNormalisationsSeparateLines)
		{
			Collections.sort(normalisedEditDistancesLogList);
			Collections.sort(editDistancesLogList);
			int j = 0;
			for (Double raw : editDistancesLogList)
			{
				String toWrite = userAtRecomm + "," + dateAtRecomm + "," + timeAtRecomm + "," + raw + ","
						+ normalisedEditDistancesLogList.get(j) + "\n";
				WritingToFile.appendLineToFileAbsolute(toWrite,
						Constant.getCommonPath() + "NormalisationDistances.csv");
				j++;
			}

		}

		System.out.println(
				"getNormalisedDistancesOverTheSet: #Vals max=" + numOfValsAtMax + " #Vals min=" + numOfValsAtMin);
		return normalisedDistances;
	}

	// ////////
	/*
	 * Added: Oct 7, 2014: for IMPORTANT POINT: THE CANDIDATE TIMELINE IS THE DIRECT CANDIDATE TIMELINE AND NOT THE
	 * LEAST DISTANT SUBCANDIDATE.
	 */
	/**
	 * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the edit distance
	 * value between the end point activity object (current activity) of the candidate timeline and the current activity
	 * object (activity at recomm point)
	 * 
	 * @param candidateTimelines
	 * @param activitiesGuidingRecomm
	 * @param caseType
	 *            can be 'SimpleV3' or 'CaseBasedV1'
	 * @param userID
	 *            user for which recommendation is being done
	 * @param dateAtRecomm
	 *            only for the purpose of writing to file
	 * @param timeAtRecomm
	 *            only for the purpose of writing to file
	 * @param alignmentBasedDistance
	 * @return <CanditateTimelineID to which this end point Activity Object belongs, edit distance of this end point
	 *         Activity Object of this candidate with end point Activity Object>
	 */
	public LinkedHashMap<String, Double> getCaseSimilarityEndPointActivityObjectCand(
			LinkedHashMap<String, Timeline> candidateTimelines, ArrayList<ActivityObject> activitiesGuidingRecomm,
			Enums.CaseType caseType, int userID, String dateAtRecomm, String timeAtRecomm,
			AlignmentBasedDistance alignmentBasedDistance)
	{
		// <CandidateTimeline ID, Edit distance>
		LinkedHashMap<String, Double> candEndPointEditDistances = new LinkedHashMap<>();

		for (Map.Entry<String, Timeline> candEntry : candidateTimelines.entrySet())
		{
			ArrayList<ActivityObject> activityObjectsInCand = candEntry.getValue().getActivityObjectsInTimeline();
			Double endPointEditDistanceForThisCandidate = new Double(-9999);

			if (caseType.equals(Enums.CaseType.CaseBasedV1))// "CaseBasedV1")) // CaseBasedV1
			{
				ActivityObject endPointActivityObjectCandidate = (activityObjectsInCand
						.get(activityObjectsInCand.size() - 1)); // only the end point activity
																	// object
				ActivityObject endPointActivityObjectCurrentTimeline = (activitiesGuidingRecomm
						.get(activitiesGuidingRecomm.size() - 1)); // activityObjectAtRecommPoint

				switch (Constant.getDatabaseName()) // (Constant.DATABASE_NAME)
				{
					case "geolife1":
						endPointEditDistanceForThisCandidate = alignmentBasedDistance
								.getCaseBasedV1SimilarityGeolifeData(endPointActivityObjectCandidate,
										endPointActivityObjectCurrentTimeline, userID);
						break;
					case "dcu_data_2":
						endPointEditDistanceForThisCandidate = alignmentBasedDistance.getCaseBasedV1SimilarityDCUData(
								endPointActivityObjectCandidate, endPointActivityObjectCurrentTimeline, userID);
						break;
					default:
						System.err.println(PopUps.getTracedErrorMsg(
								"Error in getCaseSimilarityEndPointActivityObjectCand: unrecognised database name"));
						break;
				}

			}
			else if (caseType.equals(Enums.CaseType.SimpleV3))// "SimpleV3"))
			{
				// editDistanceForThisCandidate =
				// editSimilarity.getEditDistance(entry.getValue().getActivityObjectsInTimeline(),activitiesGuidingRecomm);
				System.err.println(PopUps.getTracedErrorMsg(
						"ERROR in getEditDistancesOfEndPointActivityCand(): This method should not have been called for case type="
								+ caseType));
				errorExists = true;
				endPointEditDistanceForThisCandidate = null;
			}
			else
			{
				System.err.println(PopUps.getTracedErrorMsg(
						"ERROR in getEditDistancesOfEndPointActivityCand(): This method should not have been called for case type="
								+ caseType));
				errorExists = true;
				endPointEditDistanceForThisCandidate = null;
			}

			if (endPointEditDistanceForThisCandidate < 0)
			{
				System.err.println(PopUps.getTracedErrorMsg(
						"Error in getCaseSimilarityEndPointActivityObjectCand: endPointEditDistanceForThisCandidate "
								+ endPointEditDistanceForThisCandidate + " is not correct"));
				System.exit(-99);
			}

			candEndPointEditDistances.put(candEntry.getKey(), endPointEditDistanceForThisCandidate);
			// System.out.println("now we put "+entry.getKey()+" and score="+score);
		}

		return candEndPointEditDistances;
	}

	// /

	public static String getStringCodeOfActivityObjects(ArrayList<ActivityObject> activityObjects)
	{
		StringBuilder code = new StringBuilder();
		for (ActivityObject ao : activityObjects)
		{
			code.append(ao.getCharCode());
		}
		return code.toString();
	}

	////

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

	public boolean hasError()
	{
		return this.errorExists;
	}

	public void setErrorExists(boolean exists)
	{
		this.errorExists = exists;
	}

	/// Start of Methods for interface

	public int getNumOfCandidateTimelines()
	{
		return this.candidateTimelines.size();
	}

	public int getNumOfActsInActsGuidingRecomm()
	{
		return this.activitiesGuidingRecomm.size();
	}

	public int getNumOfValidActsInActsGuidingRecomm()
	{
		int count = 0;
		for (ActivityObject ae : this.activitiesGuidingRecomm)
		{
			if (UtilityBelt.isValidActivityName(ae.getActivityName()))
			{
				count++;
			}
		}
		return count++;
	}

	public Timeline getCandidateTimeline(String timelineID)
	{
		return this.candidateTimelines.get(timelineID);
	}

	public ArrayList<Timeline> getOnlyCandidateTimeslines()
	{
		return (ArrayList<Timeline>) this.candidateTimelines.entrySet().stream().map(e -> (Timeline) e.getValue())
				.collect(Collectors.toList());
	}

	public Set<String> getCandidateTimelineIDs()
	{
		return this.candidateTimelines.keySet();
	}

	public boolean hasCandidateTimeslines()
	{
		return hasCandidateTimelines;
	}

	public boolean hasCandidateTimelinesBelowThreshold()
	{
		return hasCandidateTimelinesBelowThreshold;
	}

	public boolean hasThresholdPruningNoEffect()
	{
		return thresholdPruningNoEffect;
	}

	public boolean isNextActivityJustAfterRecommPointIsInvalid()
	{
		return this.nextActivityJustAfterRecommPointIsInvalid;
	}

	public ActivityObject getActivityObjectAtRecomm()
	{
		return this.activityObjectAtRecommPoint;
	}

	/**
	 * 
	 * @return
	 */
	public double getThresholdAsDistance()
	{
		return thresholdAsDistance;
	}

	public int getNumOfCandTimelinesBelowThresh() // satisfying threshold
	{
		// if(hasCandidateTimelinesBelowThreshold==false)
		// {
		// System.err.println("Error: Sanity Check RM60 failed: trying to get number of candidate timelines below
		// threshold while there is no candidate below threshold, u shouldnt
		// have called this function");
		// }

		if (Constant.altSeqPredictor == Enums.AltSeqPredictor.AKOM)
		{
			return candidateTimelines.size();
		}
		else
		{
			/*
			 * Assuming that threshold has already been applied
			 */
			return this.distancesSortedMap.size();
		}

	}

	/**
	 * Returns next activity names as String
	 * 
	 * @return
	 */
	public String getNextActNamesWithoutDistString()
	{// LinkedHashMap<String, Pair<ActivityObject, Double>>
		StringBuilder result = new StringBuilder("");
		nextActivityObjectsFromCands.entrySet().stream()
				.forEach(e -> result.append("__" + e.getValue().getFirst().getActivityName()));
		return result.toString();
	}

	/**
	 * Returns next activity names with distance as String
	 * 
	 * @return
	 */
	public String getNextActNamesWithDistString()
	{// LinkedHashMap<String, Pair<ActivityObject, Double>>
		StringBuilder result = new StringBuilder("");
		nextActivityObjectsFromCands.entrySet().stream().forEach(e -> result
				.append("__" + e.getValue().getFirst().getActivityName() + ":" + e.getValue().getSecond().toString()));
		return result.toString();
	}

	// Names not changed to PD vals because this method is inherited from abstract class
	public String getActivityNamesGuidingRecommwithTimestamps()
	{
		StringBuilder res = new StringBuilder();
		for (ActivityObject ae : activitiesGuidingRecomm)
		{
			res = StringUtils.fCat(res, "  ", ae.getActivityName(), "__", ae.getPrimaryDimensionVal().toString(), "__",
					ae.getStartTimestamp().toString(), "_to_", ae.getEndTimestamp().toString());
			// res.append(" " + ae.getActivityName() + "__" + ae.getStartTimestamp() + "_to_" + ae.getEndTimestamp());
		}
		return res.toString();
	}

	/**
	 * @return rankedRecommendedActivityPDValsithoutRankScoresString
	 */
	public String getRankedRecommendedActNamesWithoutRankScores()
	{
		return this.rankedRecommendedActNamesWithoutRankScoresStr;
	}

	public String getRankedRecommendedActNamesWithRankScores()
	{
		return this.rankedRecommendedActNamesWithRankScoresStr;
	}

	public int getNumOfDistinctRecommendations()
	{
		return recommendedActivityNamesWithRankscores.size();
	}

	public LinkedHashMap<String, Pair<String, Double>> getDistancesSortedMap()
	{
		return this.distancesSortedMap;
	}

	public LinkedHashMap<String, Integer> getEndPointIndicesConsideredInCands()
	{
		return endPointIndicesConsideredInCands;
	}

	public ArrayList<ActivityObject> getActsGuidingRecomm()
	{
		return activitiesGuidingRecomm;
	}
	/// end of methods for interface

}

/////////////////////////////////////////////////
/// **
// *
// * @param map
// * @return
// */
// public long getSumOfActivityObjects(LinkedHashMap<Integer, TimelineWithNext> map)
// {
// long count = 0;
//
// for (Map.Entry<Integer, TimelineWithNext> entry : map.entrySet())
// {
// int a = entry.getValue().countNumberOfValidActivities();
// int b = entry.getValue().size();
//
// if (a != b)
// {
// PopUps.showError(
// "Error in getSumOfActivityObjects a should be equal to be since we removed invalid aos beforehand but a = "
// + a + " and b=" + b);
// }
// count += a;
// }
// return count;
// }
// public static String getNextValidActivityNameAsCode(String topSimilarUserDayActivitiesAsStringCode,
// int endPointIndexForSubsequenceWithHighestSimilarity)
// {
// String unknownAsCode = StringCode.getStringCodeFromActivityName(Constant.INVALID_ACTIVITY1);// ("Unknown");
// String othersAsCode = StringCode.getStringCodeFromActivityName(Constant.INVALID_ACTIVITY2);// ("Others");
// String nextValidActivity = null;
//
// for (int i = endPointIndexForSubsequenceWithHighestSimilarity + 1; i < topSimilarUserDayActivitiesAsStringCode
// .length(); i++)
// {
// if (String.valueOf(topSimilarUserDayActivitiesAsStringCode.charAt(i)).equals(unknownAsCode)
// || String.valueOf(topSimilarUserDayActivitiesAsStringCode.charAt(i)).equals(othersAsCode))
// {
// continue;
// }
// else
// {
// nextValidActivity = String.valueOf(topSimilarUserDayActivitiesAsStringCode.charAt(i));
// }
// }
// return nextValidActivity;
// }

// End of curtain 9 march 2017: commented out because the other constructor is essentially doing the same thing but
// with fewer logging for performance reasons
// /**
// * Recommendation for a particular RT
// *
// * @param trainingTimelines
// * @param testTimelines
// * @param dateAtRecomm
// * @param timeAtRecomm
// * start time of the current activity, equivalent to using end time of the current activity
// * @param userAtRecomm
// * user for which recommendation is being generated
// * @param thresholdVal
// * @param typeOfThreshold
// * @param matchingUnitInCountsOrHours
// * @param caseType
// * @param lookPastType
// */
// public RecommendationMasterMUMar2017(LinkedHashMap<Date, UserDayTimeline> trainingTimelines,
// LinkedHashMap<Date, UserDayTimeline> testTimelines, String dateAtRecomm, String timeAtRecomm,
// int userAtRecomm, double thresholdVal, String typeOfThreshold, double matchingUnitInCountsOrHours,
// String caseType, String lookPastType // counts or hours
// )
// {
// String performanceFileName = "/run/media/gunjan/Space/GUNJAN/GeolifeSpaceSpace/April21/Test/Performance.csv";
// StringBuilder performanceString = new StringBuilder();
// long recommMasterT0 = System.currentTimeMillis();
// performanceString.append("Start:" + recommMasterT0);
// // hjEditDistance = new HJEditDistance();
// // alignmentBasedDistance = new AlignmentBasedDistance(); // used for case based similarity
// // featureWiseEditDistance = new FeatureWiseEditDistance();
// initialiseDistancesUsed();
//
// errorExists = false;
// this.lookPastType = lookPastType;
//
// LinkedHashMap<Integer, Pair<String, Double>> editDistancesMapUnsortedFullCand;
// // HJEditDistance dummy = new HJEditDistance();
// System.out.println("\n----------------Starting Recommender MasterMU " + lookPastType + "---------------------");
// this.matchingUnitInCountsOrHours = matchingUnitInCountsOrHours;
//
// this.hasCandidateTimelines = true;
// this.nextActivityJustAfterRecommPointIsInvalid = false;
//
// String[] splittedDate = dateAtRecomm.split("/"); // dd/mm/yyyy
// this.dateAtRecomm = new Date(Integer.parseInt(splittedDate[2]) - 1900, Integer.parseInt(splittedDate[1]) - 1,
// Integer.parseInt(splittedDate[0]));
// String[] splittedTime = timeAtRecomm.split(":"); // hh:mm:ss
// this.timeAtRecomm = Time.valueOf(timeAtRecomm);
// this.userAtRecomm = Integer.toString(userAtRecomm);
// this.userIDAtRecomm = Integer.toString(userAtRecomm);
// this.caseType = caseType;
//
// System.out.println(" User at Recomm = " + this.userAtRecomm);
// System.out.println(" Date at Recomm = " + this.dateAtRecomm);
// System.out.println(" Time at Recomm = " + this.timeAtRecomm);
//
// this.trainingTimeline = new Timeline(trainingTimelines); // converting day timelines into continuous timelines
// // suitable to be used for matching unit
// // views
// this.testTimeline = new Timeline(testTimelines);
//
// if (Constant.EXPUNGE_INVALIDS_B4_RECOMM_PROCESS)
// {
// this.trainingTimeline = TimelineUtils.expungeInvalids(trainingTimeline);
// this.testTimeline = TimelineUtils.expungeInvalids(testTimeline);
//
// System.out.println(
// "Expunging invalids before recommendation process: expunging from test and training timelines");
// }
//
// // //////////////////
// if (lookPastType.equalsIgnoreCase("Count"))
// {
// this.currentTimeline = TimelineWithNext.getCurrentTimelineFromLongerTimelineMUCount(testTimeline,
// this.dateAtRecomm, this.timeAtRecomm, this.userIDAtRecomm, this.matchingUnitInCountsOrHours);
// }
//
// else if (lookPastType.equalsIgnoreCase("Hrs"))
// {
// this.currentTimeline = TimelineWithNext.getCurrentTimelineFromLongerTimelineMUHours(testTimeline,
// this.dateAtRecomm, this.timeAtRecomm, this.userIDAtRecomm, this.matchingUnitInCountsOrHours);
// }
// else
// {
// System.err.println("Error: Unrecognised lookPastType in RecommendationMasterMUCount");
// System.exit(-154);
// }
// // ////////////////////
// if (currentTimeline == null)
// {
// System.err.println("Error: current timeline is empty");
// errorExists = true;
// }
//
// this.activitiesGuidingRecomm = currentTimeline.getActivityObjectsInTimeline(); // CURRENT TIMELINE
// System.out.println("Current timeline: " + currentTimeline.getActivityObjectNamesWithTimestampsInSequence() + "="
// + this.activitiesGuidingRecomm.size());
// System.out.print("Activities in the current timeline (as activities guiding recomm) are: \t");
// for (int i = 0; i < activitiesGuidingRecomm.size(); i++)
// {
// System.out.print(activitiesGuidingRecomm.get(i).getActivityName() + " ");
// }
//
// System.out.println("\nDebug note192: getActivityNamesGuidingRecommwithTimestamps() "
// + getActivityNamesGuidingRecommwithTimestamps() + "\n size of current timeline="
// + currentTimeline.getActivityObjectsInTimeline().size());
//
// this.activityAtRecommPoint = activitiesGuidingRecomm.get(activitiesGuidingRecomm.size() - 1); // current
// // ActivityObject
// this.activityNameAtRecommPoint = this.activityAtRecommPoint.getActivityName();
//
// System.out.println("\nActivity at Recomm point (Current Activity) =" + activityNameAtRecommPoint);//
// this.activityAtRecommPoint.getActivityName()
//
// // All check OK
// // //////////////////////////
// this.candidateTimelines = getCandidateTimelinesMU(trainingTimeline, matchingUnitInCountsOrHours, lookPastType);//
// ,this.dateAtRecomm);//,this.activitiesGuidingRecomm
//
// // ///////////////////////////
// System.out.println("Number of candidate timelines =" + candidateTimelines.size());
// // $$System.out.println("the candidate timelines are as follows:");
// // $$traverseMapOfTimelinesWithNext(candidateTimelines);
//
// if (candidateTimelines.size() == 0)
// {
// System.out.println("Warning: not making recommendation for " + userAtRecomm + " on date:" + dateAtRecomm
// + " at time:" + timeAtRecomm + " because there are no candidate timelines");
// // this.singleNextRecommendedActivity = null;
// this.hasCandidateTimelines = false;
// // this.topNextActivities =null;
// this.topNextActivityObjects = null;
// this.thresholdPruningNoEffect = true;
// return;
// }
// else
// {
// this.hasCandidateTimelines = true;
// }
// // System.out.println("\nDebug note192_223: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// // /////////////////////
// // TODO CHECK: HOW THE EFFECT OF THIS DIFFERS FROM THE EXPERIMENTS DONE FOR IIWAS: in iiWAS normalisation was
// // after thresholding (correct), here
// // normalisation is before thresholding which should be changed
// // TODO
// editDistancesMapUnsortedFullCand = getNormalisedDistancesForCandidateTimelinesFullCand(candidateTimelines,
// activitiesGuidingRecomm, caseType, this.userIDAtRecomm, this.dateAtRecomm.toString(),
// this.timeAtRecomm.toString(), Constant.getDistanceUsed());
//
// /*
// * Old getHJEditDistancesForCandidateTimelinesFullCand(candidateTimelines, activitiesGuidingRecomm, caseType,
// * this.userIDAtRecomm, this.dateAtRecomm.toString(), this.timeAtRecomm.toString());
// */
//
// // getDistanceScoresforCandidateTimelines(candidateTimelines,activitiesGuidingRecomm);
// // getNormalisedDistancesForCandidateTimelinesFullCand(candidateTimelines, activitiesGuidingRecomm, caseType,
// // this.userIDAtRecomm,
// // this.dateAtRecomm.toString(),
// // this.timeAtRecomm.toString(), Constant.getDistanceUsed());//
// // getDistanceScoresforCandidateTimelines(candidateTimelines,activitiesGuidingRecomm);
//
// // //////////////////////////////////
// // System.out.println("\nDebug note192_229: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// // ########Sanity check
// if (editDistancesMapUnsortedFullCand.size() != candidateTimelines.size())
// {
// System.err.println(
// "Error at Sanity 261 inside RecommendationMasterMU: editDistancesMapUnsorted.size() !=
// candidateTimelines.size()");
// errorExists = true;
// }
// // ##############
//
// // /// REMOVE candidate timelines which are above the distance THRESHOLD. (actually here we remove the entry for
// // such candidate timelines from the
// // distance scores map
// if (typeOfThreshold.equalsIgnoreCase("Global"))
// {
// this.thresholdAsDistance = thresholdVal;
// }
// else if (typeOfThreshold.equalsIgnoreCase("Percent"))
// {
// double maxEditDistance = (new AlignmentBasedDistance()).maxEditDistance(activitiesGuidingRecomm);
// this.thresholdAsDistance = maxEditDistance * (thresholdVal / 100);
// }
// else
// {
// System.err.println("Error: type of threshold unknown in recommendation master");
// errorExists = true;
// System.exit(-2);
// }
// // System.out.println("\nDebug note192_255: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// int countCandBeforeThresholdPruning = editDistancesMapUnsortedFullCand.size();// distanceScoresSorted.size();
//
// editDistancesMapUnsortedFullCand = TimelineUtils
// .removeAboveThreshold4FullCandISD(editDistancesMapUnsortedFullCand, thresholdAsDistance);// distanceScoresSorted=
// // UtilityBelt.removeAboveThreshold2(distanceScoresSorted,thresholdAsDistance);
// int countCandAfterThresholdPruning = editDistancesMapUnsortedFullCand.size();
//
// this.thresholdPruningNoEffect = (countCandBeforeThresholdPruning == countCandAfterThresholdPruning);
// // System.out.println("\nDebug note192_263: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// if (!thresholdPruningNoEffect)
// {
// PopUps.showMessage("Ohh..threshold pruning is happening. Are you sure you wanted this?");// +msg);
// }
// // ////////////////////////////////
//
// if (editDistancesMapUnsortedFullCand.size() == 0)
// {
// System.out.println("Warning: No candidate timelines below threshold distance");
// hasCandidateTimelinesBelowThreshold = false;
// return;
// }
//
// else
// {
// hasCandidateTimelinesBelowThreshold = true;
// }
//
// // //////////////////////////////
// // System.out.println("\nDebug note192_282: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// editDistancesSortedMapFullCand = (LinkedHashMap<Integer, Pair<String, Double>>) ComparatorUtils
// .sortByValueAscendingIntStrDoub(editDistancesMapUnsortedFullCand); // Now distanceScoresSorted
// // contains the String Id for
//
// if (caseType.equals("CaseBasedV1"))
// {
// System.out.println("this is CaseBasedV1");
// // this.editDistanceOfEndPointActivityObjectCand =
// //
// getEditDistancesOfEndPointActivityObjectCand(candidateTimelines,activitiesGuidingRecomm,caseType);//getDistanceScoresforCandidateTimelines(candidateTimelines,activitiesGuidingRecomm);
// this.similarityOfEndPointActivityObjectCand = getCaseSimilarityEndPointActivityObjectCand(
// candidateTimelines, activitiesGuidingRecomm, caseType, userAtRecomm, this.dateAtRecomm.toString(),
// this.timeAtRecomm.toString());//
// getDistanceScoresforCandidateTimelines(candidateTimelines,activitiesGuidingRecomm);
// }
//
// this.topNextActivityObjects = fetchNextActivityObjects(editDistancesSortedMapFullCand, candidateTimelines);
//
// System.out.println("---------editDistancesSortedMapFullCand.size()=" + editDistancesSortedMapFullCand.size());
//
// if (Constant.verbose)
// {
// System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
//
// System.out.println("\n" + "\n The candidate timelines in increasing order of distance are:");
// traverseCandidateTimelineWithEditDistance();// editDistancesSortedMapFullCand);
//
// System.out.println("\nTop next activities are: ");// +this.topNextRecommendedActivities);
// traverseTopNextActivities();
// }
// // System.out.println("\nDebug note192_308: getActivityNamesGuidingRecommwithTimestamps() " +
// // getActivityNamesGuidingRecommwithTimestamps() +
// // " size of current timeline="
// // + currentTimeline.getActivityObjectsInTimeline().size());
// // ########Sanity check
// if (this.topNextActivityObjects.size() == editDistancesSortedMapFullCand.size()
// && editDistancesSortedMapFullCand.size() == this.candidateTimelines.size())
// {
// // System.out.println("Sanity Check 349 Passed");
// }
// else
// {
// System.err.println(
// "Error at Sanity 349 (RecommenderMasterMU: this.topNextActivityObjects.size() ==
// editDistancesSortedMapFullCand.size() && editDistancesSortedMapFullCand.size()== this.candidateTimelines.size()
// not satisfied");
// errorExists = true;
// }
//
// // ##############
// if (caseType.equals("CaseBasedV1"))
// {
// // rankScore= (1d-(editDistanceValExceptEnd/maxEditDistanceValExceptEnd))* (1d-
// // (endPointActivityEditDistanceVal/maxEditDistanceValOfEnd));
// // createRankedTopRecommendedActivityNamesCaseBasedV1(this.topNextActivityObjects,
// // this.similarityOfEndPointActivityObjectCand);
//
// // rankScore= (1d-(editDistanceValExceptEnd/maxEditDistanceValExceptEnd))* simEndPointActivityObject;
// createRankedTopRecommendedActivityNamesCaseBasedV1_3(this.topNextActivityObjects,
// this.similarityOfEndPointActivityObjectCand);
// }
//
// else
// {
// // createRankedTopRecommendedActivityNames
// createRankedTopRecommendedActivityNamesSimpleV3_3(this.topNextActivityObjects);// , this.userAtRecomm,
// // dateAtRecomm,
// // timeAtRecomm);
// }
//
// // System.out.println("Next recommended 5 activity is: "+this.topNextRecommendedActivities);
//
// /*
// * IMPORTANT: If the next activity after the current activity object in the current timeline is an invalid
// * activity, then we can include the current activity in the list of recommended activities, otherwise the
// * current activity has to be removed from the list of recommended activities
// */
// if (currentTimeline.getImmediateNextActivityInvalid() == 0) // not invalid
// {
// removeRecommPointActivityFromRankedRecomm();
// System.out.println("removing recomm point activity (Current Activity) from list of recommendation");
// }
//
// WritingToFile.writeDistanceScoresSortedMapMU(this.userAtRecomm, this.dateAtRecomm, this.timeAtRecomm,
// this.editDistancesSortedMapFullCand, this.candidateTimelines, this.topNextActivityObjects,
// this.activitiesGuidingRecomm, Constant.WriteCandInEditDistancePerRtPerCand,
// Constant.WriteEditOperatationsInEditDistancePerRtPerCand);
//
// System.out.println("\nDebug note192_end: getActivityNamesGuidingRecommwithTimestamps() "
// + getActivityNamesGuidingRecommwithTimestamps() + " size of current timeline="
// + currentTimeline.getActivityObjectsInTimeline().size());
// // System.out.println("Debug note192_2: current timeline " +
// // currentTimeline.getActivityObjectNamesInSequence());
//
// long recommMasterTEnd = System.currentTimeMillis();
//
// System.out.println("\n^^^^^^^^^^^^^^^^Exiting Recommendation Master");
// }
// End of curtain 9 march 2017

// /////////
// /

// //////////
// for case based V1
/*
 * 
 * rankScore= (1d-(editDistanceValExceptEnd/maxEditDistanceValExceptEnd))* (1d-
 * (endPointActivityEditDistanceVal/maxEditDistanceValOfEnd));
 * 
 * Generates a ranked list of recommended Activity Objects
 * 
 * setRecommendedActivityNamesRankscorePairs setRankedRecommendedActivityNamesWithRankScores
 * setRankedRecommendedActivityNamesWithoutRankScores
 * 
 * @param topNextActivityObjectsWithDistance
 */
// public void createRankedTopRecommendedActivityNamesCaseBasedV1(ArrayList<Triple<ActivityObject, Double, Integer>>
// topNextActivityObjectsWithDistance,
// LinkedHashMap<Integer, Double> editDistanceOfEndPointActivityObjectCand) // we might remove these arguments as these
// are already member variables of this
// class
// {
// String topRankedActivityNamesWithScore, topRankedActivityNamesWithoutScore;
//
// int numberOfTopNextActivityObjects = topNextActivityObjectsWithDistance.size();
//
// // System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
// topRecommendationsWithDistance="+topRecommendationsWithDistance);
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
// numberOfTopNextActivityEvenst=numberOfTopNextActivityEvenst");
// // System.out.print("Debug inside createRankedTopRecommendedActivityObjects: the read next activity objects are: ");
//
// LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<String, Double>(); //
// <ActivityName,RankScore>
//
// Double maxEditDistanceValExceptEnd = 0d;
// Double maxEditDistanceValOfEnd = 0d;
//
// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
// {
// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
//
// if (editDistanceVal > maxEditDistanceValExceptEnd)
// {
// maxEditDistanceValExceptEnd = editDistanceVal;
// }
// }
//
// // finding maximum of endpointEditDistance
// for (Map.Entry<Integer, Double> entry : editDistanceOfEndPointActivityObjectCand.entrySet())
// {
// Double editDistanceVal1 = entry.getValue();
//
// if (editDistanceVal1 > maxEditDistanceValOfEnd)
// {
// maxEditDistanceValOfEnd = editDistanceVal1;
// }
// }
//
// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
// {
// String topNextActivityName = topNextActivityObjectsWithDistance.get(i).getFirst().getActivityName();
// Double editDistanceValExceptEnd = topNextActivityObjectsWithDistance.get(i).getSecond();
//
// Integer candTimelineID = topNextActivityObjectsWithDistance.get(i).getThird();
//
// Double endPointActivityEditDistanceVal = editDistanceOfEndPointActivityObjectCand.get(candTimelineID);
//
// Double rankScore = (1d - (editDistanceValExceptEnd / maxEditDistanceValExceptEnd)) * (1d -
// (endPointActivityEditDistanceVal / maxEditDistanceValOfEnd));
//
// System.out.println("RANK SCORE CALCULATION=" + "(1d-(" + editDistanceValExceptEnd + "/" + maxEditDistanceValExceptEnd
// + "))* (1d- (" +
// endPointActivityEditDistanceVal + "/"
// + maxEditDistanceValOfEnd + "))");
//
// if (recommendedActivityNamesRankscorePairs.containsKey(topNextActivityName) == false)
// {
// recommendedActivityNamesRankscorePairs.put(topNextActivityName, rankScore);
// }
//
// else
// {
// recommendedActivityNamesRankscorePairs.put(topNextActivityName,
// recommendedActivityNamesRankscorePairs.get(topNextActivityName) + rankScore);
// }
// }
//
// System.out.println();
//
// recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>)
// UtilityBelt.sortByValue(recommendedActivityNamesRankscorePairs); // Sorted in
// descending order of ranked score:
// higher
// // ranked score means
// // higher value of rank
//
// // ///////////IMPORTANT //////////////////////////////////////////////////////////
// this.setRecommendedActivityNamesWithRankscores(recommendedActivityNamesRankscorePairs);
//
// this.setRankedRecommendedActivityNamesWithRankScores(recommendedActivityNamesRankscorePairs);
// this.setRankedRecommendedActivityNamesWithoutRankScores(recommendedActivityNamesRankscorePairs);
//
// // /////////////////////////////////////////////////////////////////////
//
// topRankedActivityNamesWithScore = getRankedRecommendedActivityNamesWithRankScores();
// topRankedActivityNamesWithoutScore = getRankedRecommendedActivityNamesWithoutRankScores();
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithScore= " +
// topRankedActivityNamesWithScore);
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithoutScore= " +
// topRankedActivityNamesWithoutScore);
//
// // return topRankedString;
// }

// /////////

// *

/*
 * Generates a ranked list of recommended Activity Objects
 * 
 * setRecommendedActivityNamesRankscorePairs setRankedRecommendedActivityNamesWithRankScores
 * setRankedRecommendedActivityNamesWithoutRankScores
 * 
 * @param topNextActivityObjectsWithDistance
 */
// public void createRankedTopRecommendedActivityNames(ArrayList<Triple<ActivityObject, Double, Integer>>
// topNextActivityObjectsWithDistance)
// {
// String topRankedActivityNamesWithScore, topRankedActivityNamesWithoutScore;
//
// int numberOfTopNextActivityObjects = topNextActivityObjectsWithDistance.size();
//
// // System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
// topRecommendationsWithDistance="+topRecommendationsWithDistance);
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects:
// numberOfTopNextActivityEvenst=numberOfTopNextActivityEvenst");
// // System.out.print("Debug inside createRankedTopRecommendedActivityObjects: the read next activity objects are: ");
//
// LinkedHashMap<String, Double> recommendedActivityNamesRankscorePairs = new LinkedHashMap<String, Double>(); //
// <ActivityName,RankScore>
//
// Double maxEditDistanceVal = 0d;
//
// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
// {
// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
//
// if (editDistanceVal > maxEditDistanceVal)
// {
// maxEditDistanceVal = editDistanceVal;
// }
// }
//
// for (int i = 0; i < numberOfTopNextActivityObjects; i++)
// {
// String topNextActivityName = topNextActivityObjectsWithDistance.get(i).getFirst().getActivityName();
// Double editDistanceVal = topNextActivityObjectsWithDistance.get(i).getSecond();
//
// Double rankScore = 1d - (editDistanceVal / maxEditDistanceVal);
//
// if (recommendedActivityNamesRankscorePairs.containsKey(topNextActivityName) == false)
// {
// recommendedActivityNamesRankscorePairs.put(topNextActivityName, rankScore);
// }
//
// else
// {
// recommendedActivityNamesRankscorePairs.put(topNextActivityName,
// recommendedActivityNamesRankscorePairs.get(topNextActivityName) + rankScore);
// }
// }
//
// System.out.println();
//
// recommendedActivityNamesRankscorePairs = (LinkedHashMap<String, Double>)
// UtilityBelt.sortByValue(recommendedActivityNamesRankscorePairs); // Sorted in
// descending order of ranked score:
// higher
// // ranked score means
// // higher value of rank
//
// // ///////////IMPORTANT //////////////////////////////////////////////////////////
// this.setRecommendedActivityNamesWithRankscores(recommendedActivityNamesRankscorePairs);
//
// this.setRankedRecommendedActivityNamesWithRankScores(recommendedActivityNamesRankscorePairs);
// this.setRankedRecommendedActivityNamesWithoutRankScores(recommendedActivityNamesRankscorePairs);
//
// // /////////////////////////////////////////////////////////////////////
//
// topRankedActivityNamesWithScore = getRankedRecommendedActivityNamesWithRankScores();
// topRankedActivityNamesWithoutScore = getRankedRecommendedActivityNamesWithoutRankScores();
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithScore= " +
// topRankedActivityNamesWithScore);
// System.out.println("Debug inside createRankedTopRecommendedActivityObjects: topRankedActivityNamesWithoutScore= " +
// topRankedActivityNamesWithoutScore);
//
// // return topRankedString;
// }

//
// // ////////
// /*
// * Added: Oct 7, 2014: for IMPORTANT POINT: THE CANDIDATE TIMELINE IS THE DIRECT CANDIDATE TIMELINE AND NOT THE LEAST
// DISTANT SUBCANDIDATE.
// */
// /**
// * Returns a map where each entry corresponds to a candidate timeline. The value of an entry is the edit distance
// value between the end point activity object
// (current activity) of the candidate
// * timeline and the current activity object (activity at recomm point)
// *
// * @param candidateTimelines
// * @param activitiesGuidingRecomm
// * @param caseType
// * can be 'SimpleV3' or 'CaseBasedV1'
// * @return <CanditateTimelineID, edit distance of this end point Activity Object of this candidate with end point
// Activity Object>
// */
// public LinkedHashMap<Integer, Double> getEditDistan