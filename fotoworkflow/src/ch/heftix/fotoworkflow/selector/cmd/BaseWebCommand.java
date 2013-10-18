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

import java.util.List;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * base class for web commands
 */
public abstract class BaseWebCommand implements WebCommand {

	FotoSelector fs = null;

	public BaseWebCommand(FotoSelector fs) {
		this.fs = fs;
	}

	abstract public void process(Query y, JsonResponse jr) throws Exception;

	public void handle(Request request, Response response) {

		try {

			JsonResponse jr = new JsonResponse();
			Query q = request.getQuery();

			try {
				process(q, jr);
			} catch (Exception e) {
				jr.code = "error";
				jr.msg = "cannot process: " + e.getMessage();
				// TODO
				e.printStackTrace(); // cannot process
			}

			JsonHelper.send(jr, response);

		} catch (Exception e) { // cannot send
			// TODO
			e.printStackTrace();
		}
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