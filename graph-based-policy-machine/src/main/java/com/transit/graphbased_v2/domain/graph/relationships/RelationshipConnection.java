package com.transit.graphbased_v2.domain.graph.relationships;

import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
public class RelationshipConnection extends Relationship implements Serializable {

    private static Map<ClazzType, ClazzType[]> validAssignments = new EnumMap<>(ClazzType.class);

    static {
        validAssignments.put(ClazzType.E, new ClazzType[]{});
        validAssignments.put(ClazzType.O, new ClazzType[]{});
        validAssignments.put(ClazzType.OA, new ClazzType[]{});
        validAssignments.put(ClazzType.I, new ClazzType[]{ClazzType.OA});
    }

    private boolean owns;
    private boolean access;
    private boolean control;

    public RelationshipConnection(UUID sourceId, UUID targetId, boolean owns, boolean access, boolean control) {
        super(sourceId, targetId);
        this.owns = owns;
        this.access = access;
        this.control = control;
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