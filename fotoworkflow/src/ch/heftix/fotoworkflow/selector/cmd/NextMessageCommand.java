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

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * search foto within foto index
 */
public class NextMessageCommand implements WebCommand {

	FotoSelector fs = null;

	public NextMessageCommand(FotoSelector fs) throws Exception {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			JsonResponse jr = new JsonResponse();

			StringBufferPayload pl = new StringBufferPayload();

			pl.append("[");

			String msg = fs.queue.poll();

			if (null != msg) {
				pl.append("{");
				pl.append("\"msg\": \"" + msg + "\"");
				pl.append("}");
			}

			pl.append("]");

			jr.code = "ok";
			jr.msg = msg;
			jr.payload = pl;

			JsonHelper.send(jr, response);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}