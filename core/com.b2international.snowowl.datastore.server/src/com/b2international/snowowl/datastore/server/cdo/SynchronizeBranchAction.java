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
package com.b2international.snowowl.datastore.server.cdo;

import static com.google.common.collect.Lists.newArrayList;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.cdo.common.branch.CDOBranch;
import org.eclipse.emf.cdo.transaction.CDOMerger.ConflictException;
import org.eclipse.emf.cdo.transaction.CDOTransaction;
import org.eclipse.emf.cdo.util.CommitException;
import org.eclipse.emf.spi.cdo.DefaultCDOMerger.Conflict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.commons.CompareUtils;
import com.b2international.snowowl.core.LogUtils;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.IBranchPathMap;
import com.b2international.snowowl.datastore.cdo.ConflictWrapper;
import com.b2international.snowowl.datastore.cdo.CustomConflictException;
import com.b2international.snowowl.datastore.cdo.ICDOConnection;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContextDescriptions;
import com.b2international.snowowl.datastore.server.CDOServerUtils;
import com.b2international.snowowl.datastore.server.index.IndexServerServiceManager;
import com.b2international.snowowl.datastore.server.internal.branch.CDOBranchMerger;

/**
 * Synchronizes changes with the task branches' parents. 
 */
public class SynchronizeBranchAction extends AbstractCDOBranchAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizeBranchAction.class);

	private static final String COMMIT_COMMENT = "Synchronized task branch with parent.";

	private final List<CDOTransaction> transactions = newArrayList();

	public SynchronizeBranchAction(final IBranchPathMap branchPathMap, final String userId) {
		super(branchPathMap, userId, DatastoreLockContextDescriptions.SYNCHRONIZE);
	}

	@Override
	protected void apply(final String repositoryId, final IBranchPath taskBranchPath) throws CustomConflictException {

		final ICDOConnection connection = getConnectionManager().getByUuid(repositoryId);
		final CDOBranch taskBranch = connection.getBranch(taskBranchPath);

		// Does the task branch exist?
		if (null == taskBranch) {
			return;
		}

		// No commits at all on task branch?
		if (Long.MIN_VALUE == CDOServerUtils.getLastCommitTime(taskBranch)) {
			return;
		}
		
		// Can the task branch have a parent?
		if (BranchPathUtils.isMain(taskBranchPath)) {
			return;
		}

		final IBranchPath parentBranchPath = taskBranchPath.getParent();
		final CDOBranch parentBranch = taskBranch.getBase().getBranch();

		// Does the parent branch exist?
		if (null == parentBranch) {
			return;
		}
		
		LOGGER.info(MessageFormat.format("Applying changes from ''{0}'' to ''{1}'' in ''{2}''...", 
				parentBranchPath.getPath(),
				taskBranchPath.getPath(), 
				connection.getRepositoryName()));

		final CDOBranchMerger branchMerger = new CDOBranchMerger(connection.getUuid());
		final Set<ConflictWrapper> conflictWrappers = new HashSet<ConflictWrapper>(); 

		try {

			// Create an empty, new branch on top with same name, do the same for the index
			final CDOBranch newTaskBranch = parentBranch.createBranch(taskBranchPath.lastSegment());
			IndexServerServiceManager.INSTANCE.getByUuid(repositoryId).reopen(taskBranchPath, newTaskBranch.getBase().getTimeStamp());

			final CDOTransaction transaction = connection.createTransaction(newTaskBranch);
			transaction.merge(taskBranch.getHead(), branchMerger);

			LOGGER.info(MessageFormat.format("Unlinking components in ''{0}''...", connection.getRepositoryName()));
			branchMerger.unlinkObjects(transaction);

			if (transaction.isDirty()) {
				transactions.add(transaction);
			}

		} catch (final ConflictException e) {

			for (final Conflict cdoConflict : branchMerger.getConflicts().values()) {	
				CDOConflictProcessorBroker.INSTANCE.processConflict(cdoConflict, conflictWrappers);
			}			
		}

		if (!conflictWrappers.isEmpty()) {
			throw new CustomConflictException("Conflicts detected while synchronizing task", conflictWrappers);
		}
	}

	@Override
	protected void postRun() throws CommitException {

		if (CompareUtils.isEmpty(transactions)) {
			return;
		}

		try {
			CDOServerUtils.commit(transactions, getUserId(), COMMIT_COMMENT, true, null);
			LogUtils.logUserEvent(LOGGER, getUserId(), "Synchronizing changes finished successfully.");
		} finally {
			for (final CDOTransaction transaction : transactions) {
				transaction.close();
			}
		}
	}
}
