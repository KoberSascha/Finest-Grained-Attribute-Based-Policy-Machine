package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.controller.dto.*;
import com.datatrust.graphbased_v2.domain.graph.nodes.*;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessValidatorTest {
    private final AccessValidator validator = new AccessValidator();

    @Test
    void crossInconsistentRequestsCannotRetainShareWriteWithoutFinalWriteAndShareRead() {
        var parent = parent("p", "q");
        var result = validator.validateRightsPropertiesNonDigitAccess(Set.of("p"), Set.of("q"), Set.of("q"), Set.of("q"), parent);
        assertEquals(Set.of("p"), result.getReadProperties());
        assertTrue(result.getWriteProperties().isEmpty());
        assertTrue(result.getShareReadProperties().isEmpty());
        assertTrue(result.getShareWriteProperties().isEmpty());
    }

    @Test
    void inheritsEveryParentConstraintEvenWhenChildSpecifiesOnlyAnotherField() {
        var parent = parent("code", "name");
        parent.setDigitsAccess(Set.of(da("code", "readProperties", 2, 4), da("code", "shareReadProperties", 2, 4)));
        var result = grant(parent, Set.of(da("name", "readProperties", 0, 2)), "code", "name");
        for (String type : List.of("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"))
            assertEquals(List.of("2:4"), ranges(result.getDigitsAccess(), "code", type));
        assertEquals(List.of("0:2"), ranges(result.getDigitsAccess(), "name", "readProperties"));
    }

    @Test
    void disjointIntersectionRemovesTheBaseFieldAndDependentRights() {
        var parent = parent("code");
        parent.setDigitsAccess(Set.of(da("code", "readProperties", 0, 2)));
        var result = grant(parent, Set.of(da("code", "readProperties", 5, 8)), "code");
        assertTrue(result.getReadProperties().isEmpty());
        assertTrue(result.getWriteProperties().isEmpty());
        assertTrue(result.getShareReadProperties().isEmpty());
        assertTrue(result.getShareWriteProperties().isEmpty());
        assertTrue(result.getDigitsAccess().isEmpty());
    }

    @Test
    void explicitEmptyOrNullRangesDenyInsteadOfBecomingUnrestricted() {
        for (Set<ReadAbleDigitsDTO> empty : Arrays.asList(Set.<ReadAbleDigitsDTO>of(), null)) {
            var result = grant(parent("code"), Set.of(new DigitsAccessDTO("code", empty, "readProperties")), "code");
            assertTrue(result.getReadProperties().isEmpty());
            assertTrue(result.getWriteProperties().isEmpty());
        }
    }

    @Test
    void zeroBasedInclusiveSingleCharacterRemainsAValidPermission() {
        var result = grant(parent("code"), Set.of(da("code", "readProperties", 0, 0)), "code");
        assertEquals(Set.of("code"), result.getReadProperties());
        assertEquals(List.of("0:0"), ranges(result.getDigitsAccess(), "code", "readProperties"));
        assertEquals(List.of("0:0"), ranges(result.getDigitsAccess(), "code", "writeProperties"));
    }

    @Test
    void delegatedReadCannotExceedParentsShareReadRange() {
        var parent = parent("code");
        parent.setDigitsAccess(Set.of(da("code", "readProperties", 0, 9), da("code", "shareReadProperties", 3, 5)));
        var result = grant(parent, Set.of(da("code", "readProperties", 0, 9)), "code");
        assertEquals(List.of("3:5"), ranges(result.getDigitsAccess(), "code", "readProperties"));
        assertEquals(List.of("3:5"), ranges(result.getDigitsAccess(), "code", "shareWriteProperties"));
        assertEquals(List.of("0:9"), ranges(parent.getDigitsAccess(), "code", "readProperties"));
    }

    @Test
    void unionPreservesRangesOfIndependentlyGrantedFields() {
        var first = readGrant("code", Set.of(da("code", "readProperties", 0, 2)));
        var second = readGrant("name", Set.of(da("name", "readProperties", 3, 5)));
        var result = validator.combineAllOaWithDigitsAccessForReadingAccess(List.of(first, second));
        assertEquals(Set.of("code", "name"), result.getReadProperties());
        assertEquals(List.of("0:2"), ranges(result.getDigitsAccess(), "code", "readProperties"));
        assertEquals(List.of("3:5"), ranges(result.getDigitsAccess(), "name", "readProperties"));
    }

    @Test
    void unionOfRestrictedContributorsCombinesTheirInclusiveRanges() {
        var first = readGrant("code", Set.of(da("code", "readProperties", 0, 2)));
        var second = readGrant("code", Set.of(da("code", "readProperties", 5, 8)));
        var result = validator.combineAllOaWithDigitsAccessForReadingAccess(List.of(first, second));
        assertEquals(List.of("0:2", "5:8"), ranges(result.getDigitsAccess(), "code", "readProperties"));
    }

    @Test
    void unrestrictedContributorDominatesOnlyItsOwnPropertyAndRight() {
        var restricted = readGrant("code", Set.of(da("code", "readProperties", 0, 2)));
        var unrestricted = readGrant("code", Set.of());
        var unrelated = readGrant("name", Set.of());
        assertTrue(validator.combineAllOaWithDigitsAccessForReadingAccess(List.of(restricted, unrestricted)).getDigitsAccess().isEmpty());
        var result = validator.combineAllOaWithDigitsAccessForReadingAccess(List.of(restricted, unrelated));
        assertEquals(List.of("0:2"), ranges(result.getDigitsAccess(), "code", "readProperties"));
        assertTrue(ranges(result.getDigitsAccess(), "name", "readProperties").isEmpty());
        // Revoking the independent unrestricted grant restores the surviving bound.
        assertEquals(List.of("0:2"), ranges(validator.combineAllOaWithDigitsAccessForReadingAccess(List.of(restricted))
                .getDigitsAccess(), "code", "readProperties"));
    }

    @Test
    void emptyRequestClearsPreviousRangeWhenParentIsUnrestricted() {
        var node = new ObjectAttributeClazz();
        node.setDigitsAccess(Set.of(da("code", "readProperties", 2, 4)));
        validator.generateValidatedOANode(node, new OAPropertiesDTO(Set.of("code"), Set.of("code"), Set.of("code"), Set.of("code"), Set.of()), parent("code"));
        assertEquals(Set.of("code"), node.getReadProperties());
        assertTrue(node.getDigitsAccess().isEmpty());
    }

    @Test
    void invalidBoundsAreRejectedBeforeGrantPersistence() {
        assertThrows(BadRequestException.class, () -> grant(parent("code"), Set.of(da("code", "readProperties", -1, 2)), "code"));
        assertThrows(BadRequestException.class, () -> grant(parent("code"), Set.of(da("code", "readProperties", 3, 2)), "code"));
        assertThrows(BadRequestException.class, () -> grant(parent("code"),
                Set.of(new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(0, 2)), null)), "code"));
    }

    private ObjectAttributeClazz grant(ObjectAttributeExtendedClazz parent, Set<DigitsAccessDTO> ranges, String... fields) {
        Set<String> properties = Set.of(fields);
        return validator.generateValidatedOANode(new ObjectAttributeClazz(), new OAPropertiesDTO(properties, properties, properties, properties, ranges), parent);
    }

    private ObjectAttributeExtendedClazz parent(String... fields) {
        var result = new ObjectAttributeExtendedClazz();
        result.setReadProperties(new HashSet<>(Set.of(fields)));
        result.setWriteProperties(new HashSet<>(Set.of(fields)));
        result.setShareReadProperties(new HashSet<>(Set.of(fields)));
        result.setShareWriteProperties(new HashSet<>(Set.of(fields)));
        result.setDigitsAccess(Set.of());
        return result;
    }

    private ObjectAttributeExtendedClazz readGrant(String property, Set<DigitsAccessDTO> ranges) {
        var result = new ObjectAttributeExtendedClazz();
        result.setReadProperties(Set.of(property));
        result.setDigitsAccess(ranges);
        return result;
    }

    private DigitsAccessDTO da(String field, String type, int from, int to) {
        return new DigitsAccessDTO(field, Set.of(new ReadAbleDigitsDTO(from, to)), type);
    }

    static List<String> ranges(Set<DigitsAccessDTO> entries, String field, String type) {
        return entries.stream().filter(entry -> field.equals(entry.getProperty()) && type.equals(entry.getType()))
                .flatMap(entry -> entry.getReadableDigits().stream()).map(range -> range.getReadableDigitsFrom() + ":" + range.getReadableDigitsTo()).sorted().toList();
    }
}
