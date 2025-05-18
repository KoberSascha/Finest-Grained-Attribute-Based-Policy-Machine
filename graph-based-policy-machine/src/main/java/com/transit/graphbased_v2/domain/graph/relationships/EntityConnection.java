package com.transit.graphbased_v2.domain.graph.relationships;

import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.exceptions.BadRequestException;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class EntityConnection extends Relationship implements Serializable {

    private static Map<ClazzType, ClazzType[]> validAssociations = new EnumMap<>(ClazzType.class);

    static {
        validAssociations.put(ClazzType.E, new ClazzType[]{ClazzType.O});
        validAssociations.put(ClazzType.OA, new ClazzType[]{});
        validAssociations.put(ClazzType.O, new ClazzType[]{});
        validAssociations.put(ClazzType.I, new ClazzType[]{});
    }

    public EntityConnection(UUID sourceId, UUID targetID) {
        super(sourceId, targetID);
    }

    public static void checkAssociation(ClazzType uaType, ClazzType targetType) throws BadRequestException {
        ClazzType[] check = validAssociations.get(uaType);
        for (ClazzType nt : check) {
            if (nt.equals(targetType)) {
                return;
            }
        }

        throw new BadRequestException(String.format("cannot associate a node of type %s to a node of type %s", uaType, targetType));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EntityConnection)) {
            return false;
        }

        EntityConnection association = (EntityConnection) o;
        return this.getSourceID() == association.getSourceID() &&
                this.getTargetID() == association.getTargetID();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSourceID(), getTargetID());
    }
}