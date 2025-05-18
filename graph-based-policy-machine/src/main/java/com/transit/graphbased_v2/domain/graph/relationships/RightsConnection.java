package com.transit.graphbased_v2.domain.graph.relationships;

import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.exceptions.BadRequestException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

;

public class RightsConnection extends Relationship {
	
	private static Map<ClazzType, ClazzType[]> validRightsConnections = new EnumMap<>(ClazzType.class);
	
	static {
		validRightsConnections.put(ClazzType.OA, new ClazzType[]{ClazzType.OA});
	}
	
	public RightsConnection(UUID childID, UUID parentID) {
		super(childID, parentID);
	}
	
	public static void checkRightConnection(ClazzType sourceType, ClazzType targetType) throws BadRequestException {
		ClazzType[] check = validRightsConnections.get(sourceType);
		for (ClazzType nt : check) {
			if (nt.equals(targetType)) {
				return;
			}
		}
		
		throw new BadRequestException(String.format("cannot assign a node of type %s to a node of type %s", sourceType, targetType));
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RightsConnection)) {
			return false;
		}
		
		RightsConnection assignment = (RightsConnection) o;
		return this.getSourceID() == assignment.getSourceID() &&
				this.getTargetID() == assignment.getTargetID();
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(getSourceID(), getTargetID());
	}
}