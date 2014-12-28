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

import java.io.File;
import java.util.List;

import org.simpleframework.http.Query;

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoDB;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

/**
 * search foto within foto index
 */
public class NextThumbnailCommand extends BaseWebCommand {

	public NextThumbnailCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {

		String searchterm = q.get("st");
		int page = q.getInteger("p");
		int pagesize = q.getInteger("n");
		String width = (String) q.get("w");
		int w = 300;

		if (null == searchterm) {
			return;
		}

		if (null != width) {
			w = Integer.parseInt(width);
		}

		FotoDB db = fs.getDB();

		List<Foto> fns = fs.searchFoto(searchterm, page + 1, pagesize);

		for (Foto foto : fns) {
			File f = new File(foto.path);
			db.getThumbnail(f, w);
		}

		jr.code = "ok";
	}
}