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

import ch.heftix.fotoworkflow.selector.FotoSelector;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * update a DB entry for a given foto
 */
public class ExcludePrivateCommand implements WebCommand {

	FotoSelector fs = null;

	public ExcludePrivateCommand(FotoSelector fs) throws Exception {
		this.fs = fs;
	}

	public Response handle(Params params) {

		Response r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request");

		try {

			fs.toggleExcludePrivatey();

			r = new Response(Status.OK, "text/plain", "done");
		} catch (Exception e) {
			r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request: " + e);
		}

		return r;
	}
}