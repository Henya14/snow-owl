/*
 * Copyright 2019 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.snowowl.fhir.core.request;

import javax.validation.constraints.NotNull;

import com.b2international.snowowl.fhir.core.model.codesystem.CodeSystem;
import com.b2international.snowowl.fhir.core.model.codesystem.LookupRequest;
import com.b2international.snowowl.fhir.core.model.codesystem.LookupResult;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * @since 7.2
 */
final class FhirLookupRequest extends FhirBaseRequest<FhirCodeSystemContext, LookupResult> {

	@NotNull
	@JsonUnwrapped
	private LookupRequest request;
	
	FhirLookupRequest(LookupRequest request) {
		this.request = request;
	}
	
	@Override
	public LookupResult execute(FhirCodeSystemContext context) {
		final CodeSystem cs = context.codeSystem();
		return LookupResult.builder()
				.build();
	}

}
