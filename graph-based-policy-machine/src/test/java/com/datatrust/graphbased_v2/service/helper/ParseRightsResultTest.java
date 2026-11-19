package com.datatrust.graphbased_v2.service.helper;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ParseRightsResultTest {
    @Test
    void preservesAllFourFieldSetsFromBatchRecord() {
        Record record = mock(Record.class);
        var objectId = UUID.randomUUID();
        when(record.get(0)).thenReturn(Values.value(Map.of("id", UUID.randomUUID().toString(), "name", "grant", "type", "OA",
                "readProperties", List.of("read", "write", "share"), "writeProperties", List.of("write"),
                "shareReadProperties", List.of("share"), "shareWriteProperties", List.of())));
        when(record.get(1)).thenReturn(Values.value(objectId.toString()));
        when(record.get(2)).thenReturn(Values.value("Example"));

        var result = new ParseRightsResult().parseOAResultNode(record, true);

        assertEquals(Set.of("read", "write", "share"), result.getReadProperties());
        assertEquals(Set.of("write"), result.getWriteProperties());
        assertEquals(Set.of("share"), result.getShareReadProperties());
        assertEquals(Set.of(), result.getShareWriteProperties());
        assertEquals(objectId, result.getOId());
    }
}
