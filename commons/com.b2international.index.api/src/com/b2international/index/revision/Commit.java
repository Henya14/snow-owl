/*
 * Copyright 2011-2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.revision;

import static com.b2international.index.query.Expressions.exactMatch;
import static com.b2international.index.query.Expressions.matchAny;
import static com.b2international.index.query.Expressions.matchRange;
import static com.b2international.index.query.Expressions.matchTextAll;
import static com.b2international.index.query.Expressions.matchTextPhrase;

import java.util.Collection;
import java.util.Set;

import com.b2international.commons.collections.Collections3;
import com.b2international.index.Analyzers;
import com.b2international.index.Doc;
import com.b2international.index.Text;
import com.b2international.index.WithScore;
import com.b2international.index.mapping.DocumentMapping;
import com.b2international.index.query.Expression;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * @since 5.2
 */
@Doc(type="commitinfodocument")
@JsonDeserialize(builder = Commit.Builder.class)
public final class Commit implements WithScore {

	static Builder builder() {
		return new Builder();
	}

	@JsonPOJOBuilder(withPrefix = "")
	public static final class Builder {

		private String id;
		private String branch;
		private String userId;
		private String comment;
		private long timestamp;
		private Set<String> newComponents;
		private Set<String> changedComponents;
		private Set<String> deletedComponents;

		public Builder id(final String id) {
			this.id = id;
			return this;
		}

		public Builder branch(final String branch) {
			this.branch = branch;
			return this;
		}

		public Builder userId(final String userId) {
			this.userId = userId;
			return this;
		}

		public Builder comment(final String comment) {
			this.comment = comment;
			return this;
		}

		public Builder timestamp(final long timestamp) {
			this.timestamp = timestamp;
			return this;
		}
		
		public Builder newComponents(Set<String> newComponents) {
			this.newComponents = newComponents;
			return this;
		}
		
		public Builder changedComponents(Set<String> changedComponents) {
			this.changedComponents = changedComponents;
			return this;
		}
		
		public Builder deletedComponents(Set<String> deletedComponents) {
			this.deletedComponents = deletedComponents;
			return this;
		}

		public Commit build() {
			return new Commit(id, branch, userId, comment, timestamp, newComponents, changedComponents, deletedComponents);
		}

	}
	
	public static final class Expressions {
		
		private Expressions() {}
		
		public static final Expression id(String id) {
			return DocumentMapping.matchId(id);
		}
		
		public static final Expression ids(Collection<String> ids) {
			return matchAny(DocumentMapping._ID, ids);
		}
		
		public static Expression branch(final String branch) {
			return exactMatch(Fields.BRANCH, branch);
		}
		
		public static Expression userId(final String userId) {
			return exactMatch(Fields.USER_ID, userId);
		}
		
		public static Expression exactComment(final String comment) {
			return matchTextPhrase(Fields.COMMENT, comment);
		}
		
		public static Expression allCommentPrefixesPresent(final String comment) {
			return matchTextAll(Fields.COMMENT+".prefix", comment);
		}
		
		public static Expression timestamp(final long timeStamp) {
			return exactMatch(Fields.TIME_STAMP, timeStamp);
		}
		
		public static Expression timestampRange(final long from, final long to) {
			return matchRange(Fields.TIME_STAMP, from, to);
		}
		
	}
	
	public static final class Fields {
		public static final String BRANCH = "branch";
		public static final String USER_ID = "userId";
		public static final String COMMENT = "comment";
		public static final String TIME_STAMP = "timestamp";
	}

	private final String id;
	private final String branch;
	private final String userId;
	@Text(analyzer=Analyzers.TOKENIZED)
	@Text(alias="prefix", analyzer=Analyzers.PREFIX, searchAnalyzer=Analyzers.TOKENIZED)
	private final String comment;
	private final long timestamp;
	private final Set<String> newComponents;
	private final Set<String> changedComponents;
	private final Set<String> deletedComponents;	
	
	
	private float score = 0.0f;
	
	private Commit(
			final String id,
			final String branch,
			final String userId,
			final String comment,
			final long timestamp,
			Set<String> newComponents,
			Set<String> changedComponents,
			Set<String> deletedComponents) {
		this.id = id;
		this.branch = branch;
		this.userId = userId;
		this.comment = comment;
		this.timestamp = timestamp;
		this.newComponents = Collections3.toImmutableSet(newComponents);
		this.changedComponents = Collections3.toImmutableSet(changedComponents);
		this.deletedComponents = Collections3.toImmutableSet(deletedComponents);
	}

	public String getId() {
		return id;
	}
	
	@Override
	public void setScore(float score) {
		this.score = score;
	}

	@Override
	@JsonIgnore
	public float getScore() {
		return score;
	}

	public String getBranch() {
		return branch;
	}

	public String getUserId() {
		return userId;
	}

	public String getComment() {
		return comment;
	}

	public long getTimestamp() {
		return timestamp;
	}
	
	public Set<String> getNewComponents() {
		return newComponents;
	}
	
	public Set<String> getChangedComponents() {
		return changedComponents;
	}
	
	public Set<String> getDeletedComponents() {
		return deletedComponents;
	}

}