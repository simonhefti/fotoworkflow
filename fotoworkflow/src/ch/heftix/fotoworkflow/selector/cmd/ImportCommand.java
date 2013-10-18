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

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import ch.heftix.fotoworkflow.selector.FotoImport;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

public class ImportCommand extends BaseWebCommand {

	TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	public ImportCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {

		String path = q.get("path");
		String pattern = q.get("pattern");
		String note = q.get("note");

		if (null == path || path.length() < 1) {
			jr.code = "error";
			jr.msg = "path is required";
			return;
		}
		if (null == pattern || pattern.length() < 1) {
			jr.code = "error";
			jr.msg = "pattern is required";
			return;
		}

		FotoImport fi = new FotoImport(fs, pattern, note);
		File root = new File(path);

		if (!root.exists()) {
			jr.code = "error";
			jr.msg = String.format("does not exist: %s", path);
			return;
		}

		if (!root.isDirectory()) {
			jr.code = "error";
			jr.msg = String.format("must be directory: %s", path);
			return;
		}

		fs.setConf("importPattern", pattern); // store for later use
		fi.visitAllDirsAndFiles(root);
		jr.code = "ok";
		jr.msg = String.format("import complete: %s", path);
	}
}