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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * deliver file from file system
 */
public class GetCommand implements WebCommand {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	public Response handle(Params params) {

		Response r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request");

		try {

			String path = (String) params.get("path");

			if (null == path) {
				return r;
			}

			File f = new File(path);
			if (!f.exists()) {
				return r;
			}

			String ext = mdh.getExtension(f);
			Matcher m = reExtensionFilter.matcher(ext);

			if (!m.matches()) {
				return r;
			}

			Metadata md = mdh.readMetadata(f);

			InputStream is = new FileInputStream(f);

			r = new Response(Status.OK, mdh.getMimeType(md), is);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return r;
	}
}