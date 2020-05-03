/*
 * Copyright 2018-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.request.rf2.exporter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.elasticsearch.common.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.commons.BooleanUtils;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.branch.BranchPathUtils;
import com.b2international.snowowl.core.codesystem.CodeSystemEntry;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystemVersionEntry;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.codesystem.version.CodeSystemVersionSearchRequestBuilder;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.PageableCollectionResource;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.repository.RepositoryRequests;
import com.b2international.snowowl.core.request.BranchRequest;
import com.b2international.snowowl.core.request.RevisionIndexReadRequest;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequestIterator;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.b2international.snowowl.snomed.core.domain.SnomedComponent;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedSearchRequestBuilder;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;

/**
 * @since 6.3
 */
public abstract class Rf2Exporter<B extends SnomedSearchRequestBuilder<B, R>, R extends PageableCollectionResource<C>, C extends SnomedComponent> {

	private static final Logger LOG = LoggerFactory.getLogger("rf2.export");
	private static final Joiner TAB_JOINER = Joiner.on('\t');
	
	private static final String CR_LF = "\r\n";

	private static final int BATCH_SIZE = 10000;
	
	// Parameters used for file name calculations
	protected final Rf2ReleaseType releaseType;
	protected final String countryNamespaceElement;
	protected final String namespaceFilter;
	protected final String archiveEffectiveTime;

	private final String transientEffectiveTime;
	private final Collection<String> modules;

	public Rf2Exporter(final Rf2ReleaseType releaseType, 
			final String countryNamespaceElement,
			final String namespaceFilter, 
			final String transientEffectiveTime, 
			final String archiveEffectiveTime, 
			final Collection<String> modules) {

		this.releaseType = releaseType;
		this.countryNamespaceElement = countryNamespaceElement;
		this.namespaceFilter = namespaceFilter;
		this.transientEffectiveTime = transientEffectiveTime;
		this.archiveEffectiveTime = archiveEffectiveTime;
		this.modules = modules;
	}

	protected abstract Path getRelativeDirectory();

	protected abstract Path getFileName();

	protected abstract String[] getHeader();

	protected abstract B createSearchRequestBuilder();

	protected abstract Stream<List<String>> getMappedStream(R results, RepositoryContext context, String branch);

	protected final String getEffectiveTime(final SnomedComponent component) {
		return getEffectiveTime(component.getEffectiveTime());
	}
	
	protected final String getEffectiveTime(final Date effectiveTime) {
		if (effectiveTime == null) {
			// FIXME: Should we add a test for unexpected encounters of unversioned content here?
			return transientEffectiveTime;
		} else {
			return EffectiveTimes.format(effectiveTime, DateFormats.SHORT); 
		}
	}

	protected final String getActive(final SnomedComponent component) {
		return BooleanUtils.toString(component.isActive());
	}

	public final void exportBranch(
			final Path releaseDirectory, 
			final RepositoryContext context, 
			final String branch, 
			final boolean includePreReleaseContent,
			final long effectiveTimeStart, 
			final long effectiveTimeEnd,
			final Set<String> visitedComponentEffectiveTimes) throws IOException {

		LOG.info("Exporting {} branch to '{}'", branch, getFileName());
		
		// Ensure that the path leading to the export file exists
		final Path exportFileDirectory = releaseDirectory.resolve(getRelativeDirectory());
		Files.createDirectories(exportFileDirectory);

		final Path exportFile = exportFileDirectory.resolve(getFileName());
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(exportFile.toFile(), "rw")) {
			try (FileChannel fileChannel = randomAccessFile.getChannel()) {

				// Add a header if the file is empty
				if (randomAccessFile.length() == 0L) {
					fileChannel.write(toByteBuffer(TAB_JOINER.join(getHeader())));
					fileChannel.write(toByteBuffer(CR_LF));
				}

				// We want to append rows, if the file already exists, so jump to the end
				fileChannel.position(fileChannel.size());
				
				if (releaseType.equals(Rf2ReleaseType.SNAPSHOT) && !includePreReleaseContent) {
					//Collect unpublished released component ids and fetch them from parent branch
					final B requestBuilder = createSearchRequestBuilder()
							.filterByModules(modules) // null value will be ignored
							.filterByEffectiveTime(EffectiveTimes.UNSET_EFFECTIVE_TIME)
							.filterByReleased(true)
							.setLimit(BATCH_SIZE);
					
					final SearchResourceRequestIterator<B, R> iterator = new SearchResourceRequestIterator<>(requestBuilder, scrolledBuilder -> {
						return new BranchRequest<R>(
							branch, 
							new RevisionIndexReadRequest<>(scrolledBuilder.build())
						)
						.execute(context);
					});
					
					//final String parentBranch = String.format("%s^", branch); 
					final String parentBranch = getLatestVersionPath(context, branch);
					Set<String> unpublishedComponents = Sets.newHashSet();
					while (iterator.hasNext()) {
						final R hits = iterator.next();
						unpublishedComponents.addAll(getMappedStream(hits, context, branch).map(row -> row.get(0)).collect(Collectors.toSet()));
					}
					
					final B parentBranchRequestBuilder = createSearchRequestBuilder()
							.filterByIds(unpublishedComponents)
							.setLimit(unpublishedComponents.size());
					
					final SearchResourceRequestIterator<B, R> parentBranchSearchIterator = new SearchResourceRequestIterator<>(parentBranchRequestBuilder, scrolledBuilder -> {
						return new BranchRequest<R>(
							parentBranch, 
							new RevisionIndexReadRequest<>(scrolledBuilder.build())
						)
						.execute(context);
					});
					
					exportBatch(parentBranch, context, parentBranchSearchIterator, visitedComponentEffectiveTimes, fileChannel, exportFile.getFileName());
				}

				/*
				 * XXX: createSearchRequestBuilder() should handle namespace/language code
				 * filtering, if applicable; we will only handle the effective time and module
				 * filters here.
				 * 
				 * An effective time filter is always set, even if not in delta mode, to prevent
				 * exporting unpublished content twice.
				 */
				final B requestBuilder = createSearchRequestBuilder()
						.filterByModules(modules) // null value will be ignored
						.filterByEffectiveTime(effectiveTimeStart, effectiveTimeEnd)
						.setLimit(BATCH_SIZE);
				
				final SearchResourceRequestIterator<B, R> iterator = new SearchResourceRequestIterator<>(requestBuilder, scrolledBuilder -> {
					return new BranchRequest<R>(
						branch, 
						new RevisionIndexReadRequest<>(scrolledBuilder.build())
					)
					.execute(context);
				});
				
				exportBatch(branch, context, iterator, visitedComponentEffectiveTimes, fileChannel, exportFile.getFileName());
			}
		}
	}
	
	private String getLatestVersionPath(RepositoryContext context, String branchPath) {
		IEventBus bus = context.service(IEventBus.class);
		
		long branchBaseTimestamp = RepositoryRequests.branching()
				.prepareGet(branchPath)
				.build()
				.execute(context)
				.baseTimestamp();
		
		final CodeSystems codeSystems = CodeSystemRequests.prepareSearchCodeSystem()
				.all()
				.build(SnomedDatastoreActivator.REPOSITORY_UUID)
				.execute(bus)
				.getSync(1, TimeUnit.MINUTES);

		final Map<String, CodeSystemEntry> codeSystemsByMainBranch = Maps.uniqueIndex(codeSystems, CodeSystemEntry::getBranchPath);

		final List<CodeSystemEntry> relativeCodeSystems = Lists.newArrayList();

		final Iterator<IBranchPath> bottomToTop = BranchPathUtils.bottomToTopIterator(BranchPathUtils.createPath(branchPath));

		while (bottomToTop.hasNext()) {
			final IBranchPath candidate = bottomToTop.next();
			if (codeSystemsByMainBranch.containsKey(candidate.getPath())) {
				relativeCodeSystems.add(codeSystemsByMainBranch.get(candidate.getPath()));
			}
		}
		if (relativeCodeSystems.isEmpty()) {
			throw new IllegalStateException("No relative code system has been found for branch '" + branchPath + "'");
		}

		final CodeSystemEntry workingCodeSystem = relativeCodeSystems.stream().findFirst().get();

		CodeSystemVersionSearchRequestBuilder versionSearch = CodeSystemRequests.prepareSearchCodeSystemVersion()
				.one()
				.filterByCodeSystemShortName(workingCodeSystem.getShortName())
				.sortBy(SearchResourceRequest.SortField.descending(CodeSystemVersionEntry.Fields.EFFECTIVE_DATE));
		
		if (branchBaseTimestamp > 0L && !branchPath.equals(workingCodeSystem.getBranchPath())) {
			versionSearch.filterByCreatedAt(0L, branchBaseTimestamp);
		}
		
		final Optional<CodeSystemVersionEntry> workingCodeSystemVersion = versionSearch
				.build(SnomedDatastoreActivator.REPOSITORY_UUID)
				.execute(bus)
				.getSync()
				.first();

		final List<CodeSystemVersionEntry> relativeCodeSystemVersions = Lists.newArrayList();

		if (workingCodeSystemVersion.isPresent() && !Strings.isNullOrEmpty(workingCodeSystemVersion.get().getPath())) {
			relativeCodeSystemVersions.add(workingCodeSystemVersion.get());
		}

		if (relativeCodeSystems.size() > 1) {

			relativeCodeSystems.stream().skip(1).forEach(codeSystem -> {

				final Map<String, CodeSystemVersionEntry> pathToVersionMap = CodeSystemRequests.prepareSearchCodeSystemVersion()
						.all()
						.filterByCodeSystemShortName(codeSystem.getShortName())
						.build(SnomedDatastoreActivator.REPOSITORY_UUID)
						.execute(bus)
						.getSync(1, TimeUnit.MINUTES)
						.stream()
						.collect(Collectors.toMap(version -> version.getPath(), v -> v));

				final Iterator<IBranchPath> branchPathIterator = BranchPathUtils.bottomToTopIterator(BranchPathUtils.createPath(branchPath));

				while (branchPathIterator.hasNext()) {
					final IBranchPath candidate = branchPathIterator.next();
					if (pathToVersionMap.containsKey(candidate.getPath())) {
						relativeCodeSystemVersions.add(pathToVersionMap.get(candidate.getPath()));
						break;
					}
				}

			});

		}

		return relativeCodeSystemVersions.stream()
				// sort versions by effective date in reversed order
				.sorted((v1, v2) -> Longs.compare(v2.getEffectiveDate(), v1.getEffectiveDate()))
				.map(CodeSystemVersionEntry::getPath).collect(Collectors.toList())
				.get(0);
	}

	private void exportBatch(String branch, RepositoryContext context, SearchResourceRequestIterator<B, R> iterator,
			Set<String> visitedComponentEffectiveTimes, FileChannel fileChannel, Path exportFileName) {
		while (iterator.hasNext()) {
			final R hits = iterator.next();
			
			getMappedStream(hits, context, branch)
				.forEachOrdered(row -> {
					String id = row.get(0);
					String effectiveTime = row.get(1);
					
					if (!visitedComponentEffectiveTimes.add(String.format("%s_%s", id, effectiveTime))) {
						return;
					}
					
					try {
						fileChannel.write(toByteBuffer(TAB_JOINER.join(row)));
						fileChannel.write(toByteBuffer(CR_LF));
					} catch (final IOException e) {
						throw new SnowowlRuntimeException("Failed to write contents for file '" + exportFileName + "'.");
					}
				});
		}
	}

	private static ByteBuffer toByteBuffer(final String s) {
		return ByteBuffer.wrap(s.getBytes(Charsets.UTF_8));
	}
}
