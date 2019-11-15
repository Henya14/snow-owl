/*
 * Copyright 2011-2019 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.repository;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.b2international.commons.extension.ClassPathScanner;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.revision.Hooks;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.snowowl.core.Repository;
import com.b2international.snowowl.core.RepositoryInfo.Health;
import com.b2international.snowowl.core.merge.ComponentRevisionConflictProcessor;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.datastore.CodeSystem;
import com.b2international.snowowl.datastore.CodeSystemVersionEntry;
import com.b2international.snowowl.datastore.request.IndexReadRequest;
import com.b2international.snowowl.datastore.review.ConceptChanges;
import com.b2international.snowowl.datastore.review.Review;
import com.b2international.snowowl.datastore.version.VersioningRequestBuilder;

/**
 * @since 4.5
 */
public final class RepositoryBuilder {
	
	private final String repositoryId;
	private final DefaultRepositoryManager manager;
	
	private int mergeMaxResults;
	private TerminologyRepositoryInitializer initializer;
	private Hooks.PreCommitHook hook;
	private Logger log;
	private VersioningRequestBuilder versioningRequestBuilder;
	private ComponentDeletionPolicy deletionPolicy;
	
	private final Mappings mappings = new Mappings(
		Review.class, 
		ConceptChanges.class, 
		CodeSystem.class, 
		CodeSystemVersionEntry.class
	);
	private ComponentRevisionConflictProcessor componentRevisionConflictProcessor;

	RepositoryBuilder(DefaultRepositoryManager defaultRepositoryManager, String repositoryId) {
		this.manager = defaultRepositoryManager;
		this.repositoryId = repositoryId;
	}

	public RepositoryBuilder setMergeMaxResults(int mergeMaxResults) {
		this.mergeMaxResults = mergeMaxResults;
		return this;
	}
	
	public RepositoryBuilder withInitializer(TerminologyRepositoryInitializer initializer) {
		this.initializer = initializer;
		return this;
	}
	
	public RepositoryBuilder addMappings(Collection<Class<?>> mappings) {
		mappings.forEach(this.mappings::putMapping);
		return this;
	}
	
	public RepositoryBuilder withPreCommitHook(Hooks.PreCommitHook hook) {
		this.hook = hook;
		return this;
	}
	
	public RepositoryBuilder logger(Logger log) {
		this.log = log;
		return this;
	}
	
	public RepositoryBuilder withVersioningRequestBuilder(VersioningRequestBuilder versioningRequestBuilder) {
		this.versioningRequestBuilder = versioningRequestBuilder;
		return this;
	}
	
	public RepositoryBuilder withComponentDeletionPolicy(ComponentDeletionPolicy deletionPolicy) {
		this.deletionPolicy = deletionPolicy;
		return this;
	}

	public RepositoryBuilder withComponentRevisionConflictProcessor(ComponentRevisionConflictProcessor componentRevisionConflictProcessor) {
		this.componentRevisionConflictProcessor = componentRevisionConflictProcessor;
		return this;
	}
	
	public Repository build(Environment env) {
		// get all repository configuration plugins and apply them to customize the repository
		List<TerminologyRepositoryConfigurer> repositoryConfigurers = ClassPathScanner.INSTANCE.getComponentsByInterface(TerminologyRepositoryConfigurer.class)
			.stream()
			.filter(configurer -> repositoryId.equals(configurer.getRepositoryId()))
			.collect(Collectors.toList());
		
		repositoryConfigurers
			.forEach(configurer -> {
				configurer.getAdditionalMappings().forEach(mappings::putMapping);
			});
		
		final TerminologyRepository repository = new TerminologyRepository(repositoryId, mergeMaxResults, env, mappings, log);
		repository.bind(VersioningRequestBuilder.class, versioningRequestBuilder);
		repository.bind(ComponentDeletionPolicy.class, deletionPolicy);
		repository.bind(ComponentRevisionConflictProcessor.class, componentRevisionConflictProcessor);
		repository.activate();
		repository.service(RevisionIndex.class).hooks().addHook(hook);
		manager.put(repositoryId, repository);
		
		// execute initialization steps
		repository.waitForHealth(Health.GREEN, 3 * 60L /*wait 3 minutes for GREEN repository status*/);
		new IndexReadRequest<Void>((context) -> {
			initializer.initialize(context);
			return null;
		}).execute(manager.getContext(repositoryId));
		
		return repository;
	}

}
