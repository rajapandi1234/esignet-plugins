/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.signup.plugin.mock.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.signup.api.dto.ProfileDto;
import io.mosip.signup.api.dto.ProfileResult;
import io.mosip.signup.api.exception.InvalidProfileException;
import io.mosip.signup.api.exception.ProfileException;
import io.mosip.signup.api.spi.ProfileRegistryPlugin;
import io.mosip.signup.api.util.ProfileCreateUpdateStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@ConditionalOnProperty(value = "signup.integration.profile-registry-plugin", havingValue = "MockProfileRegistry")
@Slf4j
@Component
public class MockProfileRegistryPluginImpl implements ProfileRegistryPlugin {


    @Override
    public void validate(String action, ProfileDto profileDto) throws InvalidProfileException {

    }

    @Override
    public ProfileResult createProfile(String requestId, ProfileDto profileDto) throws ProfileException {
        return null;
    }

    @Override
    public ProfileResult updateProfile(String requestId, ProfileDto profileDto) throws ProfileException {
        return null;
    }

    @Override
    public ProfileCreateUpdateStatus getProfileCreateUpdateStatus(String requestId) throws ProfileException {
        return null;
    }

    @Override
    public ProfileDto getProfile(String individualId) throws ProfileException {
        return null;
    }

    @Override
    public boolean isMatch(JsonNode identity, JsonNode inputChallenge) {
        return false;
    }
}
