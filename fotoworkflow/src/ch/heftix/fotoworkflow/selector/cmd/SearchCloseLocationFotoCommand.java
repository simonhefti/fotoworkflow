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
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoDB;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;
import ch.heftix.fotoworkflow.selector.json.StringBufferPayload;

/**
 * search foto based on date
 */
public class SearchCloseLocationFotoCommand implements WebCommand {

	FotoSelector fs = null;

	public SearchCloseLocationFotoCommand(FotoSelector fs) throws Exception {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			JsonResponse jr = new JsonResponse();

			StringBufferPayload pl = new StringBufferPayload();

			Query q = request.getQuery();
			String path = q.get("path");
			int page = q.getInteger("p");
			int pagesize = q.getInteger("n");

			FotoDB db = fs.getDB();

			List<Foto> fns = db.searchCloseLocation(path, page, pagesize);

			pl.append("[");

			boolean first = true;

			for (Foto fn : fns) {
				String line = fn.toJSON();
				if (first) {
					first = false;
				} else {
					line = ", " + line;
				}
				pl.append(line);
			}

			pl.append("]");

			jr.code = "ok";
			jr.payload = pl;

			JsonHelper.send(jr, response);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}