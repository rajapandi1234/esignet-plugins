/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.signup.plugin.mock.service;

import io.mosip.signup.api.dto.FrameDetail;
import io.mosip.signup.api.dto.IdentityVerificationDto;
import io.mosip.signup.api.dto.IdentityVerificationResult;
import io.mosip.signup.api.dto.VerifiedResult;
import io.mosip.signup.api.exception.IdentityVerifierException;
import io.mosip.signup.api.spi.IdentityVerifierPlugin;
import io.mosip.signup.api.util.ProcessType;
import io.mosip.signup.api.util.VerificationStatus;
import io.signup.plugin.mock.dto.UseCaseScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static io.mosip.signup.api.util.ProcessType.VIDEO;

@Slf4j
@Component
public class MockIdentityVerifierPluginImpl extends IdentityVerifierPlugin {

    Map<String, String> localStore = new HashMap<>();

    private UseCaseScene[] useCase;

    @Value("${mosip.signup.identity-verification.mock.usecase}")
    private String useCaseName;

    @Value("${mosip.signup.config-server-url}")
    private String configServerUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String getVerifierId() {
        return "mock-identity-verifier";
    }

    @Override
    public List<ProcessType> getSupportedProcessTypes() {
        return List.of(VIDEO);
    }

    @Override
    public void verify(String transactionId, IdentityVerificationDto identityVerificationDto) throws IdentityVerifierException {
        if(useCase == null) {
            useCase = restTemplate.getForObject(configServerUrl+useCaseName, UseCaseScene[].class);
        }

        IdentityVerificationResult identityVerificationResult = new IdentityVerificationResult();
        identityVerificationResult.setId(transactionId);
        identityVerificationResult.setVerifierId(getVerifierId());

        if(isStartStep(identityVerificationDto.getStepCode())) {
            Optional<UseCaseScene> result = Arrays.stream(useCase).filter(scene -> scene.getFrameNumber() == 0 &&
                    scene.getStepCode().equals(identityVerificationDto.getStepCode())).findFirst();
            if(result.isPresent()) {
                identityVerificationResult.setStep(result.get().getStep());
                identityVerificationResult.setFeedback(result.get().getFeedback());
                publishAnalysisResult(identityVerificationResult);
            }
        }

        if(identityVerificationDto.getFrames() != null) {
            for(FrameDetail frameDetail : identityVerificationDto.getFrames()) {
                Optional<UseCaseScene> result = Arrays.stream(useCase)
                        .filter(scene -> scene.getFrameNumber() == frameDetail.getOrder() &&
                                scene.getStepCode().equals(identityVerificationDto.getStepCode()))
                        .findFirst();
                if(result.isPresent()) {
                    identityVerificationResult.setStep(result.get().getStep());
                    identityVerificationResult.setFeedback(result.get().getFeedback());
                    publishAnalysisResult(identityVerificationResult);
                }
            }
        }
    }

    @Override
    public VerifiedResult getVerifiedResult(String transactionId) throws IdentityVerifierException {
        log.info("TODO - we should save the verification details in mock-identity-system");
        VerifiedResult verifiedResult = new VerifiedResult();
        verifiedResult.setStatus(VerificationStatus.COMPLETED);
        return verifiedResult;
    }

}