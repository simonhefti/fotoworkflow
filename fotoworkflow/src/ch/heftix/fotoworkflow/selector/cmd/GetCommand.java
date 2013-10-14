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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;

/**
 * deliver file from file system
 */
public class GetCommand implements WebCommand {

	private TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	public void handle(Request request, Response response) {

		try {

			Query q = request.getQuery();
			String path = (String) q.get("path");

			if (null == path) {
				return;
			}

			File f = new File(path);
			if (!f.exists()) {
				return;
			}

			String ext = mdh.getExtension(f);
			Matcher m = reExtensionFilter.matcher(ext);

			if (!m.matches()) {
				return;
			}

			OutputStream os = response.getOutputStream();

			InputStream is = new FileInputStream(f);

			Metadata md = mdh.readMetadata(f);
			response.setValue("Content-Type", mdh.getMimeType(md));
			response.setDate("Last-Modified", f.lastModified());

			byte[] buf = new byte[2 << 16];
			int read = is.read(buf);
			while (read > 0) {
				os.write(buf, 0, read);
				read = is.read(buf);
			}
			is.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}