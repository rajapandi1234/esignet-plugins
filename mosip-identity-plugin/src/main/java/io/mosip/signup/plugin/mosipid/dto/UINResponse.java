package io.mosip.signup.plugin.mosipid.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UINResponse {

    @JsonProperty("uin")
    private String UIN;
}