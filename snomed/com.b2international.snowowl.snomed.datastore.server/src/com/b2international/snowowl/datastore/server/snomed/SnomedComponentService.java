/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.datastore.server.snomed;

import static com.b2international.commons.CompareUtils.isEmpty;
import static com.b2international.commons.StringUtils.isEmpty;
import static com.b2international.commons.collect.LongSets.newLongSet;
import static com.b2international.commons.collect.LongSets.toStringSet;
import static com.b2international.snowowl.core.ApplicationContext.getServiceForClass;
import static com.b2international.snowowl.snomed.SnomedConstants.Concepts.DEFINING_CHARACTERISTIC_TYPES;
import static com.b2international.snowowl.snomed.SnomedConstants.Concepts.REFSET_DESCRIPTION_INACTIVITY_INDICATOR;
import static com.b2international.snowowl.snomed.SnomedConstants.Concepts.REFSET_MODULE_DEPENDENCY_TYPE;
import static com.b2international.snowowl.snomed.SnomedConstants.Concepts.ROOT_CONCEPT;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.CONCEPT_NUMBER;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.DESCRIPTION_NUMBER;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER;
import static com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil.deserializeValue;
import static com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil.isMapping;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.Multimaps.synchronizedMultimap;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.hash.Hashing.murmur3_32;
import static java.text.NumberFormat.getIntegerInstance;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.cdo.common.commit.CDOCommitInfo;
import org.eclipse.emf.cdo.common.id.CDOID;
import org.eclipse.emf.cdo.common.id.CDOIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.collections.PrimitiveLists;
import com.b2international.collections.PrimitiveMaps;
import com.b2international.collections.PrimitiveSets;
import com.b2international.collections.longs.LongCollection;
import com.b2international.collections.longs.LongCollections;
import com.b2international.collections.longs.LongIterator;
import com.b2international.collections.longs.LongKeyLongMap;
import com.b2international.collections.longs.LongKeyMap;
import com.b2international.collections.longs.LongList;
import com.b2international.collections.longs.LongListIterator;
import com.b2international.collections.longs.LongSet;
import com.b2international.commons.CompareUtils;
import com.b2international.commons.Pair;
import com.b2international.commons.StringUtils;
import com.b2international.commons.collect.LongSets;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.api.index.IndexException;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.IPostStoreUpdateListener;
import com.b2international.snowowl.datastore.IPostStoreUpdateListener2;
import com.b2international.snowowl.datastore.cdo.CDOUtils;
import com.b2international.snowowl.datastore.cdo.ICDOConnection;
import com.b2international.snowowl.datastore.cdo.ICDOConnectionManager;
import com.b2international.snowowl.datastore.server.snomed.index.NamespaceMapping;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.Concept;
import com.b2international.snowowl.snomed.Description;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.SnomedPackage;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.ISnomedConcept;
import com.b2international.snowowl.snomed.core.domain.ISnomedDescription;
import com.b2international.snowowl.snomed.core.domain.ISnomedRelationship;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMember;
import com.b2international.snowowl.snomed.core.lang.LanguageSetting;
import com.b2international.snowowl.snomed.datastore.ILanguageConfigurationProvider;
import com.b2international.snowowl.snomed.datastore.PredicateUtils;
import com.b2international.snowowl.snomed.datastore.SnomedConceptInactivationIdCollector;
import com.b2international.snowowl.snomed.datastore.SnomedConceptLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedDescriptionFragment;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedIconProvider;
import com.b2international.snowowl.snomed.datastore.SnomedModuleDependencyRefSetMemberFragment;
import com.b2international.snowowl.snomed.datastore.SnomedPredicateBrowser;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetMemberFragment;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptDocument;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedDescriptionIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedRefSetMemberIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService;
import com.b2international.snowowl.snomed.datastore.snor.PredicateIndexEntry;
import com.b2international.snowowl.snomed.mrcm.HierarchyInclusionType;
import com.b2international.snowowl.snomed.snomedrefset.DataType;
import com.b2international.snowowl.snomed.snomedrefset.SnomedLanguageRefSetMember;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Service singleton for the SNOMED&nbsp;CT core components.
 * <p>This class is an implementation of {@link IPostStoreUpdateListener}. 
 * All the cached and pre-calculated data structure gets updated after lightweight store updates.<br>
 * E.g.: This service implementation caches all Synonym concept and its all descendants. This cached structure gets updated after 
 * each RDBMS, index and all other ephemeral store update after a CDO invalidation event.
 * 
 * @see IPostStoreUpdateListener
 */
@SuppressWarnings("unchecked")
public class SnomedComponentService implements ISnomedComponentService, IPostStoreUpdateListener2 {

//	private static final Set<String> COMPONENT_ID_MODULE_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad().id().module().build();
//	private static final Set<String> MEMBER_REFERENCED_COMPONENT_ID_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad().memberReferencedComponentId().build();
//	private static final Set<String> MEMBER_VALUE_ID_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad().memberValueId().build();
//	private static final Set<String> MEMBER_ID_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad().memberUuid().active().memberReferencedComponentId().build();
//	private static final Set<String> MODULE_MEMBER_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad().storageKey().module().memberReferencedComponentId()
//			.memberSourceEffectiveTime().memberTargetEffectiveTime().build();
//	
//	private static final Set<String> COMPONENT_ID_KEY_TO_LOAD = SnomedMappings.fieldsToLoad().id().build();
//	private static final Set<String> COMPONENT_ID_STORAGE_KEY_TO_LOAD = SnomedMappings.fieldsToLoad().id().storageKey().build();
//	private static final Set<String> MEMBER_UUID_STORAGE_KEY_TO_LOAD = SnomedMappings.fieldsToLoad().storageKey().memberUuid().build();
//	private static final long DESCRIPTION_TYPE_ROOT_CONCEPT_ID = Long.valueOf(Concepts.DESCRIPTION_TYPE_ROOT_CONCEPT);
//	private static final long SYNONYM_CONCEPT_ID = Long.valueOf(Concepts.SYNONYM);
//	private static final Set<String> COMPONENT_LABEL_TO_LOAD = SnomedMappings.fieldsToLoad().descriptionTerm().build();
//	private static final Set<String> COMPONENT_STATUS_TO_LOAD = SnomedMappings.fieldsToLoad().active().build();
//	private static final Set<String> COMPONENT_ICON_ID_TO_LOAD = SnomedMappings.fieldsToLoad().iconId().build();
//	
//	private static final Set<String> RELATIONSHIP_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad()
//			.relationshipType()
//			.relationshipSource()
//			.relationshipDestination()
//			.relationshipDestinationNegated()
//			.build();
//	
//	private static final Set<String> DESCRIPTION_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad()
//			.descriptionTerm()
//			.descriptionType()
//			.descriptionConcept()
//			.build();
//	
//	private static final Set<String> DESCRIPTION_EXTENDED_FIELDS_TO_LOAD = SnomedMappings.fieldsToLoad()
//			.id()
//			.effectiveTime()
//			.storageKey()
//			.descriptionTerm()
//			.descriptionConcept()
//			.descriptionType()
//			.build();
//	
//	private static final Query PREFERRED_LANGUAGE_QUERY = new TermQuery(new Term(SnomedMappings.memberAcceptabilityId().fieldName(), longToPrefixCoded(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED)));
//	private static final Query ACCEPTED_LANGUAGE_QUERY = new TermQuery(new Term(SnomedMappings.memberAcceptabilityId().fieldName(), longToPrefixCoded(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_ACCEPTABLE)));
//	private static final Query DESCRIPTION_INACTIVATION_REFSET_QUERY = SnomedMappings.newQuery().memberRefSetId(REFSET_DESCRIPTION_INACTIVITY_INDICATOR).matchAll();
//	private static final Query ALL_CORE_COMPONENTS_QUERY;
//	
//	static {
//		final Query typeQuery = SnomedMappings.newQuery().type(CONCEPT_NUMBER).type(DESCRIPTION_NUMBER).type(RELATIONSHIP_NUMBER).matchAny();
//		ALL_CORE_COMPONENTS_QUERY = SnomedMappings.newQuery().and(typeQuery).matchAll();
//	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnomedComponentService.class);
	private final LoadingCache<IBranchPath, LoadingCache<CacheKeyType, Object>> cache;
	
	private final Map<IBranchPath, Job> jobMap = Maps.newHashMap();
	
	/**
	 * Populates the cache.
	 */
	public SnomedComponentService() {
		cache = CacheBuilder.newBuilder()
			.build(new CacheLoader<IBranchPath, LoadingCache<CacheKeyType, Object>>() {
				@Override
				public LoadingCache<CacheKeyType, Object> load(final IBranchPath branchPath) throws Exception {
					return CacheBuilder.newBuilder()
							.build(new CacheLoader<CacheKeyType, Object>() {
								@Override
								public Object load(final CacheKeyType key) throws Exception {
									return loadValue(branchPath, key);
								}
							});
				}
			});
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.datastore.IPostStoreUpdateListener2#getRepositoryUuid()
	 */
	@Override
	public String getRepositoryUuid() {
		final ICDOConnection connection = ApplicationContext.getInstance().getService(ICDOConnectionManager.class).get(SnomedPackage.eINSTANCE);
		return connection.getUuid();
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.b2international.snowowl.core.api.IPostStoreUpdateListener#storeUpdated(java.lang.Object)
	 */
	@Override
	public void storeUpdated(final CDOCommitInfo commitInfo) {
		if (commitInfo == null)
			return;
		
		final IBranchPath branchPath = BranchPathUtils.createPath(commitInfo.getBranch());
		
		synchronized (branchPath) {
			Job job = null;
			if (jobMap.containsKey(branchPath)) {
				job = jobMap.get(branchPath);
			} else {
				job = new BranchCacheLoadingJob(branchPath);
				jobMap.put(branchPath, job);
			}
			job.schedule();
		}
	}

	/**
	 * Returns with the available concrete domain data type labels for a specified concrete domain data type.
	 * @param dataType the data type. E.g.: {@code BOOLEAN} or {@code DECIMAL}.
	 * @return a set of concrete domain data type labels for a specified data type.
	 */
	@Override
	public Set<String> getAvailableDataTypeLabels(final IBranchPath branchPath, final DataType dataType) {
		checkAndJoin(branchPath, null);
		try {
			return ((Map<DataType, Set<String>>) cache.get(branchPath).get(CacheKeyType.DATA_TYPE_LABELS)).get(dataType);
		} catch (final ExecutionException e) {
			LOGGER.error("Error while getting available concrete domain data type labels for " + dataType, e);
			throw new UncheckedExecutionException(e);
		}
	}
	
	@Override
	public Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> getPredicates(final IBranchPath branchPath) {
		checkAndJoin(branchPath, null);
		try {
			return (Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>>) cache.get(branchPath).get(CacheKeyType.PREDICATE_TYPES);
		} catch (final ExecutionException e) {
			LOGGER.error("Error while getting available MRCM predicates on '" + branchPath + "' branch.", e);
			throw new UncheckedExecutionException(e);
		}
	}
	
	@Override
	public String[] getDescriptionProperties(final IBranchPath branchPath, final String descriptionId) {
		checkNotNull(branchPath, "Branch path argument cannot be null.");
		checkNotNull(descriptionId, "SNOMED CT description ID argument cannot be null.");
		
		//if not a valid relationship ID
		if (SnomedTerminologyComponentConstants.DESCRIPTION_NUMBER != 
				SnomedTerminologyComponentConstants.getTerminologyComponentIdValueSafe(descriptionId)) {
			return null;
		}
		final TopDocs topDocs = getIndexServerService().search(branchPath, SnomedMappings.newQuery().type(DESCRIPTION_NUMBER).id(descriptionId).matchAll(), 1);
		if (IndexUtils.isEmpty(topDocs)) {
			return null;
		}
		final ScoreDoc scoreDoc = topDocs.scoreDocs[0];
		final Document doc = getIndexServerService().document(branchPath, scoreDoc.doc, DESCRIPTION_FIELDS_TO_LOAD);
		
		final String label = SnomedMappings.descriptionTerm().getValue(doc);
		final String conceptId = SnomedMappings.descriptionConcept().getValueAsString(doc);
		final String typeId = SnomedMappings.descriptionType().getValueAsString(doc);
		return new String[] { conceptId, typeId, label };
	}
	
	@Override
	public LongSet getAllReferringMembersStorageKey(final IBranchPath branchPath, final String componentId, final EnumSet<SnomedRefSetType> types) {
		
		checkNotNull(branchPath, "Branch path argument cannot be null.");
		checkNotNull(componentId, "SNOMED CT component ID argument cannot be null.");
		checkArgument(!types.isEmpty(), "At least one reference set type must be specified.");
	
		final SnomedQueryBuilder typeQuery = SnomedMappings.newQuery();
		
		for (final SnomedRefSetType type : types) {
			typeQuery.memberRefSetType(type);
		}
		
		final SnomedQueryBuilder idQuery = SnomedMappings.newQuery();
		idQuery.memberReferencedComponentId(componentId);
		
		for (final SnomedRefSetType type : types) {
			final String field = SnomedRefSetUtil.getSpecialComponentIdIndexField(type);
			if (!StringUtils.isEmpty(field)) {
				idQuery.field(field, componentId);
			}
		}
		
		final Query query = SnomedMappings.newQuery()
				.and(typeQuery.matchAny()) // at least one of the type queries have to match
				.and(idQuery.matchAny()) // at least one of the ID queries have to match
				.matchAll();
		
		@SuppressWarnings("rawtypes")
		final IndexServerService indexService = getIndexServerService();
		
		final int maxDoc = indexService.maxDoc(branchPath);
		final DocIdCollector collector = DocIdCollector.create(maxDoc);
		
		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		try {
			
			manager = indexService.getManager(branchPath);
			searcher = manager.acquire();
			
			indexService.search(branchPath, query, collector);
			
			final int hitCount = collector.getDocIDs().size();
			
			if (0 == hitCount) {
				
				return LongCollections.emptySet();
				
			}
			
			final LongSet $ = PrimitiveSets.newLongOpenHashSetWithExpectedSize(hitCount);

			final DocIdsIterator itr = collector.getDocIDs().iterator();
			
			while (itr.next()) {
				final Document doc = searcher.doc(itr.getDocID(), Mappings.fieldsToLoad().storageKey().build());
				$.add(Mappings.storageKey().getValue(doc));
			}
			
			return $;
			
		} catch (final IOException e) {
			LOGGER.error("Error while getting storage keys for components.");
			throw new SnowowlRuntimeException(e);
		} finally {
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
				}
			}
		}
	}
	
	@Override
	public Collection<IdStorageKeyPair> getAllComponentIdStorageKeys(final IBranchPath branchPath, final short terminologyComponentId) {
		
		checkNotNull(branchPath, "Branch path argument cannot be null.");
		
		Query query = null;
		Set<String> fieldsToLoad = null;
		IndexField<?> idField = SnomedMappings.id();
		IndexField<Long> storageKeyField = Mappings.storageKey();
		
		switch (terminologyComponentId) {
			
			case SnomedTerminologyComponentConstants.CONCEPT_NUMBER: //$FALL-THROUGH$
			case SnomedTerminologyComponentConstants.DESCRIPTION_NUMBER: //$FALL-THROUGH$
			case SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER: //$FALL-THROUGH$
				
				query = SnomedMappings.newQuery().type(terminologyComponentId).matchAll();
				fieldsToLoad = COMPONENT_ID_STORAGE_KEY_TO_LOAD;
				break;
			case SnomedTerminologyComponentConstants.REFSET_NUMBER:
				query = SnomedMappings.newQuery().type(terminologyComponentId).matchAll();
				fieldsToLoad = SnomedMappings.fieldsToLoad().id().refSetStorageKey().build();
				storageKeyField = SnomedMappings.refSetStorageKey();
				break;
			case SnomedTerminologyComponentConstants.REFSET_MEMBER_NUMBER:
				query = SnomedMappings.memberUuid().toExistsQuery();
				fieldsToLoad = MEMBER_UUID_STORAGE_KEY_TO_LOAD;
				idField = SnomedMappings.memberUuid();
				break;
				
			default:
				throw new IllegalArgumentException("Unknown terminology component ID for SNOMED CT: '" + terminologyComponentId + "'.");
			
				
		}

		
		@SuppressWarnings("rawtypes")
		final IndexServerService indexService = getIndexServerService();
		
		final int maxDoc = indexService.maxDoc(branchPath);
		final DocIdCollector collector = DocIdCollector.create(maxDoc);
		
		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		try {
			
			manager = indexService.getManager(branchPath);
			searcher = manager.acquire();
			
			indexService.search(branchPath, query, collector);
			
			final int hitCount = collector.getDocIDs().size();
			final IdStorageKeyPair[] $ = new IdStorageKeyPair[hitCount];

			final DocIdsIterator itr = collector.getDocIDs().iterator();
			
			int i = 0;
			while (itr.next()) {
				
				final Document doc = searcher.doc(itr.getDocID(), fieldsToLoad);
				$[i++] = new IdStorageKeyPair(
						checkNotNull(idField.getValueAsString(doc), "Cannot get ID field for document. [" + doc + "]"),
						storageKeyField.getValue(doc));
				
			}
			
			return Arrays.asList($);
			
		} catch (final IOException e) {
			
			LOGGER.error("Error while getting component ID and storage keys for components.");
			throw new SnowowlRuntimeException(e);
			
		} finally {
			
			if (null != manager && null != searcher) {
				
				try {
					
					manager.release(searcher);
					
				} catch (final IOException e) {
					
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
					
				}
				
			}
			
		}
		
		
	}
	
	@Override
	public LongSet getComponentByRefSetIdAndReferencedComponent(final IBranchPath branchPath, final String refSetId, final short referencedComponentType) {
		
		checkNotNull(branchPath, "Branch path argument cannot be null.");
		checkNotNull(refSetId, "Reference set ID argument cannot be null.");
		
		IndexSearcher searcher = null;
		ReferenceManager<IndexSearcher> manager = null;
		
		try {
			
			@SuppressWarnings("rawtypes")
			final IndexServerService indexService = getIndexServerService();
			manager = indexService.getManager(branchPath);
			searcher = manager.acquire();

			final Set<String> referencedComponentIds = getReferencedComponentIdsByRefSetId(branchPath, indexService, manager, searcher, refSetId);
			final LongSet $ = getComponentStorageKeysByRefSetIdsAndComponentType(branchPath, indexService, manager, searcher, referencedComponentIds, referencedComponentType);

			return $;
		} catch (final IOException e) {
			LOGGER.error("Error while getting reference set member referenced component storage keys.");
			throw new SnowowlRuntimeException(e);
		} finally {
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
				}
			}
		}
	}

	@Override
	public LongKeyLongMap getConceptModuleMapping(final IBranchPath branchPath) {
		
		checkNotNull(branchPath, "branchPath");

		final int maxDoc = getIndexServerService().maxDoc(branchPath);
		
		final DocIdCollector collector = DocIdCollector.create(maxDoc);
		getIndexServerService().search(branchPath, SnomedMappings.newQuery().type(SnomedTerminologyComponentConstants.CONCEPT_NUMBER).matchAll(), collector);
		
		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		try {
			
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			manager = getIndexServerService().getManager(branchPath);
			searcher = manager.acquire();
			final LongKeyLongMap ids = PrimitiveMaps.newLongKeyLongOpenHashMap();

			while (itr.next()) {
				final Document doc = searcher.doc(itr.getDocID(), COMPONENT_ID_MODULE_FIELDS_TO_LOAD);
				ids.put(SnomedMappings.id().getValue(doc), SnomedMappings.module().getValue(doc));
			}
			
			return ids;
			
		} catch (final IOException e) {
			
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e1) {
					e.addSuppressed(e1);
				}
			}
			
			throw new SnowowlRuntimeException("Error while creating concept to module mapping.", e);
		}
		
	}
	
	@Override
	public Collection<SnomedRefSetMemberFragment> getRefSetMemberFragments(final IBranchPath branchPath, final String refSetId) {
		
		checkNotNull(branchPath, "branchPath");
		checkNotNull(refSetId, "branchPath");
		
		final Query refSetQuery = SnomedMappings.newQuery().refSet().id(refSetId).matchAll();
		final Query memberQuery = SnomedMappings.newQuery().memberRefSetId(refSetId).matchAll();
		final int maxDoc = getIndexServerService().maxDoc(branchPath);
		
		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		try {
			
			final TopDocs topDocs = getIndexServerService().search(branchPath, refSetQuery, 1);
			if (IndexUtils.isEmpty(topDocs)) {
				return emptySet();
			}

			manager = getIndexServerService().getManager(branchPath);
			searcher = manager.acquire();
			
			final Document refSetDoc = searcher.doc(topDocs.scoreDocs[0].doc, SnomedMappings.fieldsToLoad().refSetType().build());
			final SnomedRefSetType refSetType = SnomedRefSetType.get(SnomedMappings.refSetType().getValue(refSetDoc));
			
			final Set<SnomedRefSetMemberFragment> members = newHashSet();
			final DocIdCollector collector = create(maxDoc);
			getIndexServerService().search(branchPath, memberQuery, collector);
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			
			final Set<String> memberFieldsToLoad = newHashSet(MEMBER_ID_FIELDS_TO_LOAD);
			final String additionalFieldToLoad = SnomedRefSetUtil.getSpecialComponentIdIndexField(refSetType);
			if (!isEmpty(additionalFieldToLoad)) {
				memberFieldsToLoad.add(additionalFieldToLoad);
			}

			while (itr.next()) {
				final Document doc = searcher.doc(itr.getDocID(), memberFieldsToLoad);
				members.add(new SnomedRefSetMemberFragment(
						SnomedMappings.memberUuid().getValue(doc), 
						SnomedMappings.memberReferencedComponentId().getValueAsString(doc), 
						isEmpty(additionalFieldToLoad) ? doc.get(additionalFieldToLoad) : null, 
						SnomedMappings.active().getValue(doc) == 1));
			}
			
			return members;
			
		} catch (final IOException e) {
			LOGGER.error("Error while getting reference set fragments from reference set: " + refSetId);
			throw new SnowowlRuntimeException(e);
		} finally {
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
				}
			}
		}
		
	}

	@Override
	public LongSet getAllUnpublishedComponentStorageKeys(final IBranchPath branchPath) {
		checkNotNull(branchPath, "branchPath");
		return getUnpublishedStorageKeys(branchPath, SnomedMappings.newQuery().effectiveTime(EffectiveTimes.UNSET_EFFECTIVE_TIME).matchAll());
	}

	@Override
	public Collection<SnomedModuleDependencyRefSetMemberFragment> getExistingModules(final IBranchPath branchPath) {
		checkNotNull(branchPath, "branchPath");
		
		final Collection<SnomedModuleDependencyRefSetMemberFragment> modules = newHashSet();
		
		try {
			
			final int maxDoc = getIndexServerService().maxDoc(branchPath);
			final DocIdCollector collector = create(maxDoc);
			getIndexServerService().search(branchPath, SnomedMappings.newQuery().active().memberRefSetId(REFSET_MODULE_DEPENDENCY_TYPE).matchAll(), collector);
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			
			while (itr.next()) {
				final Document doc = getIndexServerService().document(branchPath, itr.getDocID(), MODULE_MEMBER_FIELDS_TO_LOAD);
				modules.add(createModuleMember(doc));
			}
			
		} catch (final IOException e) {
			final String message = "Error while resolving dependencies between existing modules.";
			LOGGER.error(message, e);
			throw new SnowowlRuntimeException(message, e);
		}
		
		return modules;
	}
	
	@Override
	public Map<String, Date> getExistingModulesWithEffectiveTime(final IBranchPath branchPath) {
		final ImmutableSet<SnomedModuleDependencyRefSetMemberFragment> existingModules = ImmutableSet.copyOf(getExistingModules(branchPath));

		final Set<String> existingModuleIds = newHashSet();
		for (final SnomedModuleDependencyRefSetMemberFragment fragment : existingModules) {
			existingModuleIds.add(fragment.getModuleId());
			existingModuleIds.add(fragment.getReferencedComponentId());
		}

		final Map<String, Date> modules = newHashMap();

		for (final String moduleId : existingModuleIds) {
			Date date = null;
			for (final SnomedModuleDependencyRefSetMemberFragment fragment : existingModules) {
				if (null != fragment.getSourceEffectiveTime() && fragment.getModuleId().equals(moduleId)) {
					date = date == null ? fragment.getSourceEffectiveTime() : fragment.getSourceEffectiveTime().compareTo(date) > 0 ? fragment.getSourceEffectiveTime() : date;
				} else if (null != fragment.getTargetEffectiveTime() && fragment.getReferencedComponentId().equals(moduleId)) {
					date = date == null ? fragment.getTargetEffectiveTime() : fragment.getTargetEffectiveTime().compareTo(date) > 0 ? fragment.getTargetEffectiveTime() : date;
				}
			}
			modules.put(moduleId, date);
		}
		return modules;
	}
	
	@Override
	public LongSet getSelfAndAllSubtypeStorageKeysForInactivation(final IBranchPath branchPath, final String... focusConceptIds) {
		checkNotNull(branchPath, "branchPath");
		checkNotNull(focusConceptIds, "focusConceptIds");
		final SnomedConceptInactivationIdCollector collector = new SnomedConceptInactivationIdCollector();
		final Collection<String> conceptIds = collector.collectSelfAndDescendantConceptIds(branchPath, focusConceptIds);
		final SnomedTerminologyBrowser terminologyBrowser = getServiceForClass(SnomedTerminologyBrowser.class);
		return newLongSet(LongSets.transform(conceptIds, new LongSets.LongFunction<String>() {
			@Override
			public long apply(final String conceptId) {
				return terminologyBrowser.getStorageKey(branchPath, conceptId);
			}
		}));
	}
	
	private SnomedModuleDependencyRefSetMemberFragment createModuleMember(final Document doc) {
		final SnomedModuleDependencyRefSetMemberFragment module = new SnomedModuleDependencyRefSetMemberFragment();
		module.setModuleId(SnomedMappings.module().getValueAsString(doc));
		module.setReferencedComponentId(SnomedMappings.memberReferencedComponentId().getValueAsString(doc));
		module.setStorageKey(Mappings.storageKey().getValue(doc));
		module.setSourceEffectiveTime(EffectiveTimes.toDate(SnomedMappings.memberSourceEffectiveTime().getValue(doc)));
		module.setTargetEffectiveTime(EffectiveTimes.toDate(SnomedMappings.memberTargetEffectiveTime().getValue(doc)));
		return module;
	}
	
	private LongSet getUnpublishedStorageKeys(final IBranchPath branchPath, final Query query) {
		checkNotNull(branchPath, "branchPath");
		checkNotNull(query, "query");
		
		final int maxDoc = getIndexServerService().maxDoc(branchPath);
		final DocIdCollector collector = DocIdCollector.create(maxDoc);
		getIndexServerService().search(branchPath, query, collector);
		
		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		try {
			
			final LongSet storageKeys = PrimitiveSets.newLongOpenHashSet(murmur3_32());
			manager = getIndexServerService().getManager(branchPath);
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			searcher = manager.acquire();
			
			while (itr.next()) {
				final Document doc = searcher.doc(itr.getDocID(), Mappings.fieldsToLoad().storageKey().build());
				storageKeys.add(Mappings.storageKey().getValue(doc));
			}
			
			return storageKeys;
			
		} catch (final IOException e) {
			LOGGER.error("Error while getting unpublished component storage keys.");
			throw new SnowowlRuntimeException(e);
		} finally {
			
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
				}
			}
		}
		
	}
	
	@Override
	public Map<String, Multimap<String, String>> getDescriptionPreferabilityMap(final IBranchPath branchPath, final String conceptId) {
		checkNotNull(branchPath, "branchPath");
		checkNotNull(conceptId, "conceptId");
		
		final Query descriptionQuery = SnomedMappings.newQuery().active().descriptionConcept(conceptId).matchAll();
		
		final Collection<String> descriptionIds = newHashSet(getIndexServerService().searchUnorderedIds(branchPath, descriptionQuery, null));
		if (isEmpty(descriptionIds)) {
			return emptyMap();
		}

		final SnomedQueryBuilder referencedComponentQuery = SnomedMappings.newQuery();
		for (final String descriptionId : descriptionIds) {
			referencedComponentQuery.memberReferencedComponentId(descriptionId);
		}
		
		final Query languageMemberQuery = SnomedMappings.newQuery()
				.active()
				.memberRefSetType(SnomedRefSetType.LANGUAGE)
				.and(referencedComponentQuery.matchAny())
				.matchAll();

		ReferenceManager<IndexSearcher> manager = null;
		IndexSearcher searcher = null;
		
		final Map<String, Multimap<String, String>> descriptionAcceptabilityMap = newHashMap();

		try {
			manager = getIndexServerService().getManager(branchPath);
			searcher = manager.acquire();
			
			final DocIdCollector collector = DocIdCollector.create(searcher.getIndexReader().maxDoc());
			searcher.search(languageMemberQuery, collector);
			
			final Set<String> fieldsToLoad = SnomedMappings.fieldsToLoad().memberReferencedComponentId().memberRefSetId().memberAcceptabilityId().build();
			
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			while (itr.next()) {
				final Document doc = searcher.doc(itr.getDocID(), fieldsToLoad);
				final String referencedComponentId = SnomedMappings.memberReferencedComponentId().getValueAsString(doc);
				final String refSetId = SnomedMappings.memberRefSetId().getValueAsString(doc);
				final String acceptabilityId = SnomedMappings.memberAcceptabilityId().getValueAsString(doc);
				if (!descriptionAcceptabilityMap.containsKey(referencedComponentId)) {
					final Multimap<String, String> acceptabilityMap = HashMultimap.create();
					descriptionAcceptabilityMap.put(referencedComponentId, acceptabilityMap);
				}
				descriptionAcceptabilityMap.get(referencedComponentId).put(acceptabilityId, refSetId);
			}
			
		} catch (final IOException e) {
			LOGGER.error("Error while getting description preferability mapping for concept '" + conceptId + "' on '" + branchPath + "' branch.");
			throw new SnowowlRuntimeException(e);
		} finally {
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					LOGGER.error("Error while releasing index searcher.");
					throw new SnowowlRuntimeException(e);
				}
			}
		}
		
		return descriptionAcceptabilityMap;
		
	}
	
	@SuppressWarnings("rawtypes")
	private Set<String> getReferencedComponentIdsByRefSetId(final IBranchPath branchPath, final IndexServerService indexService, 
			final ReferenceManager<IndexSearcher> manager, final IndexSearcher searcher, final String refSetId) throws IOException {
		
		final Query query = SnomedMappings.newQuery().memberRefSetId(refSetId).matchAll();
		
		final int maxDoc = indexService.maxDoc(branchPath);
		final DocIdCollector collector = DocIdCollector.create(maxDoc);
		indexService.search(branchPath, query, collector);
		final int hitCount = collector.getDocIDs().size();

		if (0 == hitCount) {
			return Collections.emptySet();
		}


		final Set<String> referencedComponentIds = newHashSet(); 
		final DocIdsIterator itr = collector.getDocIDs().iterator();
		while (itr.next()) {
			final Document doc = searcher.doc(itr.getDocID(), MEMBER_REFERENCED_COMPONENT_ID_FIELDS_TO_LOAD);
			referencedComponentIds.add(SnomedMappings.memberReferencedComponentId().getValueAsString(doc));
		}
		return referencedComponentIds;
	}

	@SuppressWarnings("rawtypes")
	private LongSet getComponentStorageKeysByRefSetIdsAndComponentType(final IBranchPath branchPath, final IndexServerService indexService, 
			final ReferenceManager<IndexSearcher> manager, final IndexSearcher searcher, final Set<String> referencedComponentIds, final short referencedComponentType) throws IOException {
		
		final LongSet storageKeys = PrimitiveSets.newLongOpenHashSet();
		final int maxDoc = indexService.maxDoc(branchPath);
		
		BooleanQuery query = new BooleanQuery(true);
		for (final String referencedComponentId : referencedComponentIds) {
			query.add(SnomedMappings.newQuery().type(referencedComponentType).id(referencedComponentId).matchAll(), SHOULD);

			if (query.getClauses().length + 1 == BooleanQuery.getMaxClauseCount()) {
				storageKeys.addAll(getStorageKeys(query, indexService, searcher, maxDoc, branchPath));
				query = new BooleanQuery(true);
			}
		}

		if (query.getClauses().length > 0) {
			storageKeys.addAll(getStorageKeys(query, indexService, searcher, maxDoc, branchPath));
		}

		return storageKeys;
	}

	/*returns with the identifier concept ID of the currently used language setting specified 
	 * by the selected SNOMED CT language type reference set*/
	private String getLanguageRefSetId() {
		return ApplicationContext.getInstance().getService(ILanguageConfigurationProvider.class).getLanguageConfiguration().getLanguageRefSetId();
	}
	
	/*loads the value to the cache identified with its unique key*/
	private Object loadValue(final IBranchPath branchPath, final CacheKeyType type) {
		switch (type) {
			case DATA_TYPE_LABELS:
				return getDataTypeLabels(branchPath);
			case PREDICATE_TYPES:
				return getAllPredicates(branchPath);
			case REFERENCE_SET_CDO_IDS:
				return internalGetRefSetCdoIdIdMapping(branchPath);
			default: 
				throw new IllegalArgumentException("Unknown cache key type: " + type);
		}
	}

	private Map<CDOID, String> internalGetRefSetCdoIdIdMapping(final IBranchPath branchPath) {
		@SuppressWarnings("rawtypes")
		final IndexServerService indexService = getIndexServerService();
		final Query refSetTypeQuery = SnomedMappings.newQuery().refSet().matchAll();
		final int hitCount = indexService.getHitCount(branchPath, refSetTypeQuery, null);
		final TopDocs topDocs = getIndexServerService().search(branchPath, refSetTypeQuery, hitCount);
		if (isEmpty(topDocs)) {
			return emptyMap();
		}
		final Map<CDOID, String> cdoIdToIdMap = newHashMap();
		for (final ScoreDoc scoreDoc : topDocs.scoreDocs) {
			final Document doc = indexService.document(branchPath, scoreDoc.doc, SnomedMappings.fieldsToLoad().refSetStorageKey().id().build());
			final CDOID cdoId = CDOIDUtil.createLong(SnomedMappings.refSetStorageKey().getValue(doc));
			final String refSetId = SnomedMappings.id().getValueAsString(doc);
			cdoIdToIdMap.put(cdoId, refSetId);
		}
		return unmodifiableMap(cdoIdToIdMap);
	}

	private Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> getAllPredicates(final IBranchPath branchPath) {
		checkNotNull(branchPath, "branchPath");
		final Map<HierarchyInclusionType, Multimap<String, PredicateIndexEntry>> predicates = newHashMap();
		
		predicates.put(HierarchyInclusionType.SELF, HashMultimap.<String, PredicateIndexEntry>create());
		predicates.put(HierarchyInclusionType.DESCENDANT, HashMultimap.<String, PredicateIndexEntry>create());
		predicates.put(HierarchyInclusionType.SELF_OR_DESCENDANT, HashMultimap.<String, PredicateIndexEntry>create());
		
		final ReferenceManager<IndexSearcher> manager = null;
		final IndexSearcher searcher = null;
		
		final Map<String, PredicateIndexEntry> predicateMappings = uniqueIndex(getServiceForClass(SnomedPredicateBrowser.class).getAllPredicates(branchPath), new Function<PredicateIndexEntry, String>() {
			@Override
			public String apply(final PredicateIndexEntry predicate) {
				return predicate.getId();
			}
		});
		
		try {
			
			final IndexServerService<?> service = getIndexServerService();
			final DocIdCollector collector = DocIdCollector.create(service.maxDoc(branchPath));
			final PrefixQuery query = SnomedMappings.componentReferringPredicate().toExistsQuery();
			query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
			service.search(branchPath, query, collector);
			final DocIdsIterator itr = collector.getDocIDs().iterator();
			
			while (itr.next()) {
				final Document doc = service.document(branchPath, itr.getDocID(), SnomedMappings.fieldsToLoad().id().componentReferringPredicate().build());
				final List<String> referringPredicates = SnomedMappings.componentReferringPredicate().getValues(doc);
				final String componentId = SnomedMappings.id().getValueAsString(doc);
				for (final String referringPredicate : referringPredicates) {
					final String[] split = referringPredicate.split(PredicateUtils.PREDICATE_SEPARATOR, 2);
					Preconditions.checkState(!isEmpty(split), "");
					final String predicateStorageKey = split[0];
					final String key = split[1];
					final PredicateIndexEntry predicate = predicateMappings.get(predicateStorageKey);
					final HierarchyInclusionType type = HierarchyInclusionType.get(key);
					if (type != null) {
						predicates.get(type).put(componentId, predicate);	
					} else if (SnomedTerminologyComponentConstants.getTerminologyComponentIdValueSafe(key) == CONCEPT_NUMBER) {
						predicates.get(HierarchyInclusionType.SELF).put(predicateStorageKey + "#" + componentId, predicate);	
					} else if (PredicateUtils.REFSET_PREDICATE_KEY_PREFIX.equals(key)) {
						predicates.get(HierarchyInclusionType.SELF).put(componentId, predicate);
					} else {
						throw new IllegalArgumentException("Cannot parse component referring predicate value: " + referringPredicate);
					}
				}
			}
			
		} catch (final IOException e) {
			throw new IndexException("Error while getting components referenced by MRCM predicates on '" + branchPath + "' branch.", e);
		} finally {
			if (null != manager && null != searcher) {
				try {
					manager.release(searcher);
				} catch (final IOException e) {
					try {
						manager.release(searcher);
					} catch (final IOException e1) {
						e.addSuppressed(e1);
					}
					throw new IndexException("Error while releasing index searcher.", e);
				}
			}
		}
		
		return unmodifiableMap(predicates);
	}

	/*checks the cache refreshing job. joins to the job if the job state is not NONE. makes the flow synchronous.*/
	private void checkAndJoin(final IBranchPath branchPath, @Nullable final String message) {
		if (jobMap.containsKey(branchPath)) {
			final Job cacheLoadingJob = jobMap.get(branchPath);
			// wait for the cache refreshing process, if it's already in progress on the specified branch
			if (Job.NONE != cacheLoadingJob.getState()) {
				try {
					cacheLoadingJob.join();
				} catch (final InterruptedException e) {
					LOGGER.error(null == message ? "Error while refreshing the cache." : message, e);
				}
			}
		}
	}
	
	/*returns with the concept hierarchy browser service for the SNOMED CT terminology*/
	private SnomedTerminologyBrowser getTerminologyBrowser() {
		return ApplicationContext.getInstance().getService(SnomedTerminologyBrowser.class);
	}
	
	/*initialize and returns with of map of data types and the available concrete domain data type labels.*/
	private Map<DataType, Set<String>> getDataTypeLabels(final IBranchPath branchPath) {
	
		final Map<DataType, Set<String>> dataTypeLabelMap = Maps.newHashMapWithExpectedSize(DataType.values().length);
		
		@SuppressWarnings("rawtypes")
		final IndexServerService service = (IndexServerService) getRefSetIndexService();
		
		final ReducedConcreteDomainFragmentCollector collector = new ReducedConcreteDomainFragmentCollector();
		service.search(branchPath, SnomedMappings.newQuery().memberRefSetType(SnomedRefSetType.CONCRETE_DATA_TYPE).matchAll(), collector);

		for (final DataType  type : DataType.values()) {
			
			dataTypeLabelMap.put(type, Sets.<String>newHashSet());
			final BytesRefHash labelRefHash = collector.getLabels(type);
			
			if (labelRefHash.size() < 1) {
				continue;
			}
			
			//execute a fake sort, wich does nothing but exposes the containing bytes ref IDs
			//as #compact visibility is reduced from Lucene 4.3
			final int[] compact = labelRefHash.sort(new Comparator<BytesRef>() {
				@Override public int compare(final BytesRef o1, final BytesRef o2) { return 0; }
			});
			
			final BytesRef spare = new BytesRef();
			
			for (final int i : compact) {
				
				if (i < 0) {
					continue;
				}
				
				labelRefHash.get(i, spare);
				dataTypeLabelMap.get(type).add(spare.utf8ToString());
			}
		}
		
		return dataTypeLabelMap;
	}

	/* initialize a map of namespace IDs and the associated extension namespace concept IDs */
	private LongKeyLongMap getNameSpaceIds(/*ignored*/final IBranchPath branchPath) {
		
		final LongKeyLongMap map = PrimitiveMaps.newLongKeyLongOpenHashMap();
		
		for (final SnomedConceptDocument concept : getTerminologyBrowser().getAllSubTypesById(branchPath, Concepts.NAMESPACE_ROOT)) {
			
			String namespaceId = null;
			
			// as of 20160112 the SG namespace identifier concepts have their namespace in the PT
			namespaceId = CharMatcher.DIGIT.retainFrom(concept.getLabel());
			
			if (StringUtils.isEmpty(namespaceId)) {
				namespaceId = "0"; //represents the core IHTSDO namespace
			}
			
			map.put(Long.parseLong(namespaceId), Long.parseLong(concept.getId()));
			
		}
		
		return map;
	}

	/*
	 * Executes the given query and returns with the found components storage keys.
	 */
	private LongSet getStorageKeys(final Query query, final IndexServerService<?> indexService, final IndexSearcher searcher, final int maxDoc, final IBranchPath branchPath) throws IOException {
		final LongSet resultSet = PrimitiveSets.newLongOpenHashSet();
		final DocIdCollector componentCollector = DocIdCollector.create(maxDoc);
		indexService.search(branchPath, query, componentCollector);
		final DocIdsIterator componentItr = componentCollector.getDocIDs().iterator();
		while (componentItr.next()) {
			final Document doc = searcher.doc(componentItr.getDocID(), SnomedMappings.fieldsToLoad().storageKey().build());
			resultSet.add(Mappings.storageKey().getValue(doc));
		}
		return resultSet;
	}

	private final class BranchCacheLoadingJob extends Job {
		private final IBranchPath branchPath;

		private BranchCacheLoadingJob(final IBranchPath branchPath) {
			super("Initializing for the '" + branchPath + "' branch.");
			this.branchPath = branchPath;
			setPriority(Job.INTERACTIVE);
			setUser(false);
			setSystem(true);
		}

		@Override protected IStatus run(final IProgressMonitor monitor) {
			monitor.beginTask("Initializing...", IProgressMonitor.UNKNOWN);
			final LoadingCache<CacheKeyType, Object> branchCache = cache.getIfPresent(branchPath);
			
			if (null != branchCache) {
				//synchronously refreshes the cache
				branchCache.invalidateAll();
				
				for (final CacheKeyType type : CacheKeyType.values()) {
					
					try {
						branchCache.getUnchecked(type);
					} catch (final UncheckedExecutionException e) {
						LOGGER.warn("Could not preload cached values for key " + type + ", skipping.");
					}
				}
			}
			return Status.OK_STATUS;
		}
	}

	/**
	 * Private enumerations for the cache keys.
	 */
	private static enum CacheKeyType {
		DATA_TYPE_LABELS,
		PREDICATE_TYPES,
		REFERENCE_SET_CDO_IDS,
		NAMESPACE_IDS;
	}
}
