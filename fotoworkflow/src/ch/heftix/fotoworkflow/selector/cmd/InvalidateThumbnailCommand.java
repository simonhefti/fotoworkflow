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

import ch.heftix.fotoworkflow.selector.FotoDB;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

/**
 * invalidate cached thumbnail
 * 
 */
public class InvalidateThumbnailCommand extends BaseWebCommand {

	public InvalidateThumbnailCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Params params, JsonResponse jr) throws Exception {

		int fotoid = params.getInt("fotoid", -1);

		if (-1 == fotoid) {
			return;
		}

		FotoDB db = fs.getDB();
		db.invalidateThumbnail(fotoid);

		jr.code = "ok";
		jr.msg = "done";
	}
}