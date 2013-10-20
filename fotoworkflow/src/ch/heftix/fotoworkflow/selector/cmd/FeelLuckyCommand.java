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

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoDB;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * return random set of fotos
 */
public class FeelLuckyCommand extends BaseWebCommand {

	public FeelLuckyCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {

		String searchterm = q.get("st");
		// int page = q.getInteger("p");
		int pagesize = q.getInteger("n");

		FotoDB db = fs.getDB();
		// int cnt = db.countFotos();
		
		int page = (int) (Math.random() * db.countFotos() / pagesize);
		List<Foto> fns = db.feelLucky(searchterm, page, pagesize);

		StringBufferPayload pl = new StringBufferPayload();
		BaseWebCommand.list(fns, pl);

		jr.payload = pl;
		jr.code = "ok";
	}

}