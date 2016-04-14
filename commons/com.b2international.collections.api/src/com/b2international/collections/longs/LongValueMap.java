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

import java.util.Set;

import com.b2international.collections.LongCollection;
import com.b2international.collections.PrimitiveValueMap;

/**
 * @since 4.6
 * 
 * @param <K> 
 */
public interface LongValueMap<K> extends PrimitiveValueMap {

	boolean containsKey(K key);
	
	@Override
	LongValueMap<K> dup();

	long get(K key);

	Set<K> keySet();
	
	long put(K key, long value);

	long remove(K key);
	
	@Override
	LongCollection values();
}
