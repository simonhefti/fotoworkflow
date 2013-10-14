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

import java.io.PrintStream;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;

/**
 * get (or create) thumbnail from cache
 * 
 */
public class InvalidateThumbnailCommand implements WebCommand {

	FotoSelector fs = null;

	public InvalidateThumbnailCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			Query q = request.getQuery();
			String path = (String) q.get("path");

			if (null == path) {
				return;
			}

			fs.invalidateThumbnail(path);

			PrintStream ps = response.getPrintStream();
			response.setValue("Content-Type", "text/plain");
			response.setDate("Last-Modified", System.currentTimeMillis());
			ps.println("done");

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}