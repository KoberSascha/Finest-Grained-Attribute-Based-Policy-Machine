package com.transit.graphbased_v2.domain.graph.nodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@Node("O")
public class ObjectClazz implements Serializable {
	@Id
	@Property("id")
	private UUID id;
	private String name;
	
	//ObjectClass add
	
	
	private ClazzType type;
	private String properties;
	
	private String entityClass;
	
	@DynamicLabels
	private Set<String> labels;
	
	public ObjectClazz() {
		this.properties = "";
		this.labels = new HashSet<>();
		this.entityClass = "";
	}
	
	public ObjectClazz(UUID id, String name, ClazzType type, Map<String, String> properties, String entityClass) {
		ObjectMapper objectMapper = new ObjectMapper();
		this.id = id;
		this.name = name;
		this.type = type;
		try {
			if (properties != null && !properties.isEmpty()) {
				this.properties = objectMapper.writeValueAsString(properties);
			} else {
				this.properties = "";
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		this.labels = new HashSet<>();
		this.labels.add(type.name());
		this.entityClass = entityClass;
	}
	
	
	public ObjectClazz(UUID id, String name, ClazzType type, String entityClass) {
		this.id = id;
		this.name = name;
		this.type = type;
		this.properties = "";
		this.labels = new HashSet<>();
		this.labels.add(type.name());
		this.entityClass = entityClass;
	}
	
	public ObjectClazz(String name) {
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
	
	public ObjectClazz addProperty(String key, String value) {
		if (key == null || value == null) {
			throw new IllegalArgumentException("a node cannot have a property with a null key or value");
		}
		
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			if (this.properties != null && this.properties.isEmpty()) {
				Map<String, String> props = objectMapper.readValue(properties, Map.class);
				props.put(key, value);
				this.properties = objectMapper.writeValueAsString(props);
				
			} else {
				Map<String, String> props = new HashMap<>();
				props.put(key, value);
				this.properties = objectMapper.writeValueAsString(props);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		return this;
	}
	
	public Map<String, String> getProperties() {
		ObjectMapper objectMapper = new ObjectMapper();
		if (this.properties != null && !this.properties.isEmpty() && !this.properties.isBlank()) {
			try {
				return objectMapper.readValue(properties, Map.class);
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
		}
		return new HashMap<>();
	}
	
	public void setProperties(Map<String, String> properties) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			if (properties != null && !properties.isEmpty()) {
				this.properties = objectMapper.writeValueAsString(properties);
			} else {
				this.properties = "";
			}
			
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
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
		if (!(o instanceof ObjectClazz n)) {
			return false;
		}
		
		
		return this.id.equals(n.id)
				&& this.name.equals(n.name)
				&& this.type.equals(n.type)
				&& this.properties.equals(n.properties);
	}
	
	@Override
	public String toString() {
		return name + ":" + type + ":" + properties;
	}
	
}