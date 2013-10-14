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

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;

public class FotoImport {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");
	boolean dryRun = false;
	FotoSelector fs = null;
	FotoDB db = null;
	Pattern repetitionFinder = Pattern.compile("(.+?)_\\1+.*");
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

	public String fixFilenameRepetition(String fn) {

		String res = fn;
		Matcher matcher = repetitionFinder.matcher(fn);
		String repeated = matcher.matches() ? matcher.group(1) : null;
		while (null != repeated) {
			res = fn.replaceFirst(repeated + "_", "");
			matcher = repetitionFinder.matcher(res);
			repeated = matcher.matches() ? matcher.group(1) : null;
		}
		return res;
	}

	public void handleFile(File file) throws Exception {

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
		if (!mimeType.startsWith("image")) {
			return;
		}

		// check for source
		String make = metadata.get("Make");
		if (null == make || make.length() < 1) {
			note("make: %s (%s)", make, file);
		}

		System.out.println(file.getAbsolutePath());

		String newName = mdh.format(file);
		newName = fixFilenameRepetition(newName);

		if (dryRun) {
			note("would move %s to %s", file.getAbsolutePath(), newName);
		} else {

			if (db.existsFoto(newName)) {
				note("skip (%s already stored)", newName);
			} else {
				File nf = new File(newName);
				nf.mkdirs();

				Path source = Paths.get(file.getAbsolutePath());
				Path target = Paths.get(newName);
				try {
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
					note("  --> %s", newName);
					db.insertFoto(nf, note);
					fs.queue.add("imported " + nf.getName());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void visitAllDirsAndFiles(File dir) throws Exception {
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

	public static void note(String fmt, Object... args) {
		System.out.println(String.format(fmt, args));
	}

}
