package com.transit.graphbased_v2.domain.graph.relationships;




import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public abstract class Relationship implements Serializable {
	UUID sourceId;
	
	UUID targetId;
	
	public Relationship() {
		
	}
	
	public Relationship(UUID sourceId, UUID targetId) {
		this.sourceId = sourceId;
		this.targetId = targetId;
	}
	
	public UUID getSourceID() {
		return sourceId;
	}
	
	public void setSourceID(UUID sourceId) {
		this.sourceId = sourceId;
	}
	
	public UUID getTargetID() {
		return targetId;
	}
	
	public void setTargetID(UUID targetId) {
		this.targetId = targetId;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Relationship)) {
			return false;
		}
		
		Relationship r = (Relationship) o;
		return this.sourceId == r.sourceId
				&& this.targetId == r.targetId;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(sourceId, targetId);
	}
}