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
package com.b2international.snowowl.datastore.store;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * @since 4.1
 */
public class IndexStore<T> extends SingleDirectoryIndexServerService implements Store<T> {

	private static final String ID_FIELD = "id";
	private static final String TYPE_FIELD = "type";
	private static final String SOURCE_FIELD = "source";
	private ObjectMapper objectMapper;
	private Class<T> clazz;

	/**
	 * Creates a new Index based {@link Store} implementation working on the given directory.
	 * 
	 * @param directory
	 *            - the directory to use for this index based store.
	 * @param type
	 *            - the value's type
	 */
	public IndexStore(File directory, Class<T> type) {
		super(directory);
		this.clazz = checkNotNull(type, "type");
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void put(String key, T value) {
		try {
			if (get(key) != null) {
				throw new StoreException("Duplicates on key '%s' are not allowed in store '%s'.", key, getDirectory());
			}
			updateDoc(key, value);
			commit();
		} catch (IOException e) {
			throw new StoreException("Failed to store value '%s' in key '%s'", value, key, e);
		}
	}

	@Override
	public T get(String key) {
		try {
			final Query query = matchKeyQuery(key);
			return Iterables.getOnlyElement(search(query), null);
		} catch (IOException e) {
			throw new StoreException(e.getMessage(), e);
		}
	}

	@Override
	public T remove(String key) {
		try {
			final T t = get(key);
			deleteDoc(key);
			commit();
			return t;
		} catch (IOException e) {
			throw new StoreException(e.getMessage(), e);
		}
	}

	@Override
	public boolean replace(String key, T oldValue, T newValue) {
		checkNotNull(oldValue, "oldValue");
		checkNotNull(newValue, "newValue");
		if (oldValue.equals(newValue) || !oldValue.equals(get(key))) {
			return false;
		} else {
			try {
				doUpdate(key, newValue);
				return true;
			} catch (IOException e) {
				throw new StoreException("Failed to replace key '%s' with value '%s' in store '%s'", key, newValue, getDirectory(), e);
			}
		}
	}

	private void doUpdate(String key, T newValue) throws IOException {
		deleteDoc(key);
		updateDoc(key, newValue);
		commit();
	}

	@Override
	public Collection<T> values() {
		try {
			return search(matchAllQuery(clazz));
		} catch (IOException e) {
			throw new StoreException("Failed to retrieve values from store '%s'.", getDirectory(), e);
		}
	}

	@Override
	public void clear() {
		try {
			writer.deleteAll();
			commit();
		} catch (IOException e) {
			throw new StoreException("Failed to clear store '%s'", getDirectory(), e);
		}
	}
	
	private void updateDoc(String key, T value) throws IOException {
		final Document doc = new Document();
		doc.add(new StringField(ID_FIELD, key, Field.Store.NO));
		doc.add(new StringField(TYPE_FIELD, clazz.getName(), Field.Store.NO));
		doc.add(new StringField(SOURCE_FIELD, serialize(value), Field.Store.YES));
		writer.updateDocument(new Term(ID_FIELD, key), doc);
	}
	
	private void deleteDoc(String key) throws IOException {
		writer.deleteDocuments(matchKeyQuery(key));
	}

	private String serialize(T value) throws IOException {
		return objectMapper.writeValueAsString(value);
	}

	private T deserialize(String source) throws IOException {
		return objectMapper.readValue(source, clazz);
	}
	
	private List<T> search(final Query query) throws IOException {
		return search(query, Integer.MAX_VALUE);
	}

	private List<T> search(final Query query, final int limit) throws IOException {
		return search(query, 0, limit);
	}
	
	private List<T> search(final Query query, final int offset, final int limit) throws IOException {
		IndexSearcher searcher = null;
		try {
			searcher = manager.acquire();

			final TopDocs docs = searcher.search(query, null, offset + limit, Sort.INDEXORDER, false, false);
			final ScoreDoc[] scoreDocs = docs.scoreDocs;
			final ImmutableList.Builder<T> resultBuilder = ImmutableList.builder();

			for (int i = offset; i < offset + limit && i < scoreDocs.length; i++) {
				final Document sourceDocument = searcher.doc(scoreDocs[i].doc, ImmutableSet.of(SOURCE_FIELD));
				final String source = sourceDocument.get(SOURCE_FIELD);
				final T deserializedSource = deserialize(source);
				resultBuilder.add(deserializedSource);
			}

			return resultBuilder.build();
		} finally {
			if (null != searcher) {
				manager.release(searcher);
			}
		}
	}
	
	private static BooleanQuery matchAllQuery(Class<?> type) {
		final BooleanQuery query = new BooleanQuery();
		query.add(new TermQuery(new Term(TYPE_FIELD, type.getName())), Occur.MUST);
		return query;
	}
	
	private static BooleanQuery matchKeyQuery(String key) {
		final BooleanQuery query = new BooleanQuery();
		query.add(new TermQuery(new Term(ID_FIELD, key)), Occur.MUST);
		return query;
	}

}
