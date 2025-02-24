/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.plugin.mosipid.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdaKycAuthRequest {

    private String id;
    private String version;
    private String individualId;
    private String individualIdType;
    private String transactionID;
    private String requestTime;
    private String specVersion;
    private String thumbprint;
    private String domainUri;
    private String env;
    private boolean consentObtained;
    private String request;
    private String requestHMAC;
    private String requestSessionKey;
    private Map<String, Object> metadata;
    private List<String> allowedKycAttributes;
    private Boolean claimMetadataRequired;

    @Data
    public static class AuthRequest {
        private String otp;
        private String staticPin;
        private String timestamp;
        private List<Biometric> biometrics;
        private List<KeyBindedToken> keyBindedTokens;
        private String password;
    }

    @Data
    public static class Biometric {
        private String data;
        private String hash;
        private String sessionKey;
        private String specVersion;
        private String thumbprint;
    }


}
