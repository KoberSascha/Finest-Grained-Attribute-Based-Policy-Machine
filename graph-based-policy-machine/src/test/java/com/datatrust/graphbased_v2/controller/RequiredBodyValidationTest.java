package com.datatrust.graphbased_v2.controller;

import com.datatrust.graphbased_v2.exceptions.handler.GlobalExceptionHandler;
import com.datatrust.graphbased_v2.service.IdentityService;
import com.datatrust.graphbased_v2.service.ObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequiredBodyValidationTest {
    @Test
    void missingOrMalformedIdentityIdReturns400BeforeServiceCall() throws Exception {
        var service = mock(IdentityService.class);
        var controller = new IdentityController();
        ReflectionTestUtils.setField(controller, "identityService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String body : new String[]{"{}", "{\"identityId\":null}", "{\"identityId\":\"invalid\"}"})
            mvc.perform(post("/identity").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingObjectIdOwnerClassOrPropertiesReturns400BeforeServiceCall() throws Exception {
        var service = mock(ObjectService.class);
        var controller = new ObjectController();
        ReflectionTestUtils.setField(controller, "objectService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        String validId = UUID.randomUUID().toString();
        String[] bodies = {"{}", "{\"identityId\":\"" + validId + "\",\"objectEntityClass\":\"Example\",\"properties\":[\"p\"]}",
                "{\"objectId\":\"" + validId + "\",\"objectEntityClass\":\"Example\",\"properties\":[\"p\"]}",
                "{\"objectId\":\"" + validId + "\",\"identityId\":\"" + validId + "\",\"objectEntityClass\":\" \",\"properties\":[\"p\"]}",
                "{\"objectId\":\"" + validId + "\",\"identityId\":\"" + validId + "\",\"objectEntityClass\":\"Example\"}"};
        for (String body : bodies)
            mvc.perform(post("/application/" + UUID.randomUUID() + "/object").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
