package com.transit.graphbased_v2.domain.graph.nodes;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Node("A")
public class ApplicationClazz {

    @Id
    @Property("id")
    private UUID id;
    private UUID identityId;
    private String name;
    private ClazzType type;
    @DynamicLabels
    private Set<String> labels;


    public ApplicationClazz() {
        this.name = "";
        this.labels = new HashSet<>();
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

}
