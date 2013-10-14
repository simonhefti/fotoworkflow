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
package ch.heftix.fotoworkflow.selector.cmd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import ch.heftix.fotoworkflow.selector.FotoSelector;

/**
 * deliver file from class path
 */
public class GetCLCommand implements WebCommand {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();

	private Properties extensionMap = new Properties();
	private Properties targetMap = new Properties();

	public GetCLCommand(FotoSelector fs) throws IOException {

		InputStream is = FotoSelector.class.getResourceAsStream("resources.properties");
		targetMap.load(is);
		is.close();

		is = FotoSelector.class.getResourceAsStream("extensions.properties");
		extensionMap.load(is);
		is.close();
	}

	public String mapResource(String name) {
		return (String) targetMap.getProperty(name);
	}

	public String mapExtension(String fn) {
		String ext = mdh.getExtension(fn);
		return (String) extensionMap.getProperty(ext);
	}

	public void handle(Request request, Response response) {

		try {

			String target = request.getTarget();
			String name = mapResource(target);

			if (null == name) {
				PrintStream body = response.getPrintStream();
				response.setValue("Content-Type", "text/plain");
				body.println("No target registered for: " + target);
				return;
			}

			InputStream is = FotoSelector.class.getResourceAsStream(name);
			if (null == is) {
				PrintStream body = response.getPrintStream();
				response.setValue("Content-Type", "text/plain");
				body.println("No target registered for: " + target);
				return;
			}

			OutputStream os = response.getOutputStream();

			String mt = mapExtension(name);
			if (null == mt) {
				mt = "text/plain";
			}
			response.setValue("Content-Type", mt);

			byte[] buf = new byte[2 << 16];
			int read = is.read(buf);
			while (read > 0) {
				os.write(buf, 0, read);
				read = is.read(buf);
			}
			is.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}