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

import java.util.List;

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * base class for web commands
 */
public abstract class BaseWebCommand implements WebCommand {

	FotoSelector fs = null;

	public BaseWebCommand(FotoSelector fs) {
		this.fs = fs;
	}

	abstract public void process(Params params, JsonResponse jr) throws Exception;

	public Response handle(Params params) {

		Response r = new Response(Status.INTERNAL_ERROR, "text/plain", "cannot handle request");

		try {

			JsonResponse jr = new JsonResponse();

			try {
				process(params, jr);
			} catch (Exception e) {
				jr.code = "error";
				jr.msg = "cannot process: " + e.getMessage();
				// TODO
				e.printStackTrace(); // cannot process
			}

			r = new Response(Status.OK, "application/json;charset=UTF-8", jr.toJSON());

		} catch (Exception e) { // cannot send
			// TODO
			e.printStackTrace();
		}

		return r;
	}

	public static void list(List<Foto> elements, StringBufferPayload sb) {

		sb.append("[");

		boolean first = true;

		for (Foto fn : elements) {
			String line = fn.toJSON();
			if (first) {
				first = false;
			} else {
				line = ", " + line;
			}
			sb.append(line);
		}

		sb.append("]");
	}

}