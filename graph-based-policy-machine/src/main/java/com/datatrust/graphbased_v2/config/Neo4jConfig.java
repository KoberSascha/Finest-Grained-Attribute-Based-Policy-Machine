package com.datatrust.graphbased_v2.config;

import datatrust.pmcoreproperty.domain.converters.MapConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.convert.Neo4jConversions;

import java.util.Collections;

@Configuration
public class Neo4jConfig {

    @Bean
    public Neo4jConversions neo4jConversions() {
        return new Neo4jConversions(Collections.singletonList(new MapConverter()));
    }
}
