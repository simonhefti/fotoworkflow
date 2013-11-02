/*
 * Copyright (C) 2008-2013 by Simon Hefti.
 * All rights reserved.
 * 
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 */
package ch.heftix.fotoworkflow.selector.json;

public class JsonResponse {
	public String code = "ok";
	public String msg = "";
	public Payload payload = null;

	public String toJSON() {
		StringBuffer sb = new StringBuffer(1024);
		sb.append("{");
		sb.append("\"code\":\"" + code + "\"");
		sb.append(",\"msg\":\"" + msg + "\"");
		if (null != payload) {
			String pl = payload.toJSON();
			if (null != pl && pl.length() > 0) {
				sb.append(",\"payload\":" + pl + "");
			}
		}
		sb.append("}");
		return sb.toString();
	}
}
