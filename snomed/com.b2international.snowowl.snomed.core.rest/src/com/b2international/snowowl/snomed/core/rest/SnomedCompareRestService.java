/*
 * Copyright 2018-2019 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.core.rest;

import java.util.Collections;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.b2international.commons.validation.ApiValidation;
import com.b2international.snowowl.core.rest.AbstractRestService;
import com.b2international.snowowl.core.rest.RestApiError;
import com.b2international.snowowl.core.rest.util.DeferredResults;
import com.b2international.snowowl.datastore.request.RepositoryRequests;
import com.b2international.snowowl.datastore.request.compare.CompareResult;
import com.b2international.snowowl.snomed.core.rest.domain.SnomedCompareRestRequest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @since 7.0
 */
@Api(value = "Compare", description = "Compare", tags = "compare")
@RestController
@RequestMapping(value = "/compare")
public class SnomedCompareRestService extends AbstractSnomedRestService {
	
	public SnomedCompareRestService() {
		super(Collections.emptySet());
	}
	
	@ApiOperation(
		value = "Compare two branches", 
		notes = "Returns the new, changed and deleted components of two branches"
	)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 400, message = "Bad Request", response=RestApiError.class)
	})
	@PostMapping(consumes = { AbstractRestService.JSON_MEDIA_TYPE }, produces = { AbstractRestService.JSON_MEDIA_TYPE })
	@ResponseStatus(HttpStatus.OK)
	public DeferredResult<CompareResult> compareBranches(@RequestBody SnomedCompareRestRequest request) {
		ApiValidation.checkInput(request);
		return DeferredResults.wrap(RepositoryRequests.branching().prepareCompare()
			.setBase(request.getBaseBranch())
			.setCompare(request.getCompareBranch())
			.setLimit(request.getLimit())
			.build(repositoryId)
			.execute(getBus()));
		
	}
}
