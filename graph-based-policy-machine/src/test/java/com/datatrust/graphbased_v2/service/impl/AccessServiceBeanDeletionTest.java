package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceBeanDeletionTest {

    @Mock private RightsRepository rightsRepository;
    @Mock private OaDeletionService oaDeletionService;
    @InjectMocks private AccessServiceBean service;

    private final UUID application = new UUID(0, 1);
    private final UUID object = new UUID(0, 2);
    private final UUID identity = new UUID(0, 3);
    private final UUID requester = new UUID(0, 4);

    @Test
    void deletesAuthorizedGrantThroughSharedSubtreeService() {
        UUID oa = new UUID(0, 5);
        UUID second = new UUID(0, 6);
        when(rightsRepository.getGrantContext(application, object, requester, identity)).thenReturn(
                new RightsRepository.GrantContext(true, true, true, Set.of(), List.of(
                        new RightsRepository.ControlledGrant(oa, Set.of(requester)),
                        new RightsRepository.ControlledGrant(second, Set.of(requester)))));

        assertTrue(service.deleteConnectionRecursive(application, object, identity, requester));

        verify(oaDeletionService).deleteSubtrees(Set.of(oa, second));
    }

    @Test
    void missingAuthorizationDoesNotStartDeletion() {
        when(rightsRepository.getGrantContext(application, object, requester, identity)).thenReturn(
                new RightsRepository.GrantContext(true, true, true, Set.of(), List.of()));

        assertFalse(service.deleteConnectionRecursive(application, object, identity, requester));

        verifyNoInteractions(oaDeletionService);
    }

    @Test
    void compatibilityEntryPointDelegatesWithoutChangingTheCallerSet() {
        HashSet<UUID> roots = new HashSet<>(Set.of(identity, requester));

        service.deleteOaNodes(roots);

        verify(oaDeletionService).deleteSubtrees(roots);
        assertEquals(Set.of(identity, requester), roots);
    }

    @Test
    void selfRevokeDeletesAllReceivedSourcesThroughDedicatedOwnerExcludingLookup() {
        var received = Set.of(UUID.randomUUID(), UUID.randomUUID());
        when(rightsRepository.findReceivedGrantIds(application, object, identity)).thenReturn(received);
        assertTrue(service.deleteConnectionRecursive(application, object, identity, identity));
        verify(oaDeletionService).deleteSubtrees(received);
    }

    @Test
    void selfRevokeWithOnlyOwnershipDoesNotDeleteTheOwnerRoot() {
        when(rightsRepository.findReceivedGrantIds(application, object, identity)).thenReturn(Set.of());
        assertFalse(service.deleteConnectionRecursive(application, object, identity, identity));
        verifyNoInteractions(oaDeletionService);
    }
}
