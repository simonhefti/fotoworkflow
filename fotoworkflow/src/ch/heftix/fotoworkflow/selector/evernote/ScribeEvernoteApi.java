package ch.heftix.fotoworkflow.selector.evernote;

import org.scribe.builder.api.EvernoteApi;
import org.scribe.model.Token;

public class ScribeEvernoteApi extends EvernoteApi.Sandbox {
	@Override
	public String getAuthorizationUrl(Token requestToken) {
		return String.format("https://sandbox.evernote.com/OAuth.action?oauth_token=%s", requestToken.getToken());
	}
}
