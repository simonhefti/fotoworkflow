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

import org.simpleframework.http.Query;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * get next message from queue
 */
public class NextMessageCommand extends BaseWebCommand {

	public NextMessageCommand(FotoSelector fs) throws Exception {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {

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
	}
}