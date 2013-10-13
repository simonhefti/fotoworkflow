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
import java.io.OutputStream;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoDB.Thumbnail;

/**
 * get (or create) thumbnail from cache
 *
 */
public class GetThumbnailCommand implements WebCommand {

	FotoSelector fs = null;

	public GetThumbnailCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			Query q = request.getQuery();
			String path = (String) q.get("path");
			String width = (String) q.get("w");
			int w = 300;
			
			if (null == path) {
				return;
			}
			
			if( null != width ) {
				w = Integer.parseInt(width);
			}

			File f = new File(path);
			if (!f.exists()) {
				return;
			}

			Thumbnail thumbnail = fs.db.getThumbnail(f, w);

			OutputStream os = response.getOutputStream();

			response.setValue("Content-Type", thumbnail.mimeType);
			response.setDate("Last-Modified", f.lastModified());

			os.write(thumbnail.image);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}