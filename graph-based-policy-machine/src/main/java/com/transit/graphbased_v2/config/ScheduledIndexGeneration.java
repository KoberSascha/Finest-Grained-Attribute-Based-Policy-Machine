package com.transit.graphbased_v2.config;

import com.transit.graphbased_v2.repository.EntityConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScheduledIndexGeneration {

    @Autowired
    private EntityConnectionRepository relationshipClazzRepository;

    private boolean haveExecute = true;


    private String cypher1 =
            "CREATE INDEX ENodes IF NOT EXISTS\n" +
                    "FOR (e:E)\n" +
                    "ON (e.id);\n";
    private String cypher2 =
            "CREATE INDEX ONodes IF NOT EXISTS\n" +
                    "FOR (o:O)\n" +
                    "ON (o.id);\n";
    private String cypher3 =
            "CREATE INDEX OANodes IF NOT EXISTS\n" +
                    "FOR (oa:OA)\n" +
                    "ON (oa.id);\n";
    private String cypher4 =
            "CREATE INDEX INodes IF NOT EXISTS\n" +
                    "FOR (i:I)\n" +
                    "ON (i.id);\n";
    private String cypher5 =
            "CREATE INDEX EEntityCLass IF NOT EXISTS FOR (e:E)\n" +
                    "ON (e.entityClass);\n";
//    private String cypher6 =
//            "CREATE INDEX ControlRelation IF NOT EXISTS\n" +
//                    "FOR ()-[r:relation]-()\n" +
//                    "ON (r.control);\n";
//    private String cypher7 =
//            "CREATE INDEX AccessRelation IF NOT EXISTS\n" +
//                    "FOR ()-[r:relation]-()\n" +
//                    "ON (r.access);\n";
//    private String cypher8 =
//            "CREATE INDEX OwnsRelation IF NOT EXISTS\n" +
//                    "FOR ()-[r:relation]-()\n" +
//                    "ON (r.owns);\n";

    @Scheduled(fixedDelay = 200)
    public void indexgeneration() {
        if (haveExecute) {
            try {
                log.error("Start creating index");
                executeStartupCypher(cypher1, cypher2, cypher3, cypher4, cypher5);
                haveExecute = false;
                log.error("Index are Ready");
            } catch (Exception ignored) {
                log.error(ignored.getMessage());
                ignored.printStackTrace();
            }
        }
    }

    private void executeStartupCypher(String cypher9, String cypher10, String cypher11, String cypher12, String cypher13) {
        relationshipClazzRepository.execute(cypher9, true);
        relationshipClazzRepository.execute(cypher10, true);
        relationshipClazzRepository.execute(cypher11, true);
        relationshipClazzRepository.execute(cypher12, true);
        relationshipClazzRepository.execute(cypher13, true);

    }

}
