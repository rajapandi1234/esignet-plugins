package io.mosip.signup.plugin.mosipid.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class IdentityRequest {

    private String registrationId;
    private JsonNode identity;
}
