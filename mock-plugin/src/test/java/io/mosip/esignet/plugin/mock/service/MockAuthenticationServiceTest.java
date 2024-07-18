package io.mosip.esignet.plugin.mock.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mosip.esignet.api.dto.claim.FilterDateTime;
import io.mosip.esignet.plugin.mock.dto.KycExchangeResponseDto;
import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.VerifiedKycExchangeDto;
import io.mosip.esignet.api.dto.claim.FilterCriteria;
import io.mosip.esignet.api.dto.claim.VerificationFilter;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.kernel.core.http.ResponseWrapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class MockAuthenticationServiceTest {

    @InjectMocks
    private MockAuthenticationService mockAuthenticationService;

    @Mock
    private RestTemplate restTemplate;

    @Test
    public void doVerifiedKycExchange_withValidDetails_thenPass() throws KycExchangeException {
        ReflectionTestUtils.setField(mockAuthenticationService, "kycExchangeUrl", "http://localhost:8080/kyc/exchange");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(mockAuthenticationService, "objectMapper", objectMapper);


        VerifiedKycExchangeDto verifiedKycExchangeDto = new VerifiedKycExchangeDto();
        verifiedKycExchangeDto.setKycToken("kycToken");
        verifiedKycExchangeDto.setAcceptedClaims(Arrays.asList("name", "gender"));
        verifiedKycExchangeDto.setClaimsLocales(new String[]{"eng", "hin"});



        Map<String, VerificationFilter> acceptedVerifiedClaims=new HashMap<>();

        VerificationFilter verificationFilterForName= new VerificationFilter();
        FilterCriteria trustFrameWorkCriteria = new FilterCriteria();
        trustFrameWorkCriteria.setValues(List.of("PWD", "Income-tax"));
        FilterDateTime filterDateTime = new FilterDateTime();
        filterDateTime.setMax_age(1000);
        verificationFilterForName.setTime(filterDateTime);
        verificationFilterForName.setTrust_framework(trustFrameWorkCriteria);

        VerificationFilter verificationFilterForEmail= new VerificationFilter();
        FilterCriteria trustFrameworkForEmail = new FilterCriteria();
        trustFrameworkForEmail.setValues(List.of("PWD"));
        filterDateTime.setMax_age(500);
        verificationFilterForEmail.setTime(filterDateTime);
        verificationFilterForEmail.setTrust_framework(trustFrameworkForEmail);


        acceptedVerifiedClaims.put("name", verificationFilterForName);
        acceptedVerifiedClaims.put("email", verificationFilterForEmail);

        verifiedKycExchangeDto.setAcceptedVerifiedClaims(acceptedVerifiedClaims);
        KycExchangeResponseDto kycExchangeResponseDto = new KycExchangeResponseDto();
        kycExchangeResponseDto.setKyc("responseKyc");
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse(kycExchangeResponseDto);
        ResponseEntity<ResponseWrapper<KycExchangeResponseDto>> responseEntity = new ResponseEntity(responseWrapper, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(new ParameterizedTypeReference<ResponseWrapper<KycExchangeResponseDto>>() {
                })
        )).thenReturn(responseEntity);

        KycExchangeResult kycExchangeResult = mockAuthenticationService.doVerifiedKycExchange("RP", "CL", verifiedKycExchangeDto);
        Assert.assertEquals(kycExchangeResponseDto.getKyc(), kycExchangeResult.getEncryptedKyc());

    }

    @Test
    public void doVerifiedKycExchange_withEmptyResponse_thenFail() {
        ReflectionTestUtils.setField(mockAuthenticationService, "kycExchangeUrl", "http://localhost:8080/kyc/exchange");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(mockAuthenticationService, "objectMapper", objectMapper);

        VerifiedKycExchangeDto verifiedKycExchangeDto = new VerifiedKycExchangeDto();
        verifiedKycExchangeDto.setKycToken("kycToken");
        verifiedKycExchangeDto.setAcceptedClaims(Arrays.asList("name", "gender"));
        verifiedKycExchangeDto.setClaimsLocales(new String[]{"eng", "hin"});



        Map<String, VerificationFilter> acceptedVerifiedClaims=new HashMap<>();

        VerificationFilter verificationFilterForName= new VerificationFilter();
        FilterCriteria trustFrameWorkCriteria = new FilterCriteria();
        trustFrameWorkCriteria.setValues(List.of("PWD", "Income-tax"));
        FilterDateTime filterDateTime = new FilterDateTime();
        filterDateTime.setMax_age(1000);
        verificationFilterForName.setTime(filterDateTime);
        verificationFilterForName.setTrust_framework(trustFrameWorkCriteria);

        VerificationFilter verificationFilterForEmail= new VerificationFilter();
        FilterCriteria trustFrameworkForEmail = new FilterCriteria();
        trustFrameworkForEmail.setValues(List.of("PWD"));
        filterDateTime.setMax_age(500);
        verificationFilterForEmail.setTime(filterDateTime);
        verificationFilterForEmail.setTrust_framework(trustFrameworkForEmail);


        acceptedVerifiedClaims.put("name", verificationFilterForName);
        acceptedVerifiedClaims.put("email", verificationFilterForEmail);

        verifiedKycExchangeDto.setAcceptedVerifiedClaims(acceptedVerifiedClaims);
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponse(null);
        ResponseEntity<ResponseWrapper<KycExchangeResponseDto>> responseEntity = new ResponseEntity(responseWrapper, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(new ParameterizedTypeReference<ResponseWrapper<KycExchangeResponseDto>>() {
                })
        )).thenReturn(responseEntity);
        try {
            mockAuthenticationService.doVerifiedKycExchange("RP", "CL", verifiedKycExchangeDto);
        } catch (KycExchangeException e) {
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.DATA_EXCHANGE_FAILED);
        }
    }
}
