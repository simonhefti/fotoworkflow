/*
 * Copyright (C) 2008-2015 by Simon Hefti. All rights reserved.
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
import java.util.Properties;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

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

	public Response handle(Params params) {

		Response r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request");

		try {

			String target = params.getTarget();

			String name = mapResource(target);

			if (null == name) {
				r = new Response(Status.OK, "text/plain", "No target registered for: " + target);
				return r;
			}

			InputStream is = FotoSelector.class.getResourceAsStream(name);
			if (null == is) {
				r = new Response(Status.OK, "text/plain", "No target available for: " + target);
				return r;
			}

			String mt = mapExtension(name);
			if (null == mt) {
				mt = "text/plain";
			}

			r = new Response(Status.OK, mt, is);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return r;

	}
}