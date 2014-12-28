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

/**
 * search foto within foto index
 */
public class SearchFotoCommand extends BaseWebCommand {

	public SearchFotoCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Params params, JsonResponse jr) throws Exception {

		String searchterm = params.get("st");

		int page = params.getInt("p", 0);
		int pagesize = params.getInt("n", 10);

		List<Foto> fns = fs.searchFoto(searchterm, page, pagesize);

		StringBufferPayload pl = new StringBufferPayload();
		BaseWebCommand.list(fns, pl);

		jr.payload = pl;
		jr.code = "ok";
	}
}