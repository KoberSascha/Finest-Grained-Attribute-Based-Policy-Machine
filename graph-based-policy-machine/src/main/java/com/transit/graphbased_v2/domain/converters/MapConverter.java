package transit.pmcoreproperty.domain.converters;

import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.core.convert.converter.Converter;

import java.util.Map;

public class MapConverter implements Converter<Map<String, String>, Value> {
	
	@Override
	public Value convert(Map<String, String> map) {
		return Values.value(map);
	}
}