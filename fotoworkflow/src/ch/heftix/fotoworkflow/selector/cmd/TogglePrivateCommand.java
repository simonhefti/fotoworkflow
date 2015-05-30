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
public class TogglePrivateCommand extends BaseWebCommand {

	public TogglePrivateCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Params params, JsonResponse jr) throws Exception {

		int fotoid = params.getInt("fotoid", -1);

		try {
			fs.togglePrivate(fotoid);
			jr.code = "ok";
			jr.msg = String.format("updated %d", fotoid);
		} catch (Exception e) {
			jr.code = "nok";
			jr.msg = String.format("cannot update %d: %s", fotoid, e);
		}
	}
}