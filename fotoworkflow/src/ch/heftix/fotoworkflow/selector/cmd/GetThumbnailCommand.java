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

import java.io.ByteArrayInputStream;
import java.io.File;

import ch.heftix.fotoworkflow.selector.FotoDB;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.Thumbnail;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * get (or create) thumbnail from cache
 *
 */
public class GetThumbnailCommand implements WebCommand {

	FotoSelector fs = null;

	public GetThumbnailCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public Response handle(Params params) {

		Response r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request");

		try {

			String path = (String) params.get("path");
			String width = (String) params.get("w");
			int w = 280;

			if (null == path) {
				return r;
			}

			if (null != width) {
				w = Integer.parseInt(width);
			}

			File f = new File(path);
			if (!f.exists()) {
				return r;
			}

			FotoDB db = fs.getDB();

			Thumbnail thumbnail = db.getThumbnail(f, w);

			ByteArrayInputStream bais = new ByteArrayInputStream(thumbnail.image);

			r = new Response(Status.OK, thumbnail.mimeType, bais);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return r;

	}
}