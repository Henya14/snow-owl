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
package com.b2international.snowowl.snomed.importer.rf2.command;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.framework.console.CommandInterpreter;

import com.b2international.commons.ConsoleProgressMonitor;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.importer.ImportException;
import com.b2international.snowowl.server.console.CommandLineAuthenticator;
import com.b2international.snowowl.snomed.SnomedRelease;
import com.b2international.snowowl.snomed.SnomedReleaseType;
import com.b2international.snowowl.snomed.common.ContentSubType;
import com.b2international.snowowl.snomed.core.store.SnomedReleases;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.importer.net4j.SnomedImportResult;
import com.b2international.snowowl.snomed.importer.net4j.SnomedValidationDefect;
import com.b2international.snowowl.snomed.importer.rf2.util.ImportUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;

/**
 * Import release command for the OSGi console
 */
public class ImportZipCommand extends AbstractRf2ImporterCommand {

	public ImportZipCommand() {
		super(
				"rf2_release", 
				"-t <type> -b <branch> -v <path to rf2 archive> <path to release descriptor file>",
				"Imports SNOMED CT RF2 releases from a release archive",
				new String[] { 
					"-t <type>\t\tThe import type (FULL, SNAPSHOT or DELTA).",
					"-b <branch>\t\tThe existing branch to import the content onto. In case of extension import, an effective time from the base SNOMED CT release (e.g. 2016-01-31). If omitted 'MAIN' will be used.",
					"-v\t\t\tCreates versions for each effective time found in the release archive. If omitted no versions will be created.",
					"<path>\t\tSpecifies the release archive to import (must be a .zip file with a supported internal structure, such as the release archive of the International Release).",
					"<path to release descriptor file>\tThe path to the release descriptor file.",
					"E.g:",
					"\tImporting the international release on MAIN (using US language reference set):",
					"\tsctimport rf2_release -t full -v C:/SnomedCT_RF2Release_INT_20160131.zip C:/snomed_ct_international.json",
					"\tImport and extension on a branch (using US language reference set):",
					"\tsctimport rf2_release -t full -b 2016-01-31 -v C:/SnomedCT_Release_B2i_20160201.zip C:/snomed_ct_b2i.json"
				});
	}

	@Override
	public void execute(final CommandInterpreter interpreter) {
		
		List<String> parameters = getParameters(interpreter);
		
		if (parameters.isEmpty()) {
			printDetailedHelp(interpreter);
			return;
		}
		
		if (parameters.size() < 4) {
			interpreter.println("Invalid number of arguments");
			printDetailedHelp(interpreter);
			return;
		}
		
		ContentSubType contentSubType = null;
		String branchPath = IBranchPath.MAIN_BRANCH;
		boolean createVersions = false;
		String archiveFilePath = null;
		String metadataFilePath = null;
		
		// release type
		
		if (!"-t".equals(parameters.get(0))) {
			interpreter.println("Import type must be defined.");
			printDetailedHelp(interpreter);
			return;
		} else {
			String importType = parameters.get(1);
			try {
				contentSubType = ContentSubType.getByNameIgnoreCase(importType);
			} catch (IllegalArgumentException e) {
				interpreter.println("Invalid or unknown import type '" + importType + ".");
				printDetailedHelp(interpreter);
				return;
			}
		}

		// create version flag
		
		if (parameters.contains("-v")) {
			createVersions = true;
		}

		// archive path
		
		if (parameters.contains("-b") && parameters.contains("-v")) {
			if (parameters.size() > 5) {
				archiveFilePath = parameters.get(5);
				if (Strings.isNullOrEmpty(archiveFilePath)) {
					interpreter.println("Import archive path is missing.");
					printDetailedHelp(interpreter);
					return;
				}
			} else {
				interpreter.println("Import archive path is missing.");
				printDetailedHelp(interpreter);
				return;
			}
		} else if (parameters.contains("-b") && !parameters.contains("-v")) {
			if (parameters.size() > 4) {
				archiveFilePath = parameters.get(4);
				if (Strings.isNullOrEmpty(archiveFilePath)) {
					interpreter.println("Import archive path is missing.");
					printDetailedHelp(interpreter);
					return;
				}
			} else {
				interpreter.println("Import archive path is missing.");
				printDetailedHelp(interpreter);
				return;
			}
		} else if (!parameters.contains("-b") && parameters.contains("-v")) {
			archiveFilePath = parameters.get(3);
			if (Strings.isNullOrEmpty(archiveFilePath)) {
				interpreter.println("Import archive path is missing.");
				printDetailedHelp(interpreter);
				return;
			}
		} else if (!parameters.contains("-b") && !parameters.contains("-v")) {
			archiveFilePath = parameters.get(2);
			if (Strings.isNullOrEmpty(archiveFilePath)) {
				interpreter.println("Import archive path is missing.");
				printDetailedHelp(interpreter);
				return;
			}
		}
		
		final File archiveFile = new File(archiveFilePath);
		
		if (!archiveFile.isFile()) {
			interpreter.println("Invalid import archive path.");
			printDetailedHelp(interpreter);
			return;
		}
		
		// metadata file path
		
		if (!archiveFilePath.equals(parameters.get(parameters.size() - 1))) {
			metadataFilePath = parameters.get(parameters.size() - 1);
			if (Strings.isNullOrEmpty(metadataFilePath)) {
				interpreter.println("SNOMED CT RF2 release descriptor file must be specified");
				printDetailedHelp(interpreter);
				return;
			}
		} else {
			interpreter.println("SNOMED CT RF2 release descriptor file must be specified");
			printDetailedHelp(interpreter);
			return;
		}
		
		SnomedRelease snomedRelease = null;
			
		File metadataFile = new File(metadataFilePath);
		
		if (!metadataFile.isFile() || !metadataFilePath.endsWith(".json")) {
			interpreter.println("Invalid metadata file path.");
			printDetailedHelp(interpreter);
			return;
		} else {
			try {
				ObjectMapper mapper = new ObjectMapper();
				@SuppressWarnings("unchecked")
				Map<String, String> metadataMap = mapper.readValue(metadataFile, Map.class);
				snomedRelease = SnomedReleases.newSnomedRelease(metadataMap).build();
			} catch (IOException e) {
				interpreter.println("Unable to parse metadata file: " + e.getMessage());
				printDetailedHelp(interpreter);
				return;
			}
		}
		
		// branchPath
		
		if (parameters.contains("-b")) {
			branchPath = parameters.get(3);
			if (snomedRelease.getReleaseType() == SnomedReleaseType.INTERNATIONAL) {
				if (!BranchPathUtils.exists(SnomedDatastoreActivator.REPOSITORY_UUID, branchPath)) {
					interpreter.println("Invalid branch path '" + branchPath + "'.");
					printDetailedHelp(interpreter);
					return;
				}
			} else if (snomedRelease.getReleaseType() == SnomedReleaseType.EXTENSION) {
				if (!branchPath.equalsIgnoreCase(IBranchPath.MAIN_BRANCH)) {

					IBranchPath parentBranchPath = BranchPathUtils.createPath(BranchPathUtils.createMainPath(), branchPath);
					IBranchPath extensionBranchPath = BranchPathUtils.createPath(parentBranchPath, snomedRelease.getShortName());

					if (!BranchPathUtils.exists(SnomedDatastoreActivator.REPOSITORY_UUID, extensionBranchPath.getPath())) {
						IEventBus eventBus = ApplicationContext.getServiceForClass(IEventBus.class);
						SnomedRequests.branching().prepareCreate()
							.setParent(parentBranchPath.getPath())
							.setName(snomedRelease.getShortName())
							.build()
							.executeSync(eventBus);
					}
					
					branchPath = extensionBranchPath.getPath();
				}
			}
		}

		snomedRelease.setBranchPath(branchPath);
		
		try {

			final CommandLineAuthenticator authenticator = new CommandLineAuthenticator();

			if (!authenticator.authenticate(interpreter)) {
				return;
			}

			final SnomedImportResult result = new ImportUtil().doImport(snomedRelease, authenticator.getUsername(), contentSubType, branchPath,
					archiveFile, createVersions, new ConsoleProgressMonitor());

			Set<SnomedValidationDefect> validationDefects = result.getValidationDefects();
			
			boolean criticalFound = FluentIterable.from(validationDefects).anyMatch(new Predicate<SnomedValidationDefect>() {
				@Override public boolean apply(SnomedValidationDefect defect) {
					return defect.getDefectType().isCritical();
				}
			});
			
			if (criticalFound) {
				interpreter.println("SNOMED CT import has been canceled due to critical errors found in the RF2 release.");
			} else {
				interpreter.println("SNOMED CT import has successfully finished.");
			}

		} catch (final ImportException e) {
			interpreter.printStackTrace(e);
		}
	}

	private List<String> getParameters(final CommandInterpreter interpreter) {
		List<String> parameters = newArrayList();
		String param = null;
		do {
			param = interpreter.nextArgument();
			if (param != null) {
				parameters.add(param);
			}
		} while (param != null);
		return parameters;
	}

}