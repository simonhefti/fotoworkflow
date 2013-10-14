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

import java.util.List;

import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;

import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Notebook;

public class EvernoteVerifyCommand implements WebCommand {

	FotoSelector fs = null;

	public EvernoteVerifyCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			OAuthService service = fs.oAuthState.getOAuthService();
			String zv = request.getParameter("oauth_verifier");
			System.out.println("* auth verifier: " + zv);

			Verifier v = new Verifier(zv);
			Token accessToken = service.getAccessToken(fs.oAuthState.getRequestToken(), v);

			// System.out.println("* accessToken: " + accessToken.getToken());

			// fs.oAuthState.setAccessToken(accessToken);
			fs.setConf("evernote.accestoken", accessToken.getToken());
			
			response.setValue("Location", "/");
			response.setCode(302);

//			NoteStore.Client noteStore = fs.oAuthState.getNoteStore(accessToken.getToken());
//			List<Notebook> notebooks = noteStore.listNotebooks(accessToken.getToken());
//			for (Notebook notebook : notebooks) {
//				System.out.println(notebook.getName());
//			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}