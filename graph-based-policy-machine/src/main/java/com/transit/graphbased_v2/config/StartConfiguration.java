package com.transit.graphbased_v2.config;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource(value = {"classpath:application.local.properties", "classpath:application.properties"}, ignoreResourceNotFound = false)
public class StartConfiguration implements EnvironmentAware {
	
	private static Environment env;
	
	public static String getProperty(String key) {
		if (env == null) {
			return "";
		}
		return env.getProperty(key);
	}
	
	@Override
	public void setEnvironment(Environment env) {
		StartConfiguration.env = env;
	}
}