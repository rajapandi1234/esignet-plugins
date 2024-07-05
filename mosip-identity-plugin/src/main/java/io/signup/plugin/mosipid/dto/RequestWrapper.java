package io.signup.plugin.mosipid.dto;

import lombok.Data;

@Data
public class RequestWrapper<T> {

    private String id;
    private String version;
    private String requesttime;
    private T request;
}
