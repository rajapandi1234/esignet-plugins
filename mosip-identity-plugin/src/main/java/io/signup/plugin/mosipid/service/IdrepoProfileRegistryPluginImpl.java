/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.signup.plugin.mosipid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.micrometer.core.annotation.Timed;
import io.signup.plugin.mosipid.dto.*;
import io.signup.plugin.mosipid.util.ErrorConstants;
import io.signup.plugin.mosipid.util.ProfileCacheService;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.signup.api.dto.ProfileDto;
import io.mosip.signup.api.dto.ProfileResult;
import io.mosip.signup.api.exception.InvalidProfileException;
import io.mosip.signup.api.exception.ProfileException;
import io.mosip.signup.api.spi.ProfileRegistryPlugin;
import io.mosip.signup.api.util.ProfileCreateUpdateStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.signup.plugin.mosipid.util.ErrorConstants.*;
import static io.mosip.signup.api.util.ErrorConstants.SERVER_UNREACHABLE;

@Slf4j
@Component
public class IdrepoProfileRegistryPluginImpl implements ProfileRegistryPlugin {

    private static final String ID_SCHEMA_VERSION_FIELD_ID = "IDSchemaVersion";
    private static final String SELECTED_HANDLES_FIELD_ID = "selectedHandles";
    private static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private final Map<Double, SchemaResponse> schemaMap = new HashMap<>();
    private static final List<String> ACTIONS = Arrays.asList("CREATE", "UPDATE");

    @Value("#{'${mosip.signup.idrepo.default.selected-handles:phone}'.split(',')}")
    private List<String> defaultSelectedHandles;

    @Value("${mosip.signup.idrepo.schema-url}")
    private String schemaUrl;

    @Value("#{'${mosip.kernel.idobjectvalidator.mandatory-attributes.id-repository.new-registration:}'.split(',')}")
    private List<String> requiredFields;

    @Value("#{'${mosip.kernel.idobjectvalidator.mandatory-attributes.id-repository.update-uin:}'.split(',')}")
    private List<String> requiredUpdateFields;

    @Value("${mosip.signup.idrepo.add-identity.request.id}")
    private String addIdentityRequestID;

    @Value("${mosip.signup.idrepo.update-identity.request.id}")
    private String updateIdentityRequestID;

    @Value("${mosip.signup.idrepo.identity.request.version}")
    private String identityRequestVersion;

    @Value("${mosip.signup.idrepo.identity.endpoint}")
    private String identityEndpoint;

    @Value("${mosip.signup.idrepo.get-identity.endpoint}")
    private String getIdentityEndpoint;

    @Value("${mosip.signup.idrepo.generate-hash.endpoint}")
    private String generateHashEndpoint;

    @Value("${mosip.signup.idrepo.get-uin.endpoint}")
    private String getUinEndpoint;

    @Value("${mosip.signup.idrepo.get-status.endpoint}")
    private String getStatusEndpoint;

    @Value("#{'${mosip.signup.idrepo.mandatory-language:}'.split(',')}")
    private List<String> mandatoryLanguages;

    @Value("#{'${mosip.signup.idrepo.optional-language:}'.split(',')}")
    private List<String> optionalLanguages;

    @Autowired
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileCacheService profileCacheService;

    private void checkRegexValidator(JsonNode validator, JsonNode valueNode) {
        String value = valueNode.get("value").textValue();
        if (validator.get("type").textValue().equals("regex") &&
                !value.matches(validator.get("validator").textValue())) {
            log.error("Regex of {} does not match value of {}", validator.get("validator").textValue(), value);
            throw new InvalidProfileException(ErrorConstants.INVALID_INPUT);
        }
    }

    private void validateEntryFields(Iterator<Map.Entry<String, JsonNode>> itr, JsonNode fields) {
        while (itr.hasNext()) {
            Map.Entry<String, JsonNode> entry = itr.next();
            log.info("TODO - Need to validate field {} >> {}", entry.getKey(), entry.getValue());
            JsonNode validateField = fields.get(entry.getKey());

            if (validateField == null) {
                log.error("Null value found in key field {}", entry.getKey());
                throw new InvalidProfileException(ErrorConstants.INVALID_INPUT);
            }

            JsonNode validators = validateField.get("validators");
            if (validators == null) continue;

            JsonNode validator = validators.get(0);
            if (entry.getValue().getClass().equals(TextNode.class)) {
                checkRegexValidator(validator, entry.getValue());
            } else if (entry.getValue().getClass().equals(ArrayNode.class)) {
                for (JsonNode valueNode: entry.getValue()) {
                    JsonNode language = valueNode.get("language");
                    JsonNode langCode = validator.get("langCode");
                    if ((language == null) || (langCode != null && language.textValue().equals(langCode.textValue()))) {
                        checkRegexValidator(validator, valueNode);
                    }
                }
            }
        }
    }

    @Override
    public void validate(String action, ProfileDto profileDto) throws InvalidProfileException {
        if (!ACTIONS.contains(action)) {
            throw new InvalidProfileException(ErrorConstants.INVALID_ACTION);
        }

        JsonNode inputJson = profileDto.getIdentity();
        double version = inputJson.has(ID_SCHEMA_VERSION_FIELD_ID) ? inputJson.get(ID_SCHEMA_VERSION_FIELD_ID).asDouble() : 0;
        SchemaResponse schemaResponse = getSchemaJson(version);
        ((ObjectNode) inputJson).set(ID_SCHEMA_VERSION_FIELD_ID, objectMapper.valueToTree(schemaResponse.getIdVersion()));

        // check if any required field is missing during the "create" action.
        JsonNode requiredFieldIds = schemaResponse.getParsedSchemaJson().at("/properties/identity/required");
        if (action.equals("CREATE")) {
            for (JsonNode requiredFieldId : requiredFieldIds) {
                if (inputJson.get(requiredFieldId.textValue()) == null) {
                    log.error("Null value found in the required field of {}", requiredFieldId);
                    throw new InvalidProfileException(ErrorConstants.INVALID_INPUT);
                }
            }
        }

        // validate each entry field with schemaResponse
        Iterator<Map.Entry<String, JsonNode>> itr = inputJson.fields();
        JsonNode fields = schemaResponse.getParsedSchemaJson().at("/properties/identity/properties");
        validateEntryFields(itr, fields);
    }

    @Override
    public ProfileResult createProfile(String requestId, ProfileDto profileDto) throws ProfileException {
        JsonNode inputJson = profileDto.getIdentity();
        //set UIN
        ((ObjectNode) inputJson).set("UIN", objectMapper.valueToTree(getUniqueIdentifier()));
        //Build identity request
        IdentityRequest identityRequest = buildIdentityRequest(inputJson, false);
        identityRequest.setRegistrationId(requestId);

        if(!inputJson.has(SELECTED_HANDLES_FIELD_ID) && !CollectionUtils.isEmpty(defaultSelectedHandles)){
            ((ObjectNode) inputJson).set(SELECTED_HANDLES_FIELD_ID, objectMapper.valueToTree(defaultSelectedHandles));
        }

        List<String> requestIdsToTrack = new ArrayList<>();
        if(inputJson.has(SELECTED_HANDLES_FIELD_ID)) {
            Iterator itr = inputJson.get(SELECTED_HANDLES_FIELD_ID).iterator();
            while(itr.hasNext()) {
                String selectedHandle = ((TextNode)itr.next()).textValue();
                if(!inputJson.get(selectedHandle).isArray()) {
                    String value = inputJson.get(selectedHandle).textValue();
                    requestIdsToTrack.add(getHandleRequestId(requestId, selectedHandle, value));
                }
            }
        }
        profileCacheService.setHandleRequestIds(requestId, requestIdsToTrack);
        IdentityResponse identityResponse = addIdentity(identityRequest);
        ProfileResult profileResult = new ProfileResult();
        profileResult.setStatus(identityResponse.getStatus());
        return profileResult;
    }

    @Override
    public ProfileResult updateProfile(String requestId, ProfileDto profileDto) throws ProfileException {
        JsonNode inputJson = profileDto.getIdentity();
        //set UIN
        //((ObjectNode) inputJson).set("UIN", objectMapper.valueToTree(profileDto.getUniqueUserId()));
        ((ObjectNode) inputJson).set("UIN", objectMapper.valueToTree(profileDto.getIndividualId()));
        //Build identity request
        IdentityRequest identityRequest = buildIdentityRequest(inputJson, true);
        identityRequest.setRegistrationId(requestId);

        IdentityResponse identityResponse = updateIdentity(identityRequest);
        profileCacheService.setHandleRequestIds(requestId, Arrays.asList(requestId));

        ProfileResult profileResult = new ProfileResult();
        profileResult.setStatus(identityResponse.getStatus());
        return profileResult;
    }

    @Override
    public ProfileCreateUpdateStatus getProfileCreateUpdateStatus(String requestId) throws ProfileException {
        List<String> handleRequestIds = profileCacheService.getHandleRequestIds(requestId);
        if(handleRequestIds == null || handleRequestIds.isEmpty())
            throw new ProfileException(INVALID_REQUEST_ID);

        //TODO - Need to support returning multiple handles status
        //TODO - Also we should cache the handle create/update status
        return getRequestStatusFromServer(handleRequestIds.get(0));
    }

    @Override
    public ProfileDto getProfile(String individualId) throws ProfileException {
        try {
            String endpoint = String.format(getIdentityEndpoint, individualId);
            ResponseWrapper<IdentityResponse> responseWrapper = request(endpoint, HttpMethod.GET, null,
                    new ParameterizedTypeReference<ResponseWrapper<IdentityResponse>>() {});
            ProfileDto profileDto = new ProfileDto();
            profileDto.setIndividualId(responseWrapper.getResponse().getIdentity().get("UIN").textValue());
            //profileDto.setUniqueUserId(responseWrapper.getResponse().getIdentity().get("UIN").textValue());
            profileDto.setIdentity(responseWrapper.getResponse().getIdentity());
            profileDto.setActive(responseWrapper.getResponse().getStatus().equals("ACTIVATED"));
            return profileDto;
        } catch (ProfileException e) {
            if (e.getErrorCode().equals("IDR-IDC-007")) {
                ProfileDto profileDto = new ProfileDto();
                profileDto.setIndividualId(individualId);
                profileDto.setActive(false);
                return profileDto;
            }
            throw e;
        }
    }

    @Override
    public boolean isMatch(@NotNull JsonNode identity, @NotNull JsonNode inputChallenge) {
        int matchCount = 0;
        Iterator itr = inputChallenge.fieldNames();
        while(itr.hasNext()) {
            String fieldName = (String) itr.next();
            if(!identity.has(fieldName))
                break;

            if(identity.get(fieldName).isArray()) {
                for (JsonNode jsonNode : identity.get(fieldName)) {
                    //As of now assumption is we take user input only in single language
                    matchCount = matchCount + ((jsonNode.equals(inputChallenge.get(fieldName).get(0))) ? 1 : 0);
                }
            }
            else {
                matchCount = matchCount + ((identity.get(fieldName).equals(inputChallenge.get(fieldName))) ? 1 : 0);
            }
        }
        return !inputChallenge.isEmpty() && matchCount >= inputChallenge.size();
    }

    private SchemaResponse getSchemaJson(double version) throws ProfileException {
        if(schemaMap.containsKey(version))
            return schemaMap.get(version);

        ResponseWrapper<SchemaResponse> responseWrapper = request(schemaUrl+version,
                HttpMethod.GET, null, new ParameterizedTypeReference<ResponseWrapper<SchemaResponse>>() {});
        if (responseWrapper.getResponse().getSchemaJson()!=null) {
            SchemaResponse schemaResponse = new SchemaResponse();
            try {
                schemaResponse.setParsedSchemaJson(objectMapper.readValue(responseWrapper.getResponse().getSchemaJson(), JsonNode.class));
                schemaResponse.setIdVersion(responseWrapper.getResponse().getIdVersion());
                schemaMap.put(version, schemaResponse);
                return schemaMap.get(version);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse schemaResponse", e);
            }
        }
        log.error("Failed to fetch the latest schema json due to {}", responseWrapper);
        throw new ProfileException(REQUEST_FAILED);
    }

    @Timed(value = "getuin.api.timer", percentiles = {0.9})
    private String getUniqueIdentifier() throws ProfileException {
        ResponseWrapper<UINResponse> responseWrapper = request(getUinEndpoint, HttpMethod.GET, null,
                new ParameterizedTypeReference<ResponseWrapper<UINResponse>>() {});
        if (!StringUtils.isEmpty(responseWrapper.getResponse().getUIN()) ) {
            return responseWrapper.getResponse().getUIN();
        }
        log.error("Failed to generate UIN {}", responseWrapper.getResponse());
        throw new ProfileException(REQUEST_FAILED);
    }

    @Timed(value = "pwdhash.api.timer", percentiles = {0.9})
    private Password generateSaltedHash(String password) throws ProfileException {
        RequestWrapper<Password.PasswordPlaintext> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequesttime(getUTCDateTime());
        requestWrapper.setRequest(new Password.PasswordPlaintext(password));
        ResponseWrapper<Password.PasswordHash> responseWrapper = request(generateHashEndpoint, HttpMethod.POST, requestWrapper,
                new ParameterizedTypeReference<ResponseWrapper<Password.PasswordHash>>() {});
        if (!StringUtils.isEmpty(responseWrapper.getResponse().getHashValue()) &&
                !StringUtils.isEmpty(responseWrapper.getResponse().getSalt())) {
            return new Password(responseWrapper.getResponse().getHashValue(),
                    responseWrapper.getResponse().getSalt());
        }
        log.error("Failed to generate salted hash {}", responseWrapper.getResponse());
        throw new ProfileException(REQUEST_FAILED);
    }

    @Timed(value = "addidentity.api.timer", percentiles = {0.9})
    private IdentityResponse addIdentity(IdentityRequest identityRequest) throws ProfileException{
        RequestWrapper<IdentityRequest> restRequest = new RequestWrapper<>();
        restRequest.setId(addIdentityRequestID);
        restRequest.setVersion(identityRequestVersion);
        restRequest.setRequesttime(getUTCDateTime());
        restRequest.setRequest(identityRequest);
        ResponseWrapper<IdentityResponse> responseWrapper = request(identityEndpoint, HttpMethod.POST, restRequest,
                new ParameterizedTypeReference<ResponseWrapper<IdentityResponse>>() {});
        return responseWrapper.getResponse();
    }

    @Timed(value = "updateidentity.api.timer", percentiles = {0.9})
    private IdentityResponse updateIdentity(IdentityRequest identityRequest) throws ProfileException{
        RequestWrapper<IdentityRequest> restRequest = new RequestWrapper<>();
        restRequest.setId(updateIdentityRequestID);
        restRequest.setVersion(identityRequestVersion);
        restRequest.setRequesttime(getUTCDateTime());
        restRequest.setRequest(identityRequest);
        ResponseWrapper<IdentityResponse> responseWrapper = request(identityEndpoint, HttpMethod.PATCH, restRequest,
                new ParameterizedTypeReference<ResponseWrapper<IdentityResponse>>() {});
        return responseWrapper.getResponse();
    }

    @Timed(value = "getstatus.api.timer", percentiles = {0.9})
    private ProfileCreateUpdateStatus getRequestStatusFromServer(String applicationId) {
        ResponseWrapper<IdentityStatusResponse> responseWrapper = request(getStatusEndpoint+applicationId,
                HttpMethod.GET, null, new ParameterizedTypeReference<ResponseWrapper<IdentityStatusResponse>>() {});
        if (responseWrapper != null && responseWrapper.getResponse() != null &&
                !StringUtils.isEmpty(responseWrapper.getResponse().getStatusCode())) {
            switch (responseWrapper.getResponse().getStatusCode()) {
                case "STORED":
                    return ProfileCreateUpdateStatus.COMPLETED;
                case "FAILED":
                    return ProfileCreateUpdateStatus.FAILED;
                case "ISSUED":
                default:
                    return ProfileCreateUpdateStatus.PENDING;
            }
        }
        log.error("Get registration status failed with response {}", applicationId, responseWrapper);
        return ProfileCreateUpdateStatus.PENDING;
    }

    private <T> ResponseWrapper<T> request(String url, HttpMethod method, Object request,
                                           ParameterizedTypeReference<ResponseWrapper<T>> responseType) {
        try {
            HttpEntity<?> httpEntity = null;
            if(request != null) {
                httpEntity = new HttpEntity<>(request);
            }
            ResponseWrapper<T> responseWrapper = restTemplate.exchange(
                    url,
                    method,
                    httpEntity,
                    responseType).getBody();
            if (responseWrapper != null && responseWrapper.getResponse() != null) {
                return responseWrapper;
            }
            log.error("{} endpoint returned error response {} ", url, responseWrapper);
            throw new ProfileException(responseWrapper != null && !CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                    responseWrapper.getErrors().get(0).getErrorCode() : REQUEST_FAILED);
        } catch (RestClientException e) {
            log.error("{} endpoint is unreachable.", url, e);
            throw new ProfileException(SERVER_UNREACHABLE);
        }
    }

    private String getHandleRequestId(String requestId, String handleFieldId, String handle) {
        //TODO need to take the tag from configuration based on fieldId
        String handleWithTaggedHandleType = handle.concat("@").concat(handleFieldId).toLowerCase(Locale.ROOT);
        String handleRequestId = requestId.concat(handleWithTaggedHandleType);
        try {
            return HMACUtils2.digestAsPlainText(handleRequestId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate handleRequestId", e);
        }
        return requestId;
    }

    private IdentityRequest buildIdentityRequest(JsonNode inputJson, boolean isUpdate) {
        double version = inputJson.has(ID_SCHEMA_VERSION_FIELD_ID) ? inputJson.get(ID_SCHEMA_VERSION_FIELD_ID).asDouble() : 0;
        SchemaResponse schemaResponse = getSchemaJson(version);
        ((ObjectNode) inputJson).set(ID_SCHEMA_VERSION_FIELD_ID, objectMapper.valueToTree(schemaResponse.getIdVersion()));

        //generate salted hash for password, if exists
        if(inputJson.has("password")) {
            Password password = generateSaltedHash(inputJson.get("password").asText());
            ((ObjectNode) inputJson).set("password", objectMapper.valueToTree(password));
        }

        IdentityRequest identityRequest = new IdentityRequest();
        identityRequest.setIdentity(inputJson);
        return identityRequest;
    }

    private String getUTCDateTime() {
        return ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
    }
}
