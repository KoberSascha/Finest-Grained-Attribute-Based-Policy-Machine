package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import com.transit.graphbased_v2.transferobjects.AccessTransferComponent;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AccessTransferComponentHelper {
    @LogExecutionTime
    public AccessTransferComponent getAccessTransferComponent(ObjectAttributeClazz objectAttributeClazz, UUID objectClazzId, String objectEntityClazz, UUID identityId) {
        var result = new AccessTransferComponent();
        result.setObjectId(objectClazzId);

        result.setObjectEntityClazz(objectEntityClazz);

        if (objectEntityClazz == null) {
            result.setObjectEntityClazz(objectAttributeClazz.getEntityClass());
        }

        result.setIdentityId(identityId);

        result.setReadProperties(objectAttributeClazz.getReadProperties());
        result.setWriteProperties(objectAttributeClazz.getWriteProperties());
        result.setShareReadProperties(objectAttributeClazz.getShareReadProperties());
        result.setShareWriteProperties(objectAttributeClazz.getShareWriteProperties());

        result.setDigitsAccess(objectAttributeClazz.getDigitsAccess());

        return result;
    }

}
