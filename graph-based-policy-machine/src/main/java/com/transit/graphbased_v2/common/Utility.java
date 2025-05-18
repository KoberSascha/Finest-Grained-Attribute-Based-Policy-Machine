package com.transit.graphbased_v2.common;




import java.util.HashSet;


public class Utility {
	public Utility() {
	}
	
	public static String setToString(HashSet<String> inValue, String separator) {
		String values = "";
		for (String value : inValue) {
			values += value + separator;
		}
		values = values.substring(0, values.length() - 1);
		return values;
	}
	
	public static String arrayToString(String[] inValue, String separator) {
		String values = "";
		for (String value : inValue) {
			values += value + separator;
		}
		values = values.substring(0, values.length() - 1);
		return values;
	}
}
