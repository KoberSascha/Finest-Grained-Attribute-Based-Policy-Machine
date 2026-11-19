package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.datatrust.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.datatrust.graphbased_v2.controller.dto.ReadAbleDigitsDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import org.springframework.stereotype.Component;
import java.util.*;

/** Field and zero-based inclusive character permissions. Missing ranges mean unrestricted. */
@Component
public class AccessValidator {
    private static final String READ = "readProperties", WRITE = "writeProperties";
    private static final String SHARE_READ = "shareReadProperties", SHARE_WRITE = "shareWriteProperties";
    private static final List<String> TYPES = List.of(READ, WRITE, SHARE_READ, SHARE_WRITE);
    private record Key(String property, String type) { }
    private record Range(int from, int to) { }

    private static Set<String> safeSet(Set<String> fields) { return fields == null ? Set.of() : fields; }

    public Set<String> matchPropertiesAgainstDigitAccess() { return new HashSet<>(); }

    public ObjectAttributeClazz validateRightsPropertiesNonDigitAccess(Set<String> read, Set<String> write,
            Set<String> shareRead, Set<String> shareWrite, ObjectAttributeExtendedClazz parent) {
        var result = new ObjectAttributeClazz();
        Set<String> parentRead = parent == null ? Set.of() : safeSet(parent.getReadProperties());
        Set<String> parentWrite = parent == null ? Set.of() : safeSet(parent.getWriteProperties());
        Set<String> parentShareRead = parent == null ? Set.of() : safeSet(parent.getShareReadProperties());
        Set<String> parentShareWrite = parent == null ? Set.of() : safeSet(parent.getShareWriteProperties());
        result.setReadProperties(intersectFields(read, parentRead, parentShareRead));
        result.setWriteProperties(intersectFields(write, result.getReadProperties(), parentWrite, parentShareWrite));
        result.setShareReadProperties(intersectFields(shareRead, result.getReadProperties(), parentShareRead));
        result.setShareWriteProperties(intersectFields(shareWrite, result.getWriteProperties(), result.getShareReadProperties(), parentShareWrite));
        return result;
    }

    public ObjectAttributeClazz generateValidatedOANode(ObjectAttributeClazz node, OAPropertiesDTO request,
                                                        ObjectAttributeExtendedClazz parent) {
        node.setReadProperties(new HashSet<>(safeSet(request.getReadProperties())));
        node.setWriteProperties(new HashSet<>(safeSet(request.getWriteProperties())));
        node.setShareReadProperties(new HashSet<>(safeSet(request.getShareReadProperties())));
        node.setShareWriteProperties(new HashSet<>(safeSet(request.getShareWriteProperties())));
        node.setDigitsAccess(request.getDigitsAccess());
        return constrainDelegation(node, parent);
    }

    /** Existing descendants are intersected with their parent; previous grants never expand. */
    public ObjectAttributeClazz constrainDelegation(ObjectAttributeClazz child, ObjectAttributeExtendedClazz parent) {
        var fields = validateRightsPropertiesNonDigitAccess(child.getReadProperties(), child.getWriteProperties(),
                child.getShareReadProperties(), child.getShareWriteProperties(), parent);
        var requested = constraints(child.getDigitsAccess());
        var parentRanges = constraints(parent == null ? null : parent.getDigitsAccess());
        Map<Key, List<Range>> allowed = new LinkedHashMap<>();
        for (String type : TYPES) {
            Set<String> candidates = properties(fields, type);
            for (String property : new ArrayList<>(candidates)) {
                if (!dependenciesExist(fields, type, property)) {
                    candidates.remove(property);
                    continue;
                }
                List<Range> range = requested.get(new Key(property, type));
                for (String parentType : parentCaps(type)) range = intersection(range, parentRanges.get(new Key(property, parentType)));
                for (String ownType : ownCaps(type)) range = intersection(range, allowed.get(new Key(property, ownType)));
                if (range != null && range.isEmpty()) candidates.remove(property);
                else if (range != null) allowed.put(new Key(property, type), range);
            }
        }
        child.setReadProperties(fields.getReadProperties());
        child.setWriteProperties(fields.getWriteProperties());
        child.setShareReadProperties(fields.getShareReadProperties());
        child.setShareWriteProperties(fields.getShareWriteProperties());
        child.setDigitsAccess(toDtos(allowed));
        return child;
    }

    private List<String> parentCaps(String type) {
        return switch (type) {
            case READ -> List.of(READ, SHARE_READ);
            case WRITE -> List.of(WRITE, SHARE_WRITE);
            case SHARE_READ -> List.of(READ, SHARE_READ);
            case SHARE_WRITE -> List.of(WRITE, SHARE_READ, SHARE_WRITE);
            default -> throw new BadRequestException("Unknown access type: " + type);
        };
    }

    private List<String> ownCaps(String type) {
        return switch (type) {
            case WRITE, SHARE_READ -> List.of(READ);
            case SHARE_WRITE -> List.of(WRITE, SHARE_READ);
            default -> List.of();
        };
    }

    private boolean dependenciesExist(ObjectAttributeClazz node, String type, String property) {
        for (String dependency : ownCaps(type)) if (!properties(node, dependency).contains(property)) return false;
        return true;
    }

    public ObjectAttributeExtendedClazz combineAllOaForReadingAccess(List<ObjectAttributeExtendedClazz> nodes) {
        return combineAllOaWithDigitsAccessForReadingAccess(nodes);
    }

    /** Independent grants are unioned; an unrestricted contributor makes the field/type unrestricted. */
    public ObjectAttributeExtendedClazz combineAllOaWithDigitsAccessForReadingAccess(List<ObjectAttributeExtendedClazz> nodes) {
        if (nodes == null || nodes.isEmpty()) return null;
        var first = nodes.get(0);
        var result = new ObjectAttributeExtendedClazz();
        result.setId(first.getId());
        result.setName(first.getName());
        if (first.getType() != null) result.setType(first.getType());
        result.setEntityClass(first.getEntityClass());
        result.setOId(first.getOId());
        result.setOEntityClazz(first.getOEntityClazz());
        List<Map<Key, List<Range>>> nodeRanges = nodes.stream().map(node -> constraints(node.getDigitsAccess())).toList();
        Map<Key, List<Range>> mergedRanges = new LinkedHashMap<>();
        for (String type : TYPES) {
            Set<String> all = new LinkedHashSet<>();
            for (var node : nodes) all.addAll(getPropertiesByString(node, type));
            Set<String> granted = new LinkedHashSet<>();
            for (String property : all) {
                List<Range> merged = List.of();
                for (int index = 0; index < nodes.size(); index++) {
                    if (getPropertiesByString(nodes.get(index), type).contains(property))
                        merged = union(merged, nodeRanges.get(index).get(new Key(property, type)));
                }
                if (merged == null || !merged.isEmpty()) {
                    granted.add(property);
                    if (merged != null) mergedRanges.put(new Key(property, type), merged);
                }
            }
            setProperties(result, type, granted);
        }
        result.setDigitsAccess(toDtos(mergedRanges));
        return result;
    }

    public ObjectAttributeExtendedClazz combineAccessPropertiesFromOa(ObjectAttributeExtendedClazz source, ObjectAttributeExtendedClazz target) {
        var merged = combineAllOaWithDigitsAccessForReadingAccess(List.of(target, source));
        target.setReadProperties(merged.getReadProperties());
        target.setWriteProperties(merged.getWriteProperties());
        target.setShareReadProperties(merged.getShareReadProperties());
        target.setShareWriteProperties(merged.getShareWriteProperties());
        target.setDigitsAccess(merged.getDigitsAccess());
        if (target.getId() == null) target.setId(source.getId());
        return target;
    }

    public ObjectAttributeExtendedClazz combineDigitAccessPropertiesFromOa(ObjectAttributeExtendedClazz source, ObjectAttributeExtendedClazz target) {
        return combineAccessPropertiesFromOa(source, target);
    }

    public Set<DigitsAccessDTO> combineDigitAccessPropertiesSets(Set<DigitsAccessDTO> first, Set<DigitsAccessDTO> second) {
        var merged = constraints(first);
        constraints(second).forEach((key, ranges) -> merged.merge(key, ranges, this::union));
        return toDtos(merged);
    }

    public Set<ReadAbleDigitsDTO> combineDigitAccessProperties(DigitsAccessDTO first, DigitsAccessDTO second) {
        return rangeDtos(union(parseRanges(first), parseRanges(second)));
    }

    public Set<String> getPropertiesByString(ObjectAttributeExtendedClazz node, String type) {
        return switch (type) {
            case READ -> safeSet(node.getReadProperties());
            case WRITE -> safeSet(node.getWriteProperties());
            case SHARE_READ -> safeSet(node.getShareReadProperties());
            case SHARE_WRITE -> safeSet(node.getShareWriteProperties());
            default -> Set.of();
        };
    }

    public Set<DigitsAccessDTO> validateFinestGrainedOa(ObjectAttributeExtendedClazz node) {
        var ranges = constraints(node.getDigitsAccess());
        ranges.entrySet().removeIf(entry -> !getPropertiesByString(node, entry.getKey().type()).contains(entry.getKey().property()));
        return toDtos(ranges);
    }

    public Set<DigitsAccessDTO> validationDigitAccessAgainstParent(Set<DigitsAccessDTO> child, Set<DigitsAccessDTO> parent) {
        var childRanges = constraints(child);
        var parentRanges = constraints(parent);
        Set<Key> keys = new LinkedHashSet<>(childRanges.keySet());
        keys.addAll(parentRanges.keySet());
        Map<Key, List<Range>> result = new LinkedHashMap<>();
        for (Key key : keys) result.put(key, intersection(childRanges.get(key), parentRanges.get(key)));
        return toDtos(result);
    }

    public Set<DigitsAccessDTO> validateRightsPropertiesDigitAccessAgainstParentOa(Set<DigitsAccessDTO> child,
            Set<DigitsAccessDTO> parent, ObjectAttributeExtendedClazz node) {
        node.setDigitsAccess(validationDigitAccessAgainstParent(child, parent));
        for (var entry : constraints(node.getDigitsAccess()).entrySet())
            if (entry.getValue().isEmpty()) getPropertiesByString(node, entry.getKey().type()).remove(entry.getKey().property());
        return validateFinestGrainedOa(node);
    }

    public Set<String> validateAccessPropertiesAgainstParentProperties(Set<String> child, Set<String> parent) {
        return intersectFields(child, parent);
    }

    public Set<String> combineAccessProperties(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(safeSet(first));
        result.addAll(safeSet(second));
        return result;
    }

    @SafeVarargs
    private final Set<String> intersectFields(Set<String> first, Set<String>... others) {
        Set<String> result = new HashSet<>(safeSet(first));
        for (Set<String> fields : others) result.retainAll(safeSet(fields));
        return result;
    }

    private Set<String> properties(ObjectAttributeClazz node, String type) {
        return switch (type) {
            case READ -> node.getReadProperties();
            case WRITE -> node.getWriteProperties();
            case SHARE_READ -> node.getShareReadProperties();
            case SHARE_WRITE -> node.getShareWriteProperties();
            default -> throw new BadRequestException("Unknown access type: " + type);
        };
    }

    private void setProperties(ObjectAttributeExtendedClazz node, String type, Set<String> fields) {
        switch (type) {
            case READ -> node.setReadProperties(fields);
            case WRITE -> node.setWriteProperties(fields);
            case SHARE_READ -> node.setShareReadProperties(fields);
            case SHARE_WRITE -> node.setShareWriteProperties(fields);
            default -> throw new BadRequestException("Unknown access type: " + type);
        }
    }

    private Map<Key, List<Range>> constraints(Set<DigitsAccessDTO> entries) {
        Map<Key, List<Range>> result = new LinkedHashMap<>();
        if (entries == null) return result;
        for (var entry : entries) {
            if (entry == null || entry.getProperty() == null || entry.getProperty().isBlank()
                    || entry.getType() == null || !TYPES.contains(entry.getType()))
                throw new BadRequestException("Digit access requires a property and a valid access type.");
            result.merge(new Key(entry.getProperty(), entry.getType()), parseRanges(entry), this::union);
        }
        return result;
    }

    private List<Range> parseRanges(DigitsAccessDTO entry) {
        List<Range> result = new ArrayList<>();
        if (entry.getReadableDigits() == null) return result;
        for (var range : entry.getReadableDigits()) {
            if (range == null || range.getReadableDigitsFrom() == null || range.getReadableDigitsTo() == null
                    || range.getReadableDigitsFrom() < 0 || range.getReadableDigitsTo() < range.getReadableDigitsFrom())
                throw new BadRequestException("Digit ranges must have nonnegative inclusive start/end bounds.");
            result.add(new Range(range.getReadableDigitsFrom(), range.getReadableDigitsTo()));
        }
        return normalize(result);
    }

    private List<Range> intersection(List<Range> first, List<Range> second) {
        if (first == null) return second;
        if (second == null) return first;
        List<Range> result = new ArrayList<>();
        for (Range left : first) for (Range right : second) {
            int from = Math.max(left.from(), right.from()), to = Math.min(left.to(), right.to());
            if (from <= to) result.add(new Range(from, to));
        }
        return normalize(result);
    }

    private List<Range> union(List<Range> first, List<Range> second) {
        if (first == null || second == null) return null;
        List<Range> result = new ArrayList<>(first);
        result.addAll(second);
        return normalize(result);
    }

    private List<Range> normalize(List<Range> ranges) {
        var sorted = ranges.stream().sorted(Comparator.comparingInt(Range::from).thenComparingInt(Range::to)).toList();
        List<Range> result = new ArrayList<>();
        for (Range current : sorted) {
            if (!result.isEmpty() && current.from() <= (long) result.get(result.size() - 1).to() + 1) {
                Range previous = result.remove(result.size() - 1);
                result.add(new Range(previous.from(), Math.max(previous.to(), current.to())));
            } else result.add(current);
        }
        return result;
    }

    private Set<ReadAbleDigitsDTO> rangeDtos(List<Range> ranges) {
        Set<ReadAbleDigitsDTO> result = new LinkedHashSet<>();
        for (Range range : ranges) result.add(new ReadAbleDigitsDTO(range.from(), range.to()));
        return result;
    }

    private Set<DigitsAccessDTO> toDtos(Map<Key, List<Range>> ranges) {
        Set<DigitsAccessDTO> result = new LinkedHashSet<>();
        ranges.forEach((key, values) -> {
            if (values != null) result.add(new DigitsAccessDTO(key.property(), rangeDtos(values), key.type()));
        });
        return result;
    }
}
