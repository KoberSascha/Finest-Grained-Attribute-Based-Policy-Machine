package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.*;
import com.datatrust.graphbased_v2.domain.graph.nodes.*;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import com.datatrust.graphbased_v2.service.helper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccessServiceBeanGrantSourcesTest {
    private final RightsRepository rights = mock(RightsRepository.class);
    private final UpdateRightsRecursive propagation = mock(UpdateRightsRecursive.class);
    private final OaDeletionService deletion = mock(OaDeletionService.class);
    private final AccessServiceBean service = new AccessServiceBean();
    private final UUID app = UUID.randomUUID(), object = UUID.randomUUID(), giver = UUID.randomUUID(), recipient = UUID.randomUUID();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "rightsRepository", rights);
        ReflectionTestUtils.setField(service, "accessValidator", new AccessValidator());
        ReflectionTestUtils.setField(service, "oADeserializer", new OADeserializer());
        ReflectionTestUtils.setField(service, "updateRecursive", propagation);
        ReflectionTestUtils.setField(service, "oaDeletionService", deletion);
        when(rights.createAccess(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(new ObjectAttributeExtendedClazz()));
        when(rights.updateOa(any())).thenReturn(Optional.of(new ObjectAttributeExtendedClazz()));
    }

    @Test
    void createsSeparateChildrenForAllCurrentSourcesAndKeepsEachParentsOwnBounds() {
        var first = node("code");
        first.setDigitsAccess(Set.of(new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(0, 2)), "readProperties")));
        var second = node("name");
        context(Set.of(first.getId(), second.getId()), List.of());
        load(first, second);

        service.createConnection(app, request("code", "name"), object, recipient, giver);

        var created = ArgumentCaptor.forClass(ObjectAttributeClazz.class);
        var parents = ArgumentCaptor.forClass(UUID.class);
        verify(rights, times(2)).createAccess(eq(app), created.capture(), parents.capture(), eq(recipient), eq(giver), eq(object));
        assertEquals(2, created.getAllValues().stream().map(ObjectAttributeClazz::getId).distinct().count());
        for (int index = 0; index < 2; index++) {
            var node = created.getAllValues().get(index);
            assertEquals(parents.getAllValues().get(index).equals(first.getId()) ? Set.of("code") : Set.of("name"), node.getReadProperties());
            if (node.getReadProperties().contains("code"))
                assertTrue(node.getDigitsAccess().stream().allMatch(d -> d.getReadableDigits().stream()
                        .allMatch(r -> r.getReadableDigitsFrom() == 0 && r.getReadableDigitsTo() == 2)));
        }
    }

    @Test
    void replacementShrinksEveryLegacyDuplicateAndAddsANewSourceBeforeAnyPropagation() {
        var parent = node("old", "new");
        var newSource = node("new");
        var first = node("old");
        var duplicate = node("old");
        context(Set.of(parent.getId(), newSource.getId()), List.of(
                new RightsRepository.ControlledGrant(first.getId(), Set.of(parent.getId())),
                new RightsRepository.ControlledGrant(duplicate.getId(), Set.of(parent.getId()))));
        load(parent, newSource, first, duplicate);

        service.updateConnection(app, request("new"), object, recipient, giver);

        assertEquals(Set.of("new"), first.getReadProperties());
        assertEquals(Set.of("new"), duplicate.getReadProperties());
        var ordered = inOrder(rights, propagation);
        ordered.verify(rights).updateOa(first);
        ordered.verify(rights).updateOa(duplicate);
        ordered.verify(rights).createAccess(eq(app), any(), eq(newSource.getId()), eq(recipient), eq(giver), eq(object));
        ordered.verify(propagation).updateRecursive(first.getId());
        verify(propagation).updateRecursive(duplicate.getId());
        verifyNoInteractions(deletion);
    }

    @Test
    void anyControlledRootMakesPostADuplicateRegardlessOfOtherRecipientsContributions() {
        context(Set.of(UUID.randomUUID()), List.of(new RightsRepository.ControlledGrant(UUID.randomUUID(), Set.of(UUID.randomUUID()))));
        var error = assertThrows(BadRequestException.class,
                () -> service.createConnection(app, request("new"), object, recipient, giver));
        assertTrue(error.getMessage().contains("Rights already exists"));
        verify(rights, never()).createAccess(any(), any(), any(), any(), any(), any());
        verify(rights, never()).updateOa(any());
    }

    @Test
    void rejectsAmbiguousActualParentBeforeChangingAnyGrant() {
        var parent = node("p");
        context(Set.of(parent.getId()), List.of(new RightsRepository.ControlledGrant(UUID.randomUUID(), Set.of(parent.getId(), UUID.randomUUID()))));
        load(parent);
        assertThrows(BadRequestException.class, () -> service.updateConnection(app, request("p"), object, recipient, giver));
        verify(rights, never()).createAccess(any(), any(), any(), any(), any(), any());
        verify(rights, never()).updateOa(any());
        verifyNoInteractions(propagation, deletion);
    }

    private void context(Set<UUID> parents, List<RightsRepository.ControlledGrant> grants) {
        when(rights.getGrantContext(app, object, giver, recipient)).thenReturn(new RightsRepository.GrantContext(true, true, true, parents, grants));
    }
    private void load(ObjectAttributeClazz... nodes) {
        for (var node : nodes) when(rights.findOaByIdWithDigitAccess(node.getId())).thenReturn(Optional.of(node));
    }
    private OAPropertiesDTO request(String... fields) {
        var properties = Set.of(fields);
        return new OAPropertiesDTO(properties, properties, properties, properties, Set.of());
    }
    private ObjectAttributeClazz node(String... fields) {
        var node = new ObjectAttributeClazz();
        node.setId(UUID.randomUUID());
        node.setReadProperties(Set.of(fields));
        node.setWriteProperties(Set.of(fields));
        node.setShareReadProperties(Set.of(fields));
        node.setShareWriteProperties(Set.of(fields));
        return node;
    }
}
