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

import java.util.Arrays;
import java.util.List;

import org.simpleframework.http.Query;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * update a DB entry for a given foto
 */
public class GetConfigCommand extends BaseWebCommand {

	public GetConfigCommand(FotoSelector fs) throws Exception {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {

		StringBufferPayload pl = new StringBufferPayload();
		String k = q.get("k");
		List<String> allowedKeys = Arrays.asList("importPattern");
		if (allowedKeys.contains(k)) {
			String t = String.format("{\"%s\": \"%s\"}", k, fs.getConf(k));
			pl.append(t);
			jr.code = "ok";
		} else {
			jr.code = "error";
			jr.msg = "key may not be queried";
		}
		jr.payload = pl;
	}
}