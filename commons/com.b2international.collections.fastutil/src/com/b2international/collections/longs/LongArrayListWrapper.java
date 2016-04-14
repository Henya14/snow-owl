/*
 * Copyright 2011-2016 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.collections.longs;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * @since 4.7
 */
public class LongArrayListWrapper extends LongCollectionWrapper implements LongList {

	protected LongArrayListWrapper(it.unimi.dsi.fastutil.longs.LongList delegate) {
		super(delegate);
	}
	
	@Override
	protected it.unimi.dsi.fastutil.longs.LongList delegate() {
		return (it.unimi.dsi.fastutil.longs.LongList) super.delegate();
	}

	@Override
	public void trimToSize() {
		trim(delegate());
	}

	@Override
	public LongList dup() {
		return create(this);
	}

	@Override
	public long get(int index) {
		return delegate().getLong(index);
	}

	@Override
	public LongListIterator listIterator() {
		return new LongListIteratorWrapper(delegate().listIterator());
	}

	@Override
	public LongListIterator listIterator(int startIndex) {
		return new LongListIteratorWrapper(delegate().listIterator(startIndex));
	}

	@Override
	public long set(int index, long value) {
		return delegate().set(index, value);
	}
	
	// Builder methods
	public static LongList create(LongCollection source) {
		if (source instanceof LongArrayListWrapper) {
			final it.unimi.dsi.fastutil.longs.LongList sourceDelegate = ((LongArrayListWrapper) source).delegate();
			return new LongArrayListWrapper(clone(sourceDelegate));
		} else {
			final LongList result = create(source.size());
			result.addAll(source);
			return result;
		}
	}
	
	public static LongList create(long[] source) {
		return new LongArrayListWrapper(new it.unimi.dsi.fastutil.longs.LongArrayList(source));
	}
	
	public static LongList create(int expectedSize) {
		return new LongArrayListWrapper(new it.unimi.dsi.fastutil.longs.LongArrayList(expectedSize));
	}

	public static LongList create() {
		return new LongArrayListWrapper(new it.unimi.dsi.fastutil.longs.LongArrayList());
	}
	
	// FastUtil helper methods
	
	private static it.unimi.dsi.fastutil.longs.LongList clone(it.unimi.dsi.fastutil.longs.LongList list) {
		if (list instanceof LongArrayList) {
			return ((LongArrayList) list).clone();
		} else {
			throw new UnsupportedOperationException("Unsupported list implementation: " + list.getClass().getSimpleName());
		}
	}
	
	private static void trim(it.unimi.dsi.fastutil.longs.LongList list) {
		if (list instanceof LongArrayList) {
			((LongArrayList) list).trim();
		} else {
			throw new UnsupportedOperationException("Unsupported list implementation: " + list.getClass().getSimpleName());
		}
	}
	
}
