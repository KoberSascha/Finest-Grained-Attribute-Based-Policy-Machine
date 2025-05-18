package com.transit.graphbased_v2.repository.helper;

import com.transit.graphbased_v2.repository.AssigmentRepository;
import org.neo4j.driver.exceptions.DatabaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DBClean {
	
	@Autowired
	private AssigmentRepository relationshipClazzRepository;
	
	public void clear() throws DatabaseException {
		String cypher = "MATCH (a) -[r] -> () delete a, r;";
		relationshipClazzRepository.execute(cypher, true);
		cypher = "match(n) detach delete n";
		relationshipClazzRepository.execute(cypher, true);
	}
	
	
}
