package com.transit.graphbased_v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication()
@EnableScheduling
@EnableConfigurationProperties
//@EnableWebMvc
public class GraphBasedV2Application {

    public static void main(String[] args) {
        var context = SpringApplication.run(GraphBasedV2Application.class, args);
    }

}
