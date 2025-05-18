package com.transit.graphbased_v2.transferobjects;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Component
public class AccessTransferComponent {
    private UUID objectId;
    private String objectEntityClazz;

    private UUID identityId;

    private Set<String> readProperties;

    private Set<String> writeProperties;

    private Set<String> shareReadProperties;

    private Set<String> shareWriteProperties;

    private Set<DigitsAccessDTO> digitsAccess;


}

