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
package ch.heftix.fotoworkflow.selector.evernote;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.EvernoteApi;
import org.scribe.model.Token;
import org.scribe.oauth.OAuthService;

import ch.heftix.fotoworkflow.selector.Version;

import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.userstore.UserStore;
import com.evernote.thrift.protocol.TBinaryProtocol;
import com.evernote.thrift.transport.THttpClient;

public class EvernoteUtil {

	private OAuthService oAuthService = null;
	private Token reqToken = null;
	private UserStore.Client userStore = null;
	private NoteStore.Client noteStore = null;

	public NoteStore.Client getNoteStore(String token) throws Exception {

		THttpClient userStoreTrans = new THttpClient("https://www.evernote.com/edam/user");
		TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
		userStore = new UserStore.Client(userStoreProt, userStoreProt);

		String notestoreUrl = userStore.getNoteStoreUrl(token);

		// Set up the NoteStore client
		THttpClient noteStoreTrans = new THttpClient(notestoreUrl);
		noteStoreTrans.setCustomHeader("User-Agent", "FotoWorkflow/" + Version.getVersion());
		TBinaryProtocol noteStoreProt = new TBinaryProtocol(noteStoreTrans);
		noteStore = new NoteStore.Client(noteStoreProt, noteStoreProt);

		return noteStore;
	}

	public OAuthService getOAuthService() {

		if (null == oAuthService) {
			String consumerKey = "simon_hefti-6843";
			String consumerSecret = "5195d0426770cf7d";

			oAuthService = new ServiceBuilder().provider(EvernoteApi.class).apiKey(consumerKey)
					.apiSecret(consumerSecret).callback("http://localhost:1994?cmd=evernote-verify").build();
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