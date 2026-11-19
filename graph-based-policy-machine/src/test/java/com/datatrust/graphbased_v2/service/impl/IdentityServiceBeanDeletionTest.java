package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.ApplicationDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.domain.graph.relationships.RelationshipConnection;
import com.datatrust.graphbased_v2.repository.*;
import com.datatrust.graphbased_v2.service.ApplicationService;
import com.datatrust.graphbased_v2.service.ObjectService;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class IdentityServiceBeanDeletionTest {
    @Mock IdentityClazzRepository identities;
    @Mock RightsRepository rights;
    @Mock RelationshipConnectionRepository relationships;
    @Mock ObjectService objects;
    @Mock ApplicationService applications;
    @Mock OaDeletionService deletion;
    @InjectMocks IdentityServiceBean service;
    private final UUID identity = UUID.randomUUID();

    @Test
    void deletesEveryReceivedSourceOnTheSameObjectBeforeRemovingIdentity() {
        var object = UUID.randomUUID();
        var first = oa(object, false);
        var second = oa(object, false);
        when(identities.existsById(identity)).thenReturn(true);
        when(rights.getAllMyRights(identity)).thenReturn(List.of(first, second));
        assertTrue(service.deleteIdentity(identity));
        var order = inOrder(deletion, identities);
        order.verify(deletion).deleteSubtrees(Set.of(first.getId(), second.getId()));
        order.verify(identities).deleteById(identity);
        verifyNoInteractions(objects, applications);
    }

    @Test
    void ownerWithReturnedGrantDeletesObjectOnceAndSkipsItsAlreadyDeletedReceivedRoots() {
        var object = UUID.randomUUID();
        var returned = oa(object, false);
        var owned = oa(object, true);
        var other = oa(UUID.randomUUID(), false);
        var app = UUID.randomUUID();
        when(identities.existsById(identity)).thenReturn(true);
        when(rights.getAllMyRights(identity)).thenReturn(List.of(returned, owned, other));
        when(applications.getApplicationByOANode(owned)).thenReturn(Optional.of(new ApplicationDTO(app, "test", identity)));
        assertTrue(service.deleteIdentity(identity));
        var order = inOrder(objects, deletion, identities);
        order.verify(objects).deleteObject(app, object, identity);
        order.verify(deletion).deleteSubtrees(Set.of(other.getId()));
        order.verify(identities).deleteById(identity);
        verify(objects, times(1)).deleteObject(any(), any(), any());
    }

    private ObjectAttributeExtendedClazz oa(UUID object, boolean owns) {
        var node = new ObjectAttributeExtendedClazz();
        node.setId(UUID.randomUUID());
        node.setOId(object);
        when(relationships.getRelationship(identity, node.getId())).thenReturn(Optional.of(
                new RelationshipConnection(identity, node.getId(), owns, true, owns)));
        return node;
    }
}
