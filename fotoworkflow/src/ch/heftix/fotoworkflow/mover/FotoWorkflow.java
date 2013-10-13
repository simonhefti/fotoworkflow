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
package ch.heftix.fotoworkflow.mover;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;

public class FotoWorkflow {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();
	String pattern = null;
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");
	boolean dryRun = false;

	public static void main(String args[]) throws Exception {

		FotoWorkflow efu = new FotoWorkflow();
		File root = new File("/Users/hefti/fotos-to-upload-to-evernote");
//		root = new File("/Users/hefti/Downloads");
//		root = new File("/Users/hefti/fotoarchive-2/2011");
//		root = new File("/Users/hefti/fotoarchive-2/2010");
//
//		root = new File("/Users/hefti/exif-foto-handling");
//
//		root = new File("/Users/hefti/fotoarchive-2/2009");
//		root = new File("/Users/hefti/fotoarchive-2/2008");
		root = new File("/Users/hefti/fotoarchive-2/2007");
		root = new File("/Users/hefti/fotoarchive-2/2006");
		root = new File("/Users/hefti/fotoarchive-2/2005");

		root = new File("/Users/hefti/fotoarchive-2");

		root = new File("/Users/hefti/foto-preselection");

		String pattern = "/Users/hefti/foto-archive-3";
		
		// pattern = "/Users/hefti/foto-preselection";

		pattern += "/@{CreationDate: yyyy}";
		pattern += "/@{Model}";
		pattern += "/@{CreationDate: yyyy-MM}";
		pattern += "/@{CreationDate: yyyy-MM-dd'T'HHmm}_@{Filename}@{Unique}.@{Extension}";

		// efu.dryRun = true;
		efu.mdh.setPattern(pattern);

		if (!root.exists()) {
			note(":( root does not exist - stop (%s)", root);
		}
		if (!root.isDirectory()) {
			note(":( root is not a dir - stop (%s)", root);
		}

		efu.visitAllDirsAndFiles(root);
	}

	public FotoWorkflow() {
	}
	
	public void setPattern(String pattern) {
		mdh.setPattern(pattern);
	}

	public void setDryRun() {
		dryRun = true;
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

		// mdh.listMetadata(metadata);

		// check for source
		String make = metadata.get("Make");
		if (null == make || make.length() < 1) {
			note("make: %s (%s)", make, file);
			// mdh.listMetadata(metadata);
			// return;
		}

		System.out.println(file.getAbsolutePath());

		String newName = mdh.format(file);

		if (dryRun) {
			note("would move %s to %s", file.getAbsolutePath(), newName);
		} else {

			File nf = new File(newName);
			nf.mkdirs();

			Path source = Paths.get(file.getAbsolutePath());
			Path target = Paths.get(newName);
			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				note("  --> %s",newName);
			} catch (IOException e) {
				e.printStackTrace();
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
