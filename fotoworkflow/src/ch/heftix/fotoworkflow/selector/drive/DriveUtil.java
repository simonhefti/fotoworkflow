/*
 * Copyright (C) 2008-2014 by Simon Hefti. All rights reserved.
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * Initial Developer: Simon Hefti
 */
package ch.heftix.fotoworkflow.selector.drive;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.GoogleApi;
import org.scribe.model.Token;
import org.scribe.oauth.OAuthService;

import com.google.api.services.drive.DriveScopes;

public class DriveUtil {

	private OAuthService oAuthService = null;
	private Token reqToken = null;

	public OAuthService getOAuthService() {

		if (null == oAuthService) {
			String consumerKey = "755886059977-phgibji92viqbb4u0uebnol4u0rm0klt.apps.googleusercontent.com";
			String consumerSecret = "F853Te0Dye-hdAnx7_3qboHM";
			
			oAuthService = new ServiceBuilder()
            .provider(GoogleApi.class)
            .apiKey(consumerKey)
            .apiSecret(consumerSecret)
            .scope(DriveScopes.DRIVE)
            .callback("http://localhost:1994?cmd=drive.verify")
            .build();
		}

		return oAuthService;
	}

	public void setRequestToken(Token t) {
		reqToken = t;
	}

	public Token getRequestToken() {
		return reqToken;
	}

}