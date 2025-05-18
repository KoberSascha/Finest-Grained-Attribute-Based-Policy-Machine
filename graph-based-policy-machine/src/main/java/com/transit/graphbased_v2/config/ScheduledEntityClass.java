//package com.transit.graphbased_v2.config;
//
//import com.transit.graphbased_v2.domain.graph.nodes.EntityClazz;
//import com.transit.graphbased_v2.domain.graph.relationships.EntityConnection;
//import com.transit.graphbased_v2.repository.EntityClazzRepository;
//import com.transit.graphbased_v2.repository.EntityConnectionRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.HashSet;
//import java.util.Set;
//
//@Component
//@Slf4j
//public class ScheduledEntityClass {
//
//	@Autowired
//	private EntityClazzRepository entityClazzRepository;
//
//	@Autowired
//
//	private EntityConnectionRepository entityConnectionRepository;
//
//
//	@Scheduled(fixedDelay = 200)
//	public void mergeEntityClasses() {
//		try {
//			Set<String> entityClasses = new HashSet<>();
//			var temp = entityClazzRepository.findAll();
//			temp.forEach(entry -> entityClasses.add(entry.getEntityClass()));
//
//			for (String x : entityClasses) {
//				var tempIntern = entityClazzRepository.findAllByEntityClass(x);
//				if (tempIntern.size() > 1) {
//					var first = tempIntern.get(0);
//					for (EntityClazz clazz : tempIntern) {
//						if (!first.equals(clazz)) {
//							entityConnectionRepository.getOutgoingRelationships(clazz.getId()).forEach(entry -> {
//								entityConnectionRepository.createRelationship(new EntityConnection(first.getId(), entry.getTargetID()));
//								entityConnectionRepository.deleteRelationship(entry);
//							});
//							entityClazzRepository.delete(clazz);
//						}
//
//					}
//				}
//			}
//		} catch (Exception ignored) {
//
//		}
//	}
//
//
//}
