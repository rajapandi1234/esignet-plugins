/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.plugin.mosipid.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class CredentialDefinitionDTO {
	
	/**  */
	private Map<String, Object> credentialSubject;

	/**  */
	private List<String> type;

	/** */
	private List<String> context;

}
