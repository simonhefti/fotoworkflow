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

public class GeoPoint {

	public double lat_deg = 180.0;
	public double lng_deg = 360.0;

	public double lat_rad = 0.0;
	public double lng_rad = 0.0;

	private boolean parsed = false;

	public GeoPoint() {

	}

	public GeoPoint(final String pnt) {
		init(pnt);
	}

	/**
	 * create point from string like 54°21′44″N, 004°31′50″W
	 */
	public void init(final String pnt) {

		parsed = false;

		if (null == pnt || pnt.length() < 1) {
			throw new IllegalArgumentException("point must be of from deg min sec, deg min sec");
		}

		String[] parts = pnt.split(",");
		if (null == parts || parts.length != 2) {
			throw new IllegalArgumentException("point must be comma separated and contain two values");
		}

		String zlat = parts[0].trim();
		String zlng = parts[1].trim();

		char ref_lat = zlat.charAt(zlat.length() - 1);
		char ref_lng = zlng.charAt(zlng.length() - 1);

		zlat = zlat.substring(0, zlat.length() - 1);
		zlng = zlng.substring(0, zlng.length() - 1);

		if ('S' == ref_lat) {
			if (!zlat.startsWith("-")) {
				zlat = "-" + zlat;
			}
		}

		if ('W' == ref_lng) {
			if (!zlng.startsWith("-")) {
				zlng = "-" + zlng;
			}
		}

		try {
			setLat(todec(zlat));
			setLng(todec(zlng));
		} catch (NumberFormatException e) {
			System.out.println("problem parsing: " + pnt);
			throw e;
		}

		parsed = true;
	}

	public void setLat(final double val) {

		lat_deg = val;
		lat_rad = Math.toRadians(lat_deg);
	}

	public void setLng(final double val) {

		lng_deg = val;
		lng_rad = Math.toRadians(lng_deg);
	}

	double todec(String glat) {

		glat = glat.replace("deg", " ");
		glat = glat.replace("°", " ");
		glat = glat.replace("'", " ");
		glat = glat.replace("\"", " ");
		glat = glat.replaceAll("\\P{InBasic_Latin}", "");
		glat = glat.replaceAll("  ", " ");
		glat = glat.replaceAll("  ", " ");

		String[] lat = glat.split(" ");

		double dlat = Double.parseDouble(lat[0]);
		if (lat.length > 1) {
			double min = Double.parseDouble(lat[1]) / 60.0;
			if (dlat > 0) {
				dlat += min;
			} else {
				dlat -= min;
			}
			if (lat.length > 2) {
				double sec = Double.parseDouble(lat[2]) / 60.0 / 60.0;
				if (dlat > 0) {
					dlat += sec;
				} else {
					dlat -= sec;
				}
			}
		}

		return dlat;
	}

	public double kmFrom(final GeoPoint B) {

		double R = 6371; // km

		double x = (B.lng_rad - lng_rad) * Math.cos((lat_rad + B.lat_rad) / 2);
		double y = (B.lat_rad - lat_rad);
		double d = Math.sqrt(x * x + y * y) * R;

		return d;
	}

	public double initialAzimuth(final GeoPoint B) {

		double lat1 = lat_rad;
		double lat2 = B.lat_rad;
		double dLon = B.lng_rad - lng_rad;

		double y = Math.sin(dLon) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
		double brng = Math.atan2(y, x);

		brng = Math.toDegrees(brng);

		brng = (brng + 360.0) % 360;

		return brng;
	}

	public String direction(final GeoPoint B) {

		String res = "";

		double dLat = B.lat_deg - lat_deg;
		double dLng = B.lng_deg - lng_deg;

		String zdLat = "N";
		String zdLng = "E";

		if (dLat < 0) {
			zdLat = "S";
		}

		if (dLng < 0) {
			zdLng = "W";
		}

		if (Math.abs(dLng) < 22.5) {
			res = zdLat;
		} else if (Math.abs(dLat) < 22.5) {
			res = zdLng;
		} else {
			res = zdLat + zdLng;
		}

		return res;
	}

	public boolean isParsed() {
		return parsed;
	}

	public String toString() {

		return String.format("%.2f, %.2f", lat_deg, lng_deg);
	}
}
