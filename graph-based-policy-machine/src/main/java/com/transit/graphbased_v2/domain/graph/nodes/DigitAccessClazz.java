package com.transit.graphbased_v2.domain.graph.nodes;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Node;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Node("DA")
public class DigitAccessClazz implements Serializable {
    @Id
    private UUID id;
    private String name;
    private ClazzType type;

    private Integer digitstart;

    private Integer digitstop;

    private String property;
    private String propertytype;

    @DynamicLabels
    private Set<String> labels;

    public DigitAccessClazz() {
        this.labels = new HashSet<>();
        this.digitstart = 0;
        this.digitstop = 0;
        this.name = "";
        this.property = "";
        this.propertytype = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ClazzType getType() {
        return type;
    }

    public void setType(ClazzType type) {
        this.type = type;
        this.labels.clear();
        this.labels.add(type.name());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }


}