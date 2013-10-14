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
import java.util.regex.Pattern;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import ch.heftix.fotoworkflow.selector.FotoImport;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

public class ImportCommand implements WebCommand {

	TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");
	FotoSelector fs = null;

	public ImportCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {
			JsonResponse jr = new JsonResponse();

			Query q = request.getQuery();
			String path = q.get("path");
			String pattern = q.get("pattern");
			String note = q.get("note");

			boolean ok = true;

			if (null == path || path.length() < 1) {
				ok = false;
				jr.code = "error";
				jr.msg = "path is required";
			}
			if (null == pattern || pattern.length() < 1) {
				ok = false;
				jr.code = "error";
				jr.msg = "pattern is required";
			}

			FotoImport fi = new FotoImport(fs, pattern, note);
			File root = new File(path);

			if (!root.exists()) {
				jr.code = "error";
				jr.msg = String.format("does not exist: %s", path);
				ok = false;
			}

			if (ok && !root.isDirectory()) {
				ok = false;
				jr.code = "error";
				jr.msg = String.format("must be directory: %s", path);
			}

			if (ok) {
				fs.setConf("importPattern", pattern); // store for later use
				// efu.setDryRun();
				fi.visitAllDirsAndFiles(root);
				jr.code = "ok";
				jr.msg = String.format("import complete: %s", path);
			}

			JsonHelper.send(jr, response);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}