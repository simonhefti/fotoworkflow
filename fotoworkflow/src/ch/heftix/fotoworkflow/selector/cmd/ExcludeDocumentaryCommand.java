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

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.FotoSelector;

/**
 * update a DB entry for a given foto
 */
public class ExcludeDocumentaryCommand implements WebCommand {

	FotoSelector fs = null;

	public ExcludeDocumentaryCommand(FotoSelector fs) throws Exception {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {

		try {

			long time = System.currentTimeMillis();

			fs.toggleExcludeDocumentary();

			response.setValue("Content-Type", "text/plain");
			response.setDate("Last-Modified", time);

			PrintStream body = response.getPrintStream();
			body.println("done");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}