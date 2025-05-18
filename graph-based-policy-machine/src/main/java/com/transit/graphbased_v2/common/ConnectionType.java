package com.transit.graphbased_v2.common;

import javax.lang.model.type.UnknownTypeException;
import java.io.Serializable;

public enum ConnectionType implements Serializable {
	ASSIGNMENT("ASSIGNMENT"),
	RIGHTS("RIGHTS"),
	
	ENTITY("ENTITY"),
	
	RELATION("RELATION");
	private final String label;
	
	ConnectionType(String label) {
		this.label = label;
	}
	
	/**
	 * Given a string, return the matching NodeType. If the type is null or not one of the types listed above,
	 * null will be returned
	 *
	 * @param type The String type to convert to a NodeType.
	 * @return the equivalent NodeType of the given String, or null if an invalid type or null is passed.
	 */
	public static ConnectionType toConnectionType(String type) throws UnknownTypeException {
		if (type == null) {
			throw new UnknownTypeException(null, null);
		}
		return switch (type.toUpperCase()) {
			case "ASSIGNMENT" -> ConnectionType.ASSIGNMENT;
			case "RIGHTS" -> ConnectionType.RIGHTS;
			case "ENTITY" -> ConnectionType.ENTITY;
			case "RELATION" -> ConnectionType.RELATION;
			default -> throw new UnknownTypeException(null, type);
		};
	}
	
	public String toString() {
		return label;
	}
}