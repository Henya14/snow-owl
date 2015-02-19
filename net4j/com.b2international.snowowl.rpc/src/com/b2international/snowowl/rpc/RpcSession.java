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
package com.b2international.snowowl.rpc;

import java.util.Set;

import org.eclipse.net4j.util.io.ExtendedIOUtil.ClassResolver;

public interface RpcSession extends RpcServiceLookup {

    ////////////////////////////////////////////////
    // Session key-value store methods
    ////////////////////////////////////////////////

	/**
	 * 
	 * @param key
	 * @return
	 */
	Object get(String key);
	
	/**
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	Object put(String key, Object value);
	
	/**
	 * 
	 * @param key
	 * @return 
	 */
	Object remove(String key);

	/**
	 * 
	 * @return
	 */
	Set<String> keySet();
	
	/**
	 * 
	 * @param key
	 * @return
	 */
	boolean containsKey(String key);
	
	/**
	 * 
	 * @return
	 */
	boolean isEmpty();
	
	/**
	 * 
	 * @return
	 */
    int size();
	
    ////////////////////////////////////////////////
    // Service class loading and lookup methods
    ////////////////////////////////////////////////
    
	/**
	 * 
	 * @param serviceClass
	 */
	void registerClassLoader(Class<?> serviceClass, ClassLoader classLoader);

	/**
	 * 
	 * @param lookup
	 */
	void registerServiceLookup(RpcServiceLookup lookup);
	
	/**
	 * 
	 * @param serviceClassName
	 * @return
	 */
	ClassResolver getClassResolver(String serviceClassName);

	/**
	 * 
	 * @param serviceClassName
	 * @return
	 */
	Class<?> getServiceClassByName(String serviceClassName) throws ClassNotFoundException;

	/**
	 * 
	 * @param serviceClassName
	 * @param className
	 * @return
	 * @throws ClassNotFoundException
	 */
	Class<?> getClassByName(String serviceClassName, String className) throws ClassNotFoundException;
	
	/**
	 * 
	 * @return
	 */
	RpcProtocol getProtocol();
}