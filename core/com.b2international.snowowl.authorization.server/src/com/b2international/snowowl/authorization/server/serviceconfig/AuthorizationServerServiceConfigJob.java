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
package com.b2international.snowowl.authorization.server.serviceconfig;

import com.b2international.commons.platform.PlatformUtil;
import com.b2international.snowowl.authentication.AuthenticationConfiguration;
import com.b2international.snowowl.authorization.server.AuthorizationServerActivator;
import com.b2international.snowowl.authorization.server.providers.IAuthorizationStrategy;
import com.b2international.snowowl.authorization.server.providers.file.FileBasedAuthorizationStrategy;
import com.b2international.snowowl.authorization.server.providers.ldap.LdapAuthorizationStrategy;
import com.b2international.snowowl.authorization.server.service.AdminPartyAuthorizationService;
import com.b2international.snowowl.authorization.server.service.AuthorizationService;
import com.b2international.snowowl.core.api.SnowowlServiceException;
import com.b2international.snowowl.core.users.IAuthorizationService;
import com.b2international.snowowl.datastore.serviceconfig.AbstractServerServiceConfigJob;

/**
 * Server-side service config. job, registering {@link AuthorizationService}
 * with the application context.
 * 
 *         configuration
 * @since 3.2
 */
public class AuthorizationServerServiceConfigJob extends AbstractServerServiceConfigJob<IAuthorizationService> {

	public AuthorizationServerServiceConfigJob() {
		super("Authorization service configuration...", AuthorizationServerActivator.PLUGIN_ID);
	}

	@Override
	protected Class<IAuthorizationService> getServiceClass() {
		return IAuthorizationService.class;
	}

	@Override
	protected IAuthorizationService createServiceImplementation() throws SnowowlServiceException {
		final AuthenticationConfiguration authenticationConfiguration = getSnowOwlConfiguration().getModuleConfig(
				AuthenticationConfiguration.class);
		if (authenticationConfiguration.isAdminParty()
				&& PlatformUtil.isDevVersion(AuthorizationServerActivator.PLUGIN_ID)) {
			return new AdminPartyAuthorizationService();
		}
		return getAuthorizationService(authenticationConfiguration.getType());
	}

	/**
	 * Returns the {@link IAuthorizationService} for the given JAAS type.
	 * 
	 * @param type
	 *            - the JAAS type
	 * @return
	 * @throws SnowowlServiceException
	 *             - if the given JAAS type is not available
	 */
	private IAuthorizationService getAuthorizationService(String type) throws SnowowlServiceException {
		// TODO refactor this switch to be OO
		IAuthorizationStrategy strategy = null;
		switch (type) {
		case "PROP_FILE":
			strategy = new FileBasedAuthorizationStrategy();
			break;
		case "LDAP":
			strategy = new LdapAuthorizationStrategy();
			break;
		default:
			throw new SnowowlServiceException("Unknown authorization type: " + type);
		}
		return new AuthorizationService(strategy);
	}

}