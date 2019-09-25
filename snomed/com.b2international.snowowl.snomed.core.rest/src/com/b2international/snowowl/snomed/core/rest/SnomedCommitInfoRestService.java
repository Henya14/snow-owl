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

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.b2international.index.revision.Commit;
import com.b2international.snowowl.core.commit.CommitInfo;
import com.b2international.snowowl.core.commit.CommitInfos;
import com.b2international.snowowl.core.domain.CollectionResource;
import com.b2international.snowowl.core.rest.AbstractRestService;
import com.b2international.snowowl.core.rest.util.DeferredResults;
import com.b2international.snowowl.datastore.request.RepositoryRequests;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @since 7.0
 */
@Api(value = "Commits", description = "Commits", tags = "commits")
@RestController
@RequestMapping(value="/commits")
public class SnomedCommitInfoRestService extends AbstractSnomedRestService {

	public SnomedCommitInfoRestService() {
		super(Commit.Fields.ALL);
	}
	
	@ApiOperation(
		value = "Retrieve commit entries",
		notes = "Returns all SNOMED CT commits"
	)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response=CollectionResource.class)
	})
	@GetMapping(produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public DeferredResult<CommitInfos> search(
			@ApiParam(value = "The author of the commit to match")
			@RequestParam(value="author", required=false)
			final String author,
			
			@ApiParam(value = "Affected component identifier to match")
			@RequestParam(value="affectedComponentId", required=false)
			final String affectedComponentId,
			
			@ApiParam(value = "Commit comment term to match")
			@RequestParam(value="comment", required=false)
			final String comment,
			
			@ApiParam(value = "One or more branch paths to match")
			@RequestParam(value="branch", required=false)
			final List<String> branch,
			
			@ApiParam(value = "Commit timestamp to match")
			@RequestParam(value="timestamp", required=false)
			final Long timestamp,
			
			@ApiParam(value = "Expansion parameters")
			@RequestParam(value="expand", required=false)
			final String expand,
			
			@ApiParam(value = "The scrollKeepAlive to start a scroll using this query")
			@RequestParam(value="scrollKeepAlive", required=false)
			final String scrollKeepAlive,
			
			@ApiParam(value = "A scrollId to continue scrolling a previous query")
			@RequestParam(value="scrollId", required=false)
			final String scrollId,
			
			@ApiParam(value = "The search key to use for retrieving the next page of results")
			@RequestParam(value="searchAfter", required=false)
			final String searchAfter,
			
			@ApiParam(value = "Sort keys")
			@RequestParam(value="sort", required=false)
			final List<String> sort,
			
			@ApiParam(value = "The maximum number of items to return", defaultValue = "50")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit) {
		return DeferredResults.wrap(
				RepositoryRequests
					.commitInfos()
					.prepareSearchCommitInfo()
					.filterByAuthor(author)
					.filterByAffectedComponent(affectedComponentId)
					.filterByComment(comment)
					.filterByBranches(branch)
					.filterByTimestamp(timestamp)
					.setExpand(expand)
					.setScroll(scrollKeepAlive)
					.setScrollId(scrollId)
					.setSearchAfter(searchAfter)
					.setLimit(limit)
					.sortBy(extractSortFields(sort))
					.build(repositoryId)
					.execute(getBus()));
	}
	
	@ApiOperation(
		value = "Retrieve a commit",
		notes = "Returns a single commit entry from SNOMED CT commits"
	)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response=CollectionResource.class)
	})
	@GetMapping(value = "/{commitId}", produces = { AbstractRestService.JSON_MEDIA_TYPE })
	public DeferredResult<CommitInfo> get(
			@ApiParam(value = "Commit ID to match")
			@PathVariable(value="commitId")
			final String commitId, 
			
			@ApiParam(value = "Expansion parameters")
			@RequestParam(value="expand", required=false)
			final String expand) {
		return DeferredResults.wrap(
				RepositoryRequests
					.commitInfos()
					.prepareGetCommitInfo(commitId)
					.setExpand(expand)
					.build(repositoryId)
					.execute(getBus()));
	}
}
