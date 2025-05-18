package com.transit.graphbased_v2.domain.graph.nodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Node;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Node("OA")
public class ObjectAttributeClazz implements Serializable {
    @Id
    private UUID id;
    private String name;

    //ObjectClass add


    private ClazzType type;

    private Set<String> readProperties;
    private Set<String> writeProperties;
    private Set<String> shareReadProperties;
    private Set<String> shareWriteProperties;

    private Set<DigitsAccessDTO> digitsAccess;
    private String entityClass;

    @DynamicLabels
    private Set<String> labels;

    public ObjectAttributeClazz() {
        this.readProperties = new HashSet<>();
        this.writeProperties = new HashSet<>();
        this.shareReadProperties = new HashSet<>();
        this.shareWriteProperties = new HashSet<>();
        this.digitsAccess = new HashSet<>();
        this.labels = new HashSet<>();
        this.entityClass = "";
    }

    public ObjectAttributeClazz(UUID id, String name, ClazzType type, Map<String, String> properties, String entityClass) {
        ObjectMapper objectMapper = new ObjectMapper();
        this.id = id;
        this.name = name;
        this.type = type;
        try {
            if (properties != null && !properties.isEmpty()) {
                this.readProperties = new HashSet<>(properties.values());
                this.writeProperties = new HashSet<>(properties.values());
                this.shareReadProperties = new HashSet<>(properties.values());
                this.shareWriteProperties = new HashSet<>(properties.values());

            } else {
                this.readProperties = new HashSet<>();
                this.writeProperties = new HashSet<>();
                this.shareReadProperties = new HashSet<>();
                this.shareWriteProperties = new HashSet<>();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        this.labels = new HashSet<>();
        this.labels.add(type.name());
        this.entityClass = entityClass;
    }


    public ObjectAttributeClazz(UUID id, String name, ClazzType type, String entityClass) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.readProperties = new HashSet<>();
        this.writeProperties = new HashSet<>();
        this.shareReadProperties = new HashSet<>();
        this.shareWriteProperties = new HashSet<>();
        this.labels = new HashSet<>();
        this.labels.add(type.name());
        this.entityClass = entityClass;
    }

    public ObjectAttributeClazz(String name) {
        this.name = name;
        this.labels = new HashSet<>();
    }

    public static Map<String, String> getPropertiesFromString(String props) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (props != null && !props.isEmpty()) {
            try {
                return objectMapper.readValue(props, Map.class);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        return null;
    }

    public static String getPropertiesString(Map<String, String> properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        var proper = "";
        try {
            proper = properties == null ? "" : objectMapper.writeValueAsString(properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return proper;
    }

    public ObjectAttributeClazz addProperty(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("a node cannot have a property with a null key or value");
        }
        try {

            if (key == "readProperties") {
                if (value != null) {
                    this.readProperties.add(value);
                }
            }

            if (key == "writeProperties") {
                if (value != null) {
                    this.writeProperties.add(value);
                }
            }

            if (key == "shareReadProperties") {

                if (value != null) {
                    this.shareReadProperties.add(value);
                }
            }

            if (key == "shareWriteProperties") {

                if (value != null) {
                    this.shareWriteProperties.add(value);
                }
            }


        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return this;
    }

    public ObjectAttributeClazz updateProperty(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("a node cannot have a property with a null key or value");
        }

        try {
            if (key == "readProperties") {
                if (value != null) {
                    this.readProperties.add(value);
                }
            }

            if (key == "writeProperties") {
                if (value != null) {
                    this.writeProperties.add(value);
                }
            }

            if (key == "shareReadProperties") {

                if (value != null) {
                    this.shareReadProperties.add(value);
                }
            }

            if (key == "shareWriteProperties") {

                if (value != null) {
                    this.shareWriteProperties.add(value);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return this;
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

    /**
     * Two nodes are equal if their IDs are the same.
     *
     * @param o The object to check for equality.
     * @return true if the two objects are the same, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ObjectAttributeClazz n)) {
            return false;
        }


        return this.id.equals(n.id)
                && this.name.equals(n.name)
                && this.type.equals(n.type)
                && this.readProperties.equals(n.readProperties)
                && this.writeProperties.equals(n.writeProperties)
                && this.shareReadProperties.equals(n.shareReadProperties)
                && this.shareWriteProperties.equals(n.shareWriteProperties);
    }

    @Override
    public String toString() {
        return name + ":" + type + ":" + readProperties;
    }

}