/*
 * Copyright 2011-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.codesystem;

import com.b2international.snowowl.core.codesystem.CodeSystemSearchRequest.OptionKey;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.request.RepositoryRequestBuilder;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequestBuilder;

/**
 * @since 4.7
 */
public final class CodeSystemSearchRequestBuilder 
		extends SearchResourceRequestBuilder<CodeSystemSearchRequestBuilder, RepositoryContext, CodeSystems>
		implements RepositoryRequestBuilder<CodeSystems> {

	CodeSystemSearchRequestBuilder() {
		super();
	}
	
	public CodeSystemSearchRequestBuilder filterByToolingId(String toolingId) {
		return addOption(OptionKey.TOOLING_ID, toolingId);
	}

	public CodeSystemSearchRequestBuilder filterByToolingIds(Iterable<String> toolingIds) {
		return addOption(OptionKey.TOOLING_ID, toolingIds);
	}
	
	public CodeSystemSearchRequestBuilder filterByName(String term) {
		return addOption(OptionKey.NAME, term);
	}
	
	public CodeSystemSearchRequestBuilder filterByNameExact(String term) {
		return addOption(OptionKey.NAME_EXACT, term);
	}
	
	public CodeSystemSearchRequestBuilder filterByOid(String oid) {
		return addOption(OptionKey.OID, oid);
	}

	public CodeSystemSearchRequestBuilder filterByOids(Iterable<String> oids) {
		return addOption(OptionKey.OID, oids);
	}

	@Override
	protected SearchResourceRequest<RepositoryContext, CodeSystems> createSearch() {
		return new CodeSystemSearchRequest();
	}
}
