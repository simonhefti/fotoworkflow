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

public class FotoImport {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");
	boolean dryRun = false;
	FotoSelector fs = null;
	FotoDB db = null;
	String note = null;

	public FotoImport(FotoSelector fs, String pattern, String note) {
		this.fs = fs;
		this.db = fs.getDB();
		this.note = note;
		mdh.setPattern(pattern);
	}

	public void setDryRun() {
		dryRun = true;
	}

	public void handleFile(File file) throws Exception {

		// System.out.println("handling file: " + file);

		if (null == file) {
			return;
		}

		if (!file.exists()) {
			return;
		}

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
		// System.out.println("  mime type: " + mimeType);

		if (!mimeType.startsWith("image")) {
			return;
		}

		// check for source
		// String make = metadata.get("Make");
		// if (null == make || make.length() < 1) {
		// note("make: %s (%s)", make, file);
		// }

		// System.out.println(file.getAbsolutePath());
		
		FormatResult fr = mdh.format(file);
		if (fr.doSkip()) {
			note("  skipping %s", file.getAbsolutePath());
			return;
		}

		if (fr.doDelete()) {
			note("  exists, deleting source %s", file.getAbsolutePath());
			file.delete();
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
				note("  --> %s", newName);
				if (db.existsFoto(newName)) {
					note("  already stored, updating %s", newName);
					db.updateFoto(nf, note);
				} else {
					db.insertFoto(nf, note);
				}
				fs.message("imported " + nf.getName());
			} catch (IOException e) {
				fs.message("cannot import: %s", e);
				e.printStackTrace();
			}
		}
	}

	public void visitAllDirsAndFiles(File dir) throws Exception {

		// System.out.println("visiting: " + dir);

		if (null == dir) {
			return;
		}

		if (dir.exists() && dir.isDirectory()) {

			String[] children = dir.list();
			if (null != children) {
				for (int i = 0; i < children.length; i++) {
					if (null != children[i]) {
						visitAllDirsAndFiles(new File(dir, children[i]));
					}
				}
			}
		} else {
			handleFile(dir);
		}
	}

	public void note(String fmt, Object... args) {
		String msg = String.format(fmt, args);
		fs.message(msg);
		System.out.println(msg);
	}

}
