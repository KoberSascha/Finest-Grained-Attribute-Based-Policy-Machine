package com.transit.graphbased_v2.repository.helper;

import org.springframework.stereotype.Component;

@Component
public class RewriteBoolean {
	
	
	public String getStoreString(boolean bool) {
		if (bool) {
			return "1";
		}
		return "0";
	}
	
	public Boolean getBooleanString(String bool) {
		if (bool.equals("1")) {
			return true;
		}
		return false;
	}
}
