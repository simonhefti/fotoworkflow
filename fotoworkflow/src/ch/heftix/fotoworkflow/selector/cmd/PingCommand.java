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

import java.util.Date;

import org.simpleframework.http.Query;

import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

/**
 * test response
 */
public class PingCommand extends BaseWebCommand {

	public PingCommand(FotoSelector fs) {
		super(fs);
	}

	public void process(Query q, JsonResponse jr) throws Exception {
		jr.code = "info";
		jr.msg = "Thanks for asking. I'm doing fine. Time: " + new Date();
	}
}