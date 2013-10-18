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
package ch.heftix.fotoworkflow.selector.json;

import java.io.PrintStream;

import org.simpleframework.http.Response;

public class JsonHelper {

	public static void send(JsonResponse jr, Response r) throws Exception {
		long time = System.currentTimeMillis();

		r.setValue("Content-Type", "text/html");
		r.setDate("Last-Modified", time);

		PrintStream body = r.getPrintStream();

		body.print(jr.toJSON());
	}

}