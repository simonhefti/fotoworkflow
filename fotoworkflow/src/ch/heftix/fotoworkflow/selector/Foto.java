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
package ch.heftix.fotoworkflow.selector;

import ch.heftix.fotoworkflow.selector.json.Payload;

public class Foto implements Payload {
	public String path;
	public String mimeType;
	public String orientation;
	public int w;
	public int h;
	public String category;
	public String note;
	public String noteId;
	public String make;
	public String model;
	public String geo_long;
	public String geo_lat;
	public String creationdate;
	public String phash;
	public int isMissing = 0;
	public double tmpKmFrom = 0;

	public static final String defaultHash = "1111111111111111";

	public String getDescription() {
		String res = "Foto";
		if (null != note) {
			res += ", " + note;
		}
		if (null != creationdate) {
			res += ", " + creationdate;
		}
		if (null != model && !"NoModel".equals(model)) {
			res += ", " + model;
		}
		if (null != geo_long && !"NoLongitude".equals(geo_long)) {
			Double d = Double.parseDouble(geo_long);
			res += ", " + String.format("%.1f", d);
		}
		if (null != geo_lat && !"NoLatitude".equals(geo_lat)) {
			Double d = Double.parseDouble(geo_lat);
			res += ", " + String.format("%.1f", d);
		}
		return res;
	}

	public String toJSON() {
		StringBuffer sb = new StringBuffer(1024);
		sb.append("{");
		sb.append("\"thumbnail\":" + "\"/?cmd=thumbnail&path=" + path + "\"");
		sb.append(",\"scaled_to_screen\":" + "\"/?cmd=thumbnail&w=800&path=" + path + "\"");
		sb.append(",\"w400\":" + "\"/?cmd=thumbnail&w=400&path=" + path + "\"");
		sb.append(",\"w600\":" + "\"/?cmd=thumbnail&w=600&path=" + path + "\"");
		sb.append(",\"name\":" + "\"" + getName() + "\"");
		sb.append(",\"image\":" + "\"/?cmd=get&path=" + path + "\"");
		sb.append(",\"path\":\"" + path + "\"");
		if (null != orientation) {
			sb.append(",\"orientation\":\"" + orientation + "\"");
		}
		sb.append(",\"w\":\"" + Integer.toString(w) + "\"");
		sb.append(",\"h\":\"" + Integer.toString(h) + "\"");
		if (null != category) {
			sb.append(",\"category\":\"" + category + "\"");
		}
		if (null != make) {
			sb.append(",\"make\":\"" + make + "\"");
		}
		if (null != model) {
			sb.append(",\"model\":\"" + model + "\"");
		}
		if (null != geo_long) {
			sb.append(",\"geo_long\":\"" + geo_long + "\"");
		}
		if (null != geo_lat) {
			sb.append(",\"geo_lat\":\"" + geo_lat + "\"");
		}
		if (null != creationdate) {
			sb.append(",\"creationdate\":\"" + creationdate + "\"");
		}
		if (null != noteId) {
			sb.append(",\"noteId\":\"" + noteId + "\"");
		}
		if (null != note) {
			sb.append(",\"note\":\"" + note + "\"");
		}
		if (null != phash) {
			sb.append(",\"phash\":\"" + phash + "\"");
		}
		sb.append(",\"isMissing\":\"" + isMissing + "\"");
		sb.append("}");
		return sb.toString();
	}

	public String getName() {
		String res = "NoName";
		if (null != path) {
			int p = path.lastIndexOf("/");
			if (p > 0 && p + 1 < path.length()) {
				res = path.substring(p + 1);
			}
		}
		return res;
	}
}
