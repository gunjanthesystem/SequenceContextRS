package org.activity.constants;

/**
 * 
 * @author gunjan
 *
 */
public class DomainConstants
{

	/**
	 * Note: 'userID' (ignore case) always refers to the raw user-ids. While index of user id refers to user 1, user
	 * 2,... or user 0, user 1, ... user-ids.
	 */
	public static final int tenUserIDsGeolifeData[] = { 62, 84, 52, 68, 167, 179, 153, 85, 128, 10 };
	public static final int allUserIDsGeolifeData[] = { 62, 84, 52, 68, 167, 179, 153, 85, 128, 10, 105, /* 78, */67,
			126, 64, 111, 163, 98, 154, 125, 65, 80, 21, 69, /* 101, 175, */81, 96, 129, /* 115, */56, 91, 58, 82, 141,
			112, 53, 139, 102, 20, 138, 108, 97, /* 92, 75, */
			161, 117, 170 /* ,114, 110, 107 */ };
	public static final int userIDsDCUData[] = { 0, 1, 2, 3, 4 };
	public static final String userNamesDCUData[] = { "Stefan", "Tengqi", "Cathal", "Zaher", "Rami" };
	static final String[] GeolifeActivityNames = { "Not Available", "Unknown", "airplane", "bike", "boat", "bus", "car",
			"motorcycle", "run", "subway", "taxi", "train", "walk" };
	// static final String[] GeolifeActivityNames = { "Not Available", "Unknown", /* "airplane", */"bike", /* "boat",
	// */"bus", "car", /* "motorcycle", *//* "run", */// "subway", "taxi", "train", "walk" };
	static final String[] gowallaActivityNames = null;
	static final String[] DCUDataActivityNames = { "Others", "Unknown", "Commuting", "Computer", "Eating", "Exercising",
			"Housework", "On the Phone", "Preparing Food", "Shopping", "Socialising", "Watching TV" };
	/**
	 * Most active users in the geolife dataset
	 */

	public static final int above10RTsUserIDsGeolifeData[] = { 62, 84, 52, 68, 167, 179, 153, 85, 128, 10, 126, 111,
			163, 65, 91, 82, 139, 108 };
	public static final int gowallaWorkingCatLevel = 2; // -1 indicates original working cat
	static int[] gowallaUserIDs = null;
	public static String[] featureNames = { "ActivityName", "StartTime", "Duration", "DistanceTravelled",
			"StartGeoCoordinates", "EndGeoCoordinates", "AvgAltitude" };

	public static final int gowallaUserIDsWithGT553MaxActsPerDay[] = { 5195, 9298, 9751, 16425, 17012, 18382, 19416,
			19957, 20316, 23150, 28509, 30293, 30603, 42300, 44718, 46646, 74010, 74274, 76390, 79509, 79756, 86755,
			103951, 105189, 106328, 114774, 118023, 136677, 154692, 179386, 194812, 213489, 224943, 235659, 246993,
			251408, 269889, 311530, 338587, 395223, 563986, 624892, 862876, 1722363, 2084969, 2096330, 2103094, 2126604,
			2190642 };

	public static final int gowallaUserIDsWithGT553MaxActsPerDayIndex[] = { 117, 148, 152, 215, 223, 236, 245, 250, 251,
			266, 306, 313, 314, 333, 338, 341, 431, 433, 437, 447, 449, 471, 510, 515, 520, 543, 547, 607, 634, 661,
			678, 696, 714, 729, 741, 749, 761, 792, 816, 841, 855, 861, 869, 894, 902, 907, 912, 921, 934, };

	public final static int numOfCatLevels = 3;

	public final static String pathToSerialisedCatIDNameDictionary = "./dataToRead/UI/CatIDNameDictionary.kryo";

	public static boolean isGowallaUserIDWithGT553MaxActsPerDay(int userID)
	{
		boolean blacklisted = false;

		for (int a : gowallaUserIDsWithGT553MaxActsPerDay)
		{
			if (a == userID)
			{
				return true;
			}
		}
		return blacklisted;
	}

	public static boolean isGowallaUserIDWithGT553MaxActsPerDayIndex(int userID)
	{
		boolean blacklisted = false;

		for (int a : gowallaUserIDsWithGT553MaxActsPerDayIndex)
		{
			if (a == userID)
			{
				return true;
			}
		}
		return blacklisted;
	}

	public DomainConstants()
	{
	}

}
