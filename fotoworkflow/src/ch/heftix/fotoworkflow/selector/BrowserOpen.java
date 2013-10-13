/*
 * Copyright (C) 2010-2013 by Simon Hefti.
 * All rights reserved.
 * 
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 */
package ch.heftix.fotoworkflow.selector;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class BrowserOpen {

	public static void openURL(String url) {

		boolean opened = false;

		if (null != url && url.length() > 5 && Desktop.isDesktopSupported()) {
			try {
				Desktop desk = Desktop.getDesktop();
				URI uri = new URI(url);
				desk.browse(uri);
				opened = true;
			} catch (URISyntaxException e) {
				note("w ignoring invalid URL: %s", url);
				return;
			} catch (UnsupportedOperationException e) {
				note("w System cannot open browser. Please navigate to '%s'", url);
				opened = false;
			} catch (IOException e) {
				note("w System cannot open browser. Please navigate to '%s'", url);
				opened = false;
			}
		}

		if (!opened) {
			String osName = System.getProperty("os.name");
			String cmd = "";
			if (osName.startsWith("Mac OS")) {
				cmd = String.format("open %s", url);
			} else if (osName.startsWith("Windows"))
				cmd = String.format("rundll32 url.dll,FileProtocolHandler %s", url);
			else { // assume Unix or Linux
				String[] browsers = { "google-chrome", "firefox", "opera", "epiphany", "konqueror", "conkeror",
						"midori", "kazehakase", "mozilla" };
			}
			try {
				Runtime.getRuntime().exec(cmd);
			} catch (IOException e) {
				note("w System cannot open browser. Please navigate to '%s'", url);
				opened = false;
			}

		}

	}

	public static void note(String fmt, Object... args) {
		System.out.println(String.format(fmt, args));
	}
}
