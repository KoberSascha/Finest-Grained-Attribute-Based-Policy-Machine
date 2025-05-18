package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.common.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class StringListHelper {


	public static String collectionToString(Collection<String> values) {
		var temp = new ArrayList<String>(values);
		Collections.sort(temp);
		return StringUtils.join(temp, Constants.DELIMITER);
	}


	public static Set<String> listToSetString(List<Object> list) {
		Set<String> set = new HashSet<>();

		for (Object obj : list) {
			String str = String.valueOf(obj); // Convert the object to a string representation
			set.add(str); // Add the string to the set
		}
		return set;
	}


	public static List<String> stringToList(String values) {
		if (values == null) {
			return new ArrayList<>();
		}
		if (values.isEmpty() || values.isBlank()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(Arrays.asList(values.split(Constants.DELIMITER)));
	}

	public static Set<String> stringToSet(String values) {
		if (values == null) {
			return new HashSet<>();
		}
		if (values.isEmpty() || values.isBlank()) {
			return new HashSet<>();
		}
		return new HashSet<>(Arrays.asList(values.split(Constants.DELIMITER)));
	}


	public static Map<String, String> removeMapDuplicates(Map<String, String> inputMap) {

		Set<String> uniqueKeys = new HashSet<>();
		Map<String, String> resultMap = new HashMap<>();

		for (Map.Entry<String, String> entry : inputMap.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			if (!uniqueKeys.contains(key)) {
				uniqueKeys.add(key);
				resultMap.put(key, value);
			}
		}

		return resultMap;
	}


	public static Set<String> removeSetDuplicates(Set<String> inputSet) {

		Set<String> resultSet = new HashSet<>(inputSet);

		return resultSet;
	}

}
