/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.signup.plugin.mosipid.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ProfileCacheService {

    private static final String REQUEST_IDS = "request_ids";

    @Autowired
    private CacheManager cacheManager;

    @Cacheable(value = REQUEST_IDS, key = "#requestId")
    public List<String> setHandleRequestIds(String requestId, List<String> handleRequestIds) {
        return handleRequestIds;
    }

    public  List<String> getHandleRequestIds(String requestId) {
        return cacheManager.getCache(REQUEST_IDS).get(requestId, List.class);//NOSONAR getCache() will not be returning null here.
    }
}