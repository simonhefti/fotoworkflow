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
package ch.heftix.fotoworkflow.selector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;

import ch.heftix.fotoworkflow.mover.FormatResult;
import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;

public class FotoMove {

	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");
	boolean dryRun = false;
	FotoDB db = null;

	public FotoMove(FotoDB db) {
		this.db = db;
	}

	public void setDryRun() {
		dryRun = true;
	}

	public void moveFoto(int fotoid, String album) throws Exception {

		Foto foto = db.getFoto(fotoid);

		File file = new File(foto.path);
		if (!file.exists()) {
			return;
		}

		TikaMetadataHelper mdh = new TikaMetadataHelper();

		String ext = mdh.getExtension(file);
		Matcher m = reExtensionFilter.matcher(ext);
		if (!m.matches()) {
			return;
		}

		Metadata metadata = null;

		try {
			metadata = mdh.readMetadata(file);
		} catch (Exception e1) {
			// cannot parse metadata - ignore
			return;
		}

		String mimeType = metadata.get("Content-Type");
		if (!mimeType.startsWith("image")) {
			return;
		}

		String patternKey = album + "Pattern";
		String pattern = db.getConf(patternKey);
		mdh.setPattern(pattern);

		FormatResult fr = mdh.format(file);
		if (fr.doSkip()) {
			note("  skipping %s", file.getAbsolutePath());
			return;
		}

		String newName = fr.getResult();

		if (dryRun) {
			note("would move %s to %s", file.getAbsolutePath(), newName);
		} else {

			File nf = new File(newName);
			nf.mkdirs();

			Path source = Paths.get(file.getAbsolutePath());
			Path target = Paths.get(newName);

			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				db.updatePath(fotoid, newName);
				note("  moved %d to %s", fotoid, newName);
			} catch (IOException e) {
				note("cannot move: %s", e);
				e.printStackTrace();
			}
		}
	}

	public void note(String fmt, Object... args) {
		String msg = String.format(fmt, args);
		System.out.println(msg);
	}

}
