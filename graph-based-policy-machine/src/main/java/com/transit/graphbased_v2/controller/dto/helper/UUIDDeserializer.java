package com.transit.graphbased_v2.controller.dto.helper;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.UUID;

public class UUIDDeserializer extends JsonDeserializer<UUID> {
	
	@Override
	public UUID deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		if (p.getText() == null) {
			throw  new NullPointerException();
		}
		return UUID.fromString(p.getText());
	}
}
