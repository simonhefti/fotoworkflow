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
package ch.heftix.fotoworkflow.selector.drive;

import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;

public class DriveVerifyCommand implements WebCommand {

	FotoSelector fs = null;

	public DriveVerifyCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			OAuthService service = fs.driveState.getOAuthService();
			String zv = request.getParameter("oauth_verifier");
			System.out.println("* auth verifier: " + zv);

			Verifier v = new Verifier(zv);
			Token accessToken = service.getAccessToken(fs.driveState.getRequestToken(), v);

			System.out.println("* accessToken: " + accessToken.getToken());

			// fs.oAuthState.setAccessToken(accessToken);
			fs.setConf("drive.accestoken", accessToken.getToken());

			response.setValue("Location", "/");
			response.setCode(302);

		} catch (Exception e) {
			fs.message("cannot verify Google Drive authorization: %s", e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}