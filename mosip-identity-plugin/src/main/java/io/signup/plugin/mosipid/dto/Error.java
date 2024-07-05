package io.signup.plugin.mosipid.dto;

import lombok.Data;

@Data
public class Error {

    private String errorCode;
    private String message;
}