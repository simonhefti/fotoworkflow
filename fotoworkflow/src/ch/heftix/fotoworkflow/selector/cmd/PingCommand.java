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

import java.util.Date;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

/**
 * test response
 */
public class PingCommand implements WebCommand {

	FotoSelector fs = null;
	
	public PingCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {
		
		fs.queue.add("ping queue " + System.currentTimeMillis());
		JsonResponse jr = new JsonResponse();
		jr.code = "info";
		jr.msg = "Thanks for asking. I'm doing fine. Time: " + new Date();
		try {
			JsonHelper.send(jr, response);
		} catch (Exception e) {
			response.setCode(500);
		}
		fs.queue.add("ping queue " + System.currentTimeMillis());
	}
}