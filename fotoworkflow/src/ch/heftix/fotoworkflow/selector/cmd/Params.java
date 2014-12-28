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
package ch.heftix.fotoworkflow.selector.cmd;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;

@SuppressWarnings("serial")
public class Params implements Serializable {
	private Map<String, String> params = new HashMap<String, String>();
	private String target = null;

	public Params(IHTTPSession session) {
		this.params = session.getParms();
		this.target = session.getUri();
		if (null == this.target) {
			this.target = "/";
		}
	}

	public String get(String key) {
		return params.get(key);
	}

	public int getInt(String key, int def) {
		int res = def;
		String v = get(key);
		if (null != v && v.length() > 0) {
			try {
				res = Integer.parseInt(v);
			} catch (NumberFormatException e) {
				// fall back to default
			}
		}
		return res;
	}

	public String getTarget() {
		return this.target;
	}

}