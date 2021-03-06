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
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

/**
 * update a DB entry for a given foto
 */
public class ConfigSetCommand extends BaseWebCommand {

	public ConfigSetCommand(FotoSelector fs) throws Exception {
		super(fs);
	}

	public void process(Params params, JsonResponse jr) throws Exception {

		String k = params.get("k");
		String v = params.get("v");
		if (ConfigGetCommand.allowedKeys.contains(k)) {
			fs.setConf(k, v);
			jr.msg = "configuration saved";
			jr.code = "ok";
		} else {
			jr.code = "error";
			jr.msg = "key may not be queried";
		}
	}
}