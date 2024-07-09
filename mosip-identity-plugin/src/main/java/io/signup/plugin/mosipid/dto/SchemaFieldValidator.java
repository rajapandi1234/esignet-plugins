package io.signup.plugin.mosipid.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SchemaFieldValidator {

    private String type;
    private String validator;
    private List<String> arguments;
    private String langCode;
}
