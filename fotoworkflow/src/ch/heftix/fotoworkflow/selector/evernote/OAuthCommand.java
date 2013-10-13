/*
 * Copyright (C) 2008-2013 by Simon Hefti. All rights reserved.
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * Initial Developer: Simon Hefti
 */
package ch.heftix.fotoworkflow.selector.evernote;

import org.scribe.model.Token;
import org.scribe.oauth.OAuthService;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.WebCommand;

public class OAuthCommand implements WebCommand {

	FotoSelector fs = null;

	public OAuthCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			OAuthService service = fs.oAuthState.getOAuthService();
			Token requestToken = service.getRequestToken();
			fs.oAuthState.setRequestToken(requestToken);
			String authUrl = service.getAuthorizationUrl(requestToken);

			// System.out.println("* auth URL: " + authUrl);

			response.setValue("Location", authUrl);
			response.setCode(302);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}