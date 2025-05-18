package com.transit.graphbased_v2.domain.graph.relationships;


import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.exceptions.BadRequestException;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class Assignment extends Relationship implements Serializable {


    private static Map<ClazzType, ClazzType[]> validAssignments = new EnumMap<>(ClazzType.class);

    static {
        validAssignments.put(ClazzType.E, new ClazzType[]{});
        validAssignments.put(ClazzType.O, new ClazzType[]{ClazzType.OA});
        validAssignments.put(ClazzType.OA, new ClazzType[]{});
        validAssignments.put(ClazzType.O, new ClazzType[]{});
        validAssignments.put(ClazzType.I, new ClazzType[]{});
    }

    public Assignment(UUID sourceId, UUID targetId) {
        super(sourceId, targetId);
    }

    public static void checkAssignment(ClazzType sourceType, ClazzType targetType) throws BadRequestException {
        ClazzType[] check = validAssignments.get(sourceType);
        for (ClazzType nt : check) {
            if (nt.equals(targetType)) {
                return;
            }
        }

        throw new BadRequestException(String.format("cannot assign a node of type %s to a node of type %s", sourceType, targetType));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Assignment)) {
            return false;
        }

        Assignment assignment = (Assignment) o;
        return this.getSourceID() == assignment.getSourceID() &&
                this.getTargetID() == assignment.getTargetID();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSourceID(), getTargetID());
    }
}