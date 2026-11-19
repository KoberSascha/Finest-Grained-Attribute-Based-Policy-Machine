package com.datatrust.graphbased_v2.repository;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.summary.ResultSummary;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphRepositoryTest {
    @Test
    void consumesAutoCommitWriteAndClosesSessionEvenWhenCallerIgnoresItsResult() {
        var driver = mock(Driver.class);
        var session = mock(Session.class);
        var source = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(session.run("WRITE", Map.of())).thenReturn(source);
        when(source.keys()).thenReturn(List.of());
        when(source.list()).thenReturn(List.of());

        new GraphRepository(driver).execute("WRITE", false);

        var order = inOrder(source, session);
        order.verify(source).list();
        order.verify(source).consume();
        order.verify(session).close();
    }

    @Test
    void returnedRecordsRemainReadableAfterTheSessionIsClosed() {
        var driver = mock(Driver.class);
        var session = mock(Session.class);
        var source = mock(Result.class);
        var record = mock(Record.class);
        var summary = mock(ResultSummary.class);
        when(driver.session()).thenReturn(session);
        when(session.run("READ", Map.of("id", "value"))).thenReturn(source);
        when(source.keys()).thenReturn(List.of("id"));
        when(source.list()).thenReturn(List.of(record));
        when(source.consume()).thenReturn(summary);

        var buffered = new GraphRepository(driver).execute("READ", Map.of("id", "value"), false);

        verify(session).close();
        assertEquals(List.of("id"), buffered.keys());
        assertSame(record, buffered.peek());
        assertSame(record, buffered.single());
        assertFalse(buffered.hasNext());
        assertSame(summary, buffered.consume());
    }

    @Test
    void materializesTransactionResultBeforeCommitAndClose() {
        var driver = mock(Driver.class);
        var session = mock(Session.class);
        var transaction = mock(Transaction.class);
        var source = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(transaction);
        when(transaction.run("WRITE", Map.of())).thenReturn(source);
        when(source.keys()).thenReturn(List.of());
        when(source.list()).thenReturn(List.of());

        new GraphRepository(driver).execute("WRITE", true);

        var order = inOrder(source, transaction, session);
        order.verify(source).consume();
        order.verify(transaction).commit();
        order.verify(transaction).close();
        order.verify(session).close();
    }
}
