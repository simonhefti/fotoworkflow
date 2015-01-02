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

			int fotoid = params.getInt("fotoid", -1);
			int width = params.getInt("w", 300);
			int height = params.getInt("h", 300);

			if (-1 == fotoid) {
				return r;
			}

			FotoDB db = fs.getDB();

			Thumbnail thumbnail = db.getThumbnail(fotoid, width, height);

			ByteArrayInputStream bais = new ByteArrayInputStream(thumbnail.image);

			r = new Response(Status.OK, thumbnail.mimeType, bais);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return r;

	}
}