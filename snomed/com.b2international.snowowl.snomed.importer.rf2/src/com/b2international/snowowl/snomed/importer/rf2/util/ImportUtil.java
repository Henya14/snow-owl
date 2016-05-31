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
package com.b2international.snowowl.snomed.importer.rf2.util;

import static com.b2international.commons.CompareUtils.isEmpty;
import static com.b2international.snowowl.core.users.SpecialUserStore.SYSTEM_USER_NAME;
import static com.b2international.snowowl.snomed.importer.net4j.ImportConfiguration.ImportSourceKind.FILES;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.CONCEPT;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.DESCRIPTION;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.LANGUAGE_REFERENCE_SET;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.RELATIONSHIP;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.STATED_RELATIONSHIP;
import static com.b2international.snowowl.snomed.importer.release.ReleaseFileSet.ReleaseComponentType.TEXT_DEFINITION;
import static com.b2international.snowowl.snomed.importer.rf2.util.RF2ReleaseRefSetFileCollector.collectUrlFromRelease;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.cdo.common.branch.CDOBranch;
import org.eclipse.emf.cdo.util.CommitException;

import com.b2international.collections.longs.LongSet;
import com.b2international.commons.ConsoleProgressMonitor;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.platform.Extensions;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.LogUtils;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.cdo.ICDOConnectionManager;
import com.b2international.snowowl.datastore.oplock.IOperationLockManager;
import com.b2international.snowowl.datastore.oplock.IOperationLockTarget;
import com.b2international.snowowl.datastore.oplock.OperationLockException;
import com.b2international.snowowl.datastore.oplock.OperationLockRunner;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContext;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContextDescriptions;
import com.b2international.snowowl.datastore.oplock.impl.IDatastoreOperationLockManager;
import com.b2international.snowowl.datastore.oplock.impl.SingleRepositoryAndBranchLockTarget;
import com.b2international.snowowl.datastore.server.CDOServerCommitBuilder;
import com.b2international.snowowl.datastore.server.CDOServerUtils;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.importer.ImportException;
import com.b2international.snowowl.importer.Importer;
import com.b2international.snowowl.snomed.SnomedPackage;
import com.b2international.snowowl.snomed.SnomedRelease;
import com.b2international.snowowl.snomed.SnomedReleaseType;
import com.b2international.snowowl.snomed.common.ContentSubType;
import com.b2international.snowowl.snomed.core.domain.ISnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSet;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSets;
import com.b2international.snowowl.snomed.core.lang.LanguageSetting;
import com.b2international.snowowl.snomed.datastore.ILanguageConfigurationProvider;
import com.b2international.snowowl.snomed.datastore.ISnomedImportPostProcessor;
import com.b2international.snowowl.snomed.datastore.SnomedConceptLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedRefSetIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.importer.net4j.ImportConfiguration;
import com.b2international.snowowl.snomed.importer.net4j.SnomedImportResult;
import com.b2international.snowowl.snomed.importer.net4j.SnomedValidationDefect;
import com.b2international.snowowl.snomed.importer.release.ReleaseFileSet;
import com.b2international.snowowl.snomed.importer.release.ReleaseFileSetSelectors;
import com.b2international.snowowl.snomed.importer.rf2.SnomedCompositeImportUnit;
import com.b2international.snowowl.snomed.importer.rf2.SnomedCompositeImporter;
import com.b2international.snowowl.snomed.importer.rf2.model.ComponentImportType;
import com.b2international.snowowl.snomed.importer.rf2.model.ComponentImportUnit;
import com.b2international.snowowl.snomed.importer.rf2.model.SnomedImportContext;
import com.b2international.snowowl.snomed.importer.rf2.refset.AbstractSnomedRefSetImporter;
import com.b2international.snowowl.snomed.importer.rf2.refset.SnomedRefSetImporterFactory;
import com.b2international.snowowl.snomed.importer.rf2.terminology.SnomedConceptImporter;
import com.b2international.snowowl.snomed.importer.rf2.terminology.SnomedDescriptionImporter;
import com.b2international.snowowl.snomed.importer.rf2.terminology.SnomedRelationshipImporter;
import com.b2international.snowowl.snomed.importer.rf2.validation.SnomedValidationContext;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.primitives.Longs;

/**
 * Provides utility methods for setting up and running importers.
 *
 */
public final class ImportUtil {

	private static final org.slf4j.Logger IMPORT_LOGGER = org.slf4j.LoggerFactory.getLogger(ImportUtil.class);
	private static final String SNOMED_IMPORT_POST_PROCESSOR_EXTENSION = "com.b2international.snowowl.snomed.datastore.snomedImportPostProcessor";

	public SnomedImportResult doImport(
			final String requestingUserId,
			final ImportConfiguration configuration,
			final IProgressMonitor monitor) throws ImportException {
		
		try (SnomedImportContext context = new SnomedImportContext()) {
			return doImportInternal(context, requestingUserId, configuration, monitor);
		} catch (Exception e) {
			throw new ImportException(e);
		}
	}

	public SnomedImportResult doImport(
			final SnomedRelease snomedRelease,
			final ContentSubType contentSubType,
			final IBranchPath branchPath,
			final File releaseArchive,
			final boolean shouldCreateVersions) throws Exception {
		
		return doImport(snomedRelease, branchPath, contentSubType, releaseArchive, shouldCreateVersions, SYSTEM_USER_NAME, new NullProgressMonitor());
	}
	
	public SnomedImportResult doImport(
			final SnomedRelease snomedRelease,
			final String userId,
			final ContentSubType contentSubType,
			final String branchPathName,
			final File releaseArchive,
			final boolean createVersions,
			final IProgressMonitor monitor) throws ImportException {
		
		return doImport(snomedRelease, BranchPathUtils.createPath(branchPathName), contentSubType, releaseArchive, createVersions, userId, monitor);
	}

	private SnomedImportResult doImport(
			final SnomedRelease snomedRelease,
			final IBranchPath branchPath,
			final ContentSubType contentSubType,
			final File releaseArchive,
			final boolean shouldCreateVersions,
			final String userId,
			final IProgressMonitor monitor) {
		
		checkNotNull(branchPath, "branchPath");
		checkNotNull(contentSubType, "contentSubType");
		checkNotNull(releaseArchive, "releaseArchive");
		checkArgument(releaseArchive.canRead(), "Cannot read SNOMED CT RF2 release archive content.");
		checkArgument(BranchPathUtils.exists("snomedStore", branchPath.getPath()));
		
		final ImportConfiguration config = new ImportConfiguration();
		config.setSnomedRelease(snomedRelease);
		config.setVersion(contentSubType);
		config.setBranchPath(branchPath.getPath());
		config.setCreateVersions(shouldCreateVersions);
		config.setArchiveFile(releaseArchive);

		final List<String> zipFiles = listZipFiles(releaseArchive);
		final ReleaseFileSet archiveFileSet = ReleaseFileSetSelectors.SELECTORS.getFirstApplicable(zipFiles, contentSubType);
		
		if (archiveFileSet == null) {
			throw new ImportException("Archive file is an unrecognized SNOMED CT RF2 release archive.");
		}
		
		config.setReleaseFileSet(archiveFileSet);
		
		for (final URL refSetUrl : collectUrlFromRelease(config)) {
			config.addRefSetSource(refSetUrl);
		}
		
		String languageRefsetId = ApplicationContext.getServiceForClass(ILanguageConfigurationProvider.class).getLanguageConfiguration().getLanguageRefSetId();
		
		if (!new SnomedRefSetLookupService().exists(branchPath, languageRefsetId)) {
			final SnomedRefSetNameCollector provider = new SnomedRefSetNameCollector(config, new ConsoleProgressMonitor(), "Searching for language reference sets");
	
			try {
				for (String relativeLanguageRefSetPath : archiveFileSet.getAllFileName(zipFiles, LANGUAGE_REFERENCE_SET, contentSubType)) {
					provider.parse(config.toURL(new File(relativeLanguageRefSetPath)));
				}
			} catch (final IOException e) {
				throw new ImportException(e);
			}
	
			if (!provider.getAvailableLabels().containsKey(languageRefsetId)) {
				throw new ImportException("No language reference set with identifier '" + languageRefsetId + "' could be found in release archive.");
			}
		}

		config.setSourceKind(FILES);
		
		final File tempDir = Files.createTempDir();
		tempDir.deleteOnExit();
		// read and copy entries to temporary files
		try (final ZipFile archive = new ZipFile(releaseArchive)) {
			config.setConceptsFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, CONCEPT, contentSubType)));
			config.setDescriptionsFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, DESCRIPTION, contentSubType)));
			config.setRelationshipsFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, RELATIONSHIP, contentSubType)));
			config.setLanguageRefSetFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, LANGUAGE_REFERENCE_SET, contentSubType)));
			
			// These paths might turn out to be empty
			config.setStatedRelationshipsFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, STATED_RELATIONSHIP, contentSubType)));
			config.setTextDefinitionFile(createTemporaryFile(tempDir, archive, archiveFileSet.getFileName(zipFiles, TEXT_DEFINITION, contentSubType)));
		} catch (IOException e) {
			throw new ImportException(e);
		}

		return doImport(userId, config, monitor);
	}
	
	private File createTemporaryFile(final File tmpDir, final ZipFile archive, final String entryPath) throws IOException {
		if (!Strings.isNullOrEmpty(entryPath)) {
			final File file = new File(tmpDir, String.format("%s.%s", Files.getNameWithoutExtension(entryPath), Files.getFileExtension(entryPath)));
			Files.copy(new InputSupplier<InputStream>() {
				@Override
				public InputStream getInput() throws IOException {
					return archive.getInputStream(archive.getEntry(entryPath));
				}
			}, file);
			return file;
		}
		return new File("");
	}

	private SnomedImportResult doImportInternal(final SnomedImportContext context, final String requestingUserId, final ImportConfiguration configuration, final IProgressMonitor monitor) {
		final SubMonitor subMonitor = SubMonitor.convert(monitor, "Importing release files...", 17);
		final SnomedImportResult result = new SnomedImportResult();
		final IBranchPath branchPath = BranchPathUtils.createPath(configuration.getBranchPath());
		LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import started from RF2 release format.");
		
		if (!isContentValid(result, requestingUserId, configuration, branchPath, subMonitor)) {
			LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import failed due to invalid RF2 release file(s).");
			return result;
		}
		
		final Set<URL> patchedRefSetURLs = Sets.newHashSet(configuration.getRefSetUrls());
		final Set<String> patchedExcludedRefSetIDs = Sets.newHashSet(configuration.getExcludedRefSetIds());
		final List<Importer> importers = Lists.newArrayList();

		final File stagingDirectoryRoot = new File(System.getProperty("java.io.tmpdir"));

		context.setVersionCreationEnabled(configuration.isCreateVersions());
		context.setLogger(IMPORT_LOGGER);
		context.setStagingDirectory(stagingDirectoryRoot);
		context.setContentSubType(configuration.getVersion());
		context.setIgnoredRefSetIds(patchedExcludedRefSetIDs);
		context.setSnomedReleaseShortName(configuration.getSnomedRelease().getShortName());
		context.setSnomedReleaseOID(configuration.getSnomedRelease().getCodeSystemOID());

		try {

			if (ImportConfiguration.isValidReleaseFile(configuration.getConceptsFile())) {
				final URL url = configuration.toURL(configuration.getConceptsFile());
				importers.add(new SnomedConceptImporter(context, url.openStream(), configuration.getMappedName(url.getPath())));
			}

			if (ImportConfiguration.isValidReleaseFile(configuration.getDescriptionsFile())) {
				final URL url = configuration.toURL(configuration.getDescriptionsFile());
				importers.add(new SnomedDescriptionImporter(context, url.openStream(), configuration.getMappedName(url.getPath()), ComponentImportType.DESCRIPTION));
			}
			
			if (ImportConfiguration.isValidReleaseFile(configuration.getTextDefinitionFile())) {
				final URL url = configuration.toURL(configuration.getTextDefinitionFile());
				importers.add(new SnomedDescriptionImporter(context, url.openStream(), configuration.getMappedName(url.getPath()), ComponentImportType.TEXT_DEFINITION));
			}

			if (ImportConfiguration.isValidReleaseFile(configuration.getRelationshipsFile())) {
				final URL url = configuration.toURL(configuration.getRelationshipsFile());
				importers.add(new SnomedRelationshipImporter(context, url.openStream(), configuration.getMappedName(url.getPath()), ComponentImportType.RELATIONSHIP));
			}

			if (ImportConfiguration.isValidReleaseFile(configuration.getStatedRelationshipsFile())) {
				final URL url = configuration.toURL(configuration.getStatedRelationshipsFile());
				importers.add(new SnomedRelationshipImporter(context, url.openStream(), configuration.getMappedName(url.getPath()), ComponentImportType.STATED_RELATIONSHIP));
			}

		} catch (final IOException e) {
			final String reason = null != e.getMessage() ? " Reason: '" + e.getMessage() + "'" : "";
			LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import failed due to invalid RF2 release file URL." + reason);
			throw new ImportException("Invalid release file URL(s).", e);
		}

		for (final URL url : patchedRefSetURLs) {

			try {

				final AbstractSnomedRefSetImporter<?, ?> createRefSetImporter = SnomedRefSetImporterFactory.createRefSetImporter(url, context, configuration.getMappedName(url.getPath()));

				if (createRefSetImporter == null) {
					final String message = MessageFormat.format("Skipping unsupported reference set with URL ''{0}''.", url);
					LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, message);
					IMPORT_LOGGER.info(message);
				} else {
					importers.add(createRefSetImporter);
				}

			} catch (final IOException e) {
				final String reason = null != e.getMessage() ? " Reason: '" + e.getMessage() + "'" : "";
				LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import failed due to I/O error while creating reference set importer." + reason);
				throw new ImportException("I/O error occurred while creating reference set importer.", e);
			}	
		}

		final boolean terminologyExistsBeforeImport = ApplicationContext.getInstance().getService(SnomedTerminologyBrowser.class).isTerminologyAvailable(BranchPathUtils.createMainPath());
		final boolean onlyRefSetImportersRegistered = Iterables.all(importers, Predicates.instanceOf(AbstractSnomedRefSetImporter.class));

		/*
		 * Commit notifications for changes made by the import should only be sent if the terminology already exists,
		 * and only changes for reference sets are coming in from the import files. 
		 */
		context.setCommitNotificationEnabled(terminologyExistsBeforeImport && onlyRefSetImportersRegistered);
		context.setUserId(requestingUserId);

		final ICDOConnectionManager connectionManager = ApplicationContext.getInstance().getService(ICDOConnectionManager.class);
		final CDOBranch branch = connectionManager.get(SnomedPackage.eINSTANCE).getBranch(branchPath);

		if (null == branch) {
			throw new ImportException("Branch does not exist. [" + branchPath + "]");
		}

		final SnomedEditingContext editingContext = new SnomedEditingContext(branchPath);
		context.setEditingContext(editingContext);
		context.setAggregatorSupplier(new EffectiveTimeBaseTransactionAggregatorSupplier(editingContext.getTransaction()));

		final IOperationLockTarget lockTarget = new SingleRepositoryAndBranchLockTarget(editingContext.getTransaction().getSession().getRepositoryInfo().getUUID(), branchPath);
		final DatastoreLockContext lockContext = new DatastoreLockContext(requestingUserId, DatastoreLockContextDescriptions.IMPORT);
		final SnomedImportResult[] resultHolder = new SnomedImportResult[1];
		final IDatastoreOperationLockManager lockManager = ApplicationContext.getInstance().getServiceChecked(IDatastoreOperationLockManager.class);
		
		try {
			OperationLockRunner.with(lockManager).run(new Runnable() { 
				@Override 
				public void run() {
					createSnomedRelease(configuration.getSnomedRelease(), context, configuration.getBranchPath());
					resultHolder[0] = doImportLocked(requestingUserId, configuration, result, branchPath, context, subMonitor, importers, editingContext, branch);
				}
			}, lockContext, IOperationLockManager.NO_TIMEOUT, lockTarget);
		} catch (final OperationLockException | InterruptedException e) {
			throw new ImportException(e);
		} catch (final InvocationTargetException e) {
			throw new ImportException(e.getCause());
		}
		
		return resultHolder[0];
	}
	
	/*
	 * Code systems must be committed to MAIN, therefore the proper editing context must be used accordingly 
	 */
	private void createSnomedRelease(final SnomedRelease snomedRelease, final SnomedImportContext context, final String branchPath) {
		try {
			// import works on MAIN -> get editingContext from import context
			if (branchPath.equals(IBranchPath.MAIN_BRANCH)) {
				if (!context.getEditingContext().isSnomedReleaseExists(snomedRelease.getShortName(), snomedRelease.getCodeSystemOID())) {
					context.getEditingContext().add(snomedRelease);
				}
				
				if (context.getEditingContext().isDirty()) {
					new CDOServerCommitBuilder(context.getUserId(), String.format("Created SNOMED CT code system '%s' (OID: %s)", snomedRelease.getShortName(), snomedRelease.getCodeSystemOID()), context.getEditingContext().getTransaction())
						.sendCommitNotification(false)
						.parentContextDescription(DatastoreLockContextDescriptions.IMPORT)
						.commit();
				}
			// import works on a branch -> open a separate editingContext for MAIN
			} else {
				try (SnomedEditingContext snomedEditingContext = new SnomedEditingContext(BranchPathUtils.createMainPath())) {
					
					if (!snomedEditingContext.isSnomedReleaseExists(snomedRelease.getShortName(), snomedRelease.getCodeSystemOID())) {
						snomedEditingContext.add(snomedRelease);
					} else if (snomedRelease.getReleaseType() == SnomedReleaseType.EXTENSION) {
						SnomedRelease existingSnomedRelease = snomedEditingContext.getSnomedRelease(snomedRelease.getShortName(), snomedRelease.getCodeSystemOID());
						Date effectiveTimeOfExistingRelease = getBaseEffectiveTimeOf(existingSnomedRelease);
						Date effectiveTimeOfRequestedSnomedRelease = getBaseEffectiveTimeOf(snomedRelease);
						if (effectiveTimeOfExistingRelease != null && effectiveTimeOfRequestedSnomedRelease != null) {
							if (effectiveTimeOfRequestedSnomedRelease.after(effectiveTimeOfExistingRelease)) {
								// XXX index change processing does not follow this change!
								existingSnomedRelease.setBranchPath(snomedRelease.getBranchPath());
							}
						}
					}
					
					if (snomedEditingContext.isDirty()) {
						CDOServerUtils.commit(snomedEditingContext, context.getUserId(), String.format("Created SNOMED CT code system '%s' (OID: %s)", snomedRelease.getShortName(), snomedRelease.getCodeSystemOID()), null);
					}
				}
			}
		} catch (CommitException e) {
			throw new ImportException("Unable to commit SNOMED CT code system", e);
		}
	}

	private Date getBaseEffectiveTimeOf(SnomedRelease snomedRelease) {
		List<String> pathSegments = Splitter.on(Branch.SEPARATOR).splitToList(snomedRelease.getBranchPath());
		int indexOfExtensionIdentifier = pathSegments.lastIndexOf(snomedRelease.getShortName());
		if (indexOfExtensionIdentifier > 0) {
			String baseEffectiveTime = pathSegments.get(indexOfExtensionIdentifier -1);
			try {
				return EffectiveTimes.parse(baseEffectiveTime, DateFormats.DEFAULT);
			} catch (SnowowlRuntimeException | NullPointerException e) {
				throw new ImportException(String.format("Unable to parse base effective time of SNOMED CT code system (%s)", snomedRelease.getBranchPath()), e);
			}
		}
		return null;
	}

	private SnomedImportResult doImportLocked(final String requestingUserId, final ImportConfiguration configuration,
			final SnomedImportResult result, final IBranchPath branchPath, final SnomedImportContext context,
			final SubMonitor subMonitor, final List<Importer> importers, final SnomedEditingContext editingContext,
			final CDOBranch branch) {

		try { 

			final long lastCommitTime = CDOServerUtils.getLastCommitTime(branch);
			context.setCommitTime(lastCommitTime);

			final SnomedCompositeImporter importer = new SnomedCompositeImporter(IMPORT_LOGGER, context, importers, ComponentImportUnit.ORDERING);

			importer.preImport(subMonitor.newChild(1, SubMonitor.SUPPRESS_NONE));
			final SnomedCompositeImportUnit snomedCompositeImportUnit = importer.getCompositeUnit(subMonitor.newChild(1, SubMonitor.SUPPRESS_NONE));
			snomedCompositeImportUnit.doImport(subMonitor.newChild(10, SubMonitor.SUPPRESS_NONE));
			importer.postImport(subMonitor.newChild(1, SubMonitor.SUPPRESS_NONE));

			// If there were no changes, no need to recreate semantic indexes
			if (context.getVisitedConcepts().size() == 0 && context.getVisitedRefSets().size() == 0) {
				return result;
			}

			// release specific post processing
			postProcess(context);
			
			result.getVisitedConcepts().addAll(getVisitedConcepts(context.getVisitedConcepts(), branchPath));
			result.getVisitedRefSets().addAll(getVisitedRefSets(context.getVisitedRefSets(), branchPath));

			return result;
		} finally {
			subMonitor.done();
			if (!result.getVisitedConcepts().isEmpty() || !result.getVisitedRefSets().isEmpty()) {
				LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import successfully finished.");
			} else {
				LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, "SNOMED CT import finished. No changes could be found.");
			}
		}
	}

	private Collection<SnomedConceptIndexEntry> getVisitedConcepts(final LongSet visitedConceptIds, final IBranchPath branchPath) {
		if (visitedConceptIds.size() == 0) {
			return Collections.emptyList();
		}

		final SnomedConcepts concepts = SnomedRequests.prepareSearchConcept()
				.all()
				.setLocales(getLocales())
				.setExpand("pt()")
				.setComponentIds(getAsStringList(visitedConceptIds))
				.build(branchPath.getPath())
				.executeSync(getEventBus());
		
		final SnomedConceptLookupService lookupService = new SnomedConceptLookupService();
		return FluentIterable.from(concepts).transform(new Function<ISnomedConcept, SnomedConceptIndexEntry>() {
			@Override
			public SnomedConceptIndexEntry apply(ISnomedConcept concept) {
				final String label = concept.getPt() == null ? concept.getId() : concept.getPt().getTerm();
				final long storageKey = lookupService.getStorageKey(branchPath, concept.getId());
				return SnomedConceptIndexEntry.builder(concept)
						.label(label)
						.storageKey(storageKey)
						.build();
			}
		}).toList();
	}
	
	private Collection<SnomedRefSetIndexEntry> getVisitedRefSets(final LongSet visitedRefSetIds, final IBranchPath branchPath) {
		if (visitedRefSetIds.size() == 0) {
			return Collections.emptyList();
		}

		final Collection<SnomedConceptIndexEntry> identifierConcepts = getVisitedConcepts(visitedRefSetIds, branchPath);
		
		final SnomedReferenceSets refSets = SnomedRequests.prepareSearchRefSet()
				.all()
				.setLocales(getLocales())
				.setComponentIds(getAsStringList(visitedRefSetIds))
				.build(branchPath.getPath())
				.executeSync(getEventBus());
		
		final SnomedRefSetLookupService lookupService = new SnomedRefSetLookupService();
		return FluentIterable.from(refSets.getItems()).transform(new Function<SnomedReferenceSet, SnomedRefSetIndexEntry>() {
			@Override
			public SnomedRefSetIndexEntry apply(final SnomedReferenceSet refSet) {
				final Optional<SnomedConceptIndexEntry> identifierConcept = getIdentifierConcept(identifierConcepts, refSet);
				final String preferredTerm = identifierConcept.isPresent() ? identifierConcept.get().getLabel() : refSet.getId();
				final long storageKey = lookupService.getStorageKey(branchPath, refSet.getId());
				return SnomedRefSetIndexEntry.builder(refSet)
						.label(preferredTerm)
						.storageKey(storageKey)
						.build();
			}
		}).toList();
	}
	
	private ImmutableList<String> getAsStringList(final LongSet longIds) {
		final long[] longIdArray = longIds.toArray();
		Arrays.sort(longIdArray);
		
		return FluentIterable.from(Longs.asList(longIdArray)).transform(new Function<Long, String>() {
			@Override
			public String apply(Long input) {
				return String.valueOf(input);
			}
		}).toList();
	}

	private List<ExtendedLocale> getLocales() {
		return ApplicationContext.getInstance().getService(LanguageSetting.class).getLanguagePreference();
	}

	private IEventBus getEventBus() {
		return ApplicationContext.getInstance().getService(IEventBus.class);
	}

	private Optional<SnomedConceptIndexEntry> getIdentifierConcept(final Collection<SnomedConceptIndexEntry> identifierConcepts,
			final SnomedReferenceSet refSet) {
		return FluentIterable.from(identifierConcepts).firstMatch(new Predicate<SnomedConceptIndexEntry>() {
			@Override
			public boolean apply(SnomedConceptIndexEntry concept) {
				return concept.getId().equals(refSet.getId());
			}
		});
	}

	// result is populated with validation errors if the return value is false
	private boolean isContentValid(final SnomedImportResult result, final String requestingUserId, final ImportConfiguration configuration, final IBranchPath branchPath, final SubMonitor subMonitor) {
		final SnomedValidationContext validator = new SnomedValidationContext(requestingUserId, configuration, IMPORT_LOGGER);
		result.getValidationDefects().addAll(validator.validate(subMonitor.newChild(1)));
		
		if (!isEmpty(result.getValidationDefects())) {
			// If only header differences exist, continue the import
			final FluentIterable<String> defects = FluentIterable.from(result.getValidationDefects()).transformAndConcat(new Function<SnomedValidationDefect, Iterable<? extends String>>() {
				@Override
				public Iterable<? extends String> apply(SnomedValidationDefect input) {
					return input.getDefects();
				}
			});
			final String message = String.format("Validation encountered %s issue(s).", defects.size());
			LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, message);
			for (String defect : defects) {
				LogUtils.logImportActivity(IMPORT_LOGGER, requestingUserId, branchPath, defect);
			}
			
			return !Iterables.tryFind(result.getValidationDefects(), new Predicate<SnomedValidationDefect>() {
				@Override
				public boolean apply(SnomedValidationDefect input) {
					return input.getDefectType().isCritical();
				}
			}).isPresent();
		}
		
		return true;
	}

	private void postProcess(final SnomedImportContext context) {
		for (final ISnomedImportPostProcessor processor : Extensions.getExtensions(SNOMED_IMPORT_POST_PROCESSOR_EXTENSION, ISnomedImportPostProcessor.class)) {
			processor.postProcess(context);
		}
	}

	public static long parseLong(final String componentId) {

		try {
			return Long.parseLong(Preconditions.checkNotNull(componentId, "componentId"));
		} catch (final NumberFormatException e) {
			throw new IllegalArgumentException(MessageFormat.format("Couldn''t convert component ID to a long: ''{0}''.", componentId));
		}
	}

	public static List<String> listZipFiles(final File filePath) {

		if (!filePath.canRead()) {
			return Collections.emptyList();
		}

		final Set<String> listOfFiles = new HashSet<String>();

		try {

			final ZipFile zipFile = new ZipFile(filePath);

			final Enumeration<? extends ZipEntry> files = zipFile.entries();

			while(files.hasMoreElements()) {

				final ZipEntry nextElement = files.nextElement();

				if (!nextElement.isDirectory()) {
					final String zipPath = nextElement.toString();
					listOfFiles.add(zipPath);
				}
			}

			zipFile.close();

		} catch (final IOException e) {
			return Collections.emptyList();
		}

		return ImmutableList.copyOf(listOfFiles);
	}
}
