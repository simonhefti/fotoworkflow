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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

/**
 * formats metadata information of image according to format string
 */
public class TikaMetadataHelper {

	private DateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
	private MetadataFormatPattern[] mfps = null;
	private Pattern reFileExtension = Pattern.compile("[.]([^.]+)$");
	Pattern repetitionFinder = Pattern.compile(".+(.{3,})_\\1+.*");

	public String fixFilenameRepetition(String fn) {

		String res = fn;
		Matcher matcher = repetitionFinder.matcher(fn);
		String repeated = matcher.matches() ? matcher.group(1) : null;
		while (null != repeated) {
			// System.out.println("repeated '" + repeated + "'");
			res = res.replaceFirst(repeated + "_", "");
			// System.out.println("  new: '" + res + "'");
			matcher = repetitionFinder.matcher(res);
			repeated = matcher.matches() ? matcher.group(1) : null;
		}
		return res;
	}

	public String fixHashedFilename(String fn) {

		String res = fn;

		int pos = fn.lastIndexOf("_");

		if (pos > 0) {
			String uid = fn.substring(pos + 1, fn.length());
			String pre = fn.substring(0, pos);
			if (uid.length() > 16) {
				String ext = getExtension(fn);
				int nbr = (int)(99999 * Math.random());
				res = String.format("%s_%d.%s", pre, nbr, ext);
			}
		}
		return res;
	}

	/** get GPS Altitude field */
	public double getAltitude(Metadata md) {
		double res = 0;
		String tmp = md.get("GPS Altitude");
		if (null != tmp && tmp.length() > 1) {
			String[] parts = tmp.split(" ");
			tmp = parts[0];
			if (null != tmp && tmp.length() > 1) {
				try {
					res = Double.parseDouble(tmp);
				} catch (NumberFormatException e) {
					// catch silently
					e.printStackTrace();
				}
			}
		}
		return res;
	}

	/** helper for integer parsing */
	private int parseInt(String v) {
		int res = 0;
		if (null != v && v.length() > 1) {
			try {
				res = Integer.parseInt(v);
			} catch (NumberFormatException e) {
				// catch silently
				e.printStackTrace();
			}
		}
		return res;
	}

	/** get image width field */
	public int getWidth(Metadata md) {
		int res = 0;
		String tmp = md.get("Image Width");
		if (null != tmp && tmp.length() > 1) {
			String[] parts = tmp.split(" ");
			tmp = parts[0];
			res = parseInt(tmp);
		}
		return res;
	}

	/** get image height field */
	public int getHeight(Metadata md) {
		int res = 0;
		String tmp = md.get("Image Height");
		if (null != tmp && tmp.length() > 1) {
			String[] parts = tmp.split(" ");
			tmp = parts[0];
			res = parseInt(tmp);
		}
		return res;
	}

	/** get Creation-Date field */
	public Date getCreationDate(Metadata md) {
		Date res = null;
		String zd = md.get("Creation-Date");
		if (null == zd) {
			return null;
		}
		try {
			res = parser.parse(zd);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * combine creation date and model
	 * 
	 * @deprecated
	 * */
	public String getImageDescription(Metadata md) {

		Date dt = getCreationDate(md);
		String d = "";
		if (null != dt) {
			d = " | " + formatter.format(dt);
		}
		String m = md.get("Model");
		GeoPoint gp = getGPS(md);
		String g = "";
		if (null != gp) {
			g = " | " + gp.toString();
		}

		return m + d + g;
	}

	/** get GPS data (long, lat) as point */
	public GeoPoint getGPS(Metadata md) {

		String lng = md.get("GPS Longitude");
		String lng_ref = md.get("GPS Longitude Ref");

		if (null == lng || null == lng_ref) {
			return null;
		}

		lng = lng + lng_ref;

		String lat = md.get("GPS Latitude");
		String lat_ref = md.get("GPS Latitude Ref");

		if (null == lat || null == lat_ref) {
			return null;
		}

		lat = lat + lat_ref;

		String pnt = lat + ", " + lng;

		GeoPoint res = new GeoPoint();
		res.init(pnt);

		return res;
	}

	/** define formatting pattern */
	public void setPattern(String pattern) {
		mfps = parsePattern(pattern);
	}

	/** understand formatting pattern */
	MetadataFormatPattern[] parsePattern(String pattern) {

		List<MetadataFormatPattern> tmp = new ArrayList<MetadataFormatPattern>();

		String regexp = "\\@\\{([A-Za-z]*):*([^}]*)\\}"; // find @{}
		Pattern p = Pattern.compile(regexp);

		Matcher m = p.matcher(pattern);
		int pos = 0;
		while (m.find()) {
			int start = m.start();
			int end = m.end();
			if (start != pos) {
				MetadataFormatPattern mfp = new MetadataFormatPattern();
				mfp.definition = pattern.substring(pos, start);
				tmp.add(mfp);
			}
			String k = m.group(1);
			String v = m.group(2);
			MetadataFormatPattern mfp = new MetadataFormatPattern();
			mfp.definition = pattern.substring(start, end);
			mfp.tagName = k.trim();
			mfp.format = v.trim();
			tmp.add(mfp);
			pos = end;
		}
		if (pos < pattern.length()) {
			MetadataFormatPattern mfp = new MetadataFormatPattern();
			mfp.definition = pattern.substring(pos, pattern.length());
			tmp.add(mfp);
		}

		MetadataFormatPattern[] res = new MetadataFormatPattern[tmp.size()];
		tmp.toArray(res);

		return res;
	}

	/** wrap tika read */
	public Metadata readMetadata(String fn) throws IOException {
		if (null == fn) {
			return null;
		}
		File f = new File(fn);
		if (!f.exists()) {
			return null;
		}
		if (f.isDirectory()) {
			return null;
		}
		return readMetadata(f);
	}

	/** wrap tika read */
	public Metadata readMetadata(File f) throws IOException {

		Tika tika = new Tika();

		InputStream is = new FileInputStream(f);
		Metadata metadata = new Metadata();

		tika.parse(is, metadata);
		is.close();

		return metadata;
	}

	/** helper: list all exif fields */
	public void listMetadata(File f) throws IOException {

		Metadata metadata = readMetadata(f);
		listMetadata(metadata);
	}

	/** helper: list all exif fields */
	public void listMetadata(Metadata metadata) throws IOException {

		for (String name : metadata.names()) {
			System.out.println("k: '" + name + "' -> '" + metadata.get(name) + "'");
		}
	}

	/** format according to pattern */
	public String format(File f) throws IOException {
		return format(f, mfps);
	}

	/** format according to pattern */
	public String format(File f, Metadata metadata) throws IOException {
		return format(f, metadata, mfps);
	}

	/** format according to pattern */
	public String format(File f, MetadataFormatPattern[] ps) throws IOException {

		Metadata metadata = readMetadata(f);
		return format(f, metadata, ps);
	}

	/** format according to pattern */
	public String format(String fn, Metadata metadata, String pattern) throws IOException {

		MetadataFormatPattern p[] = parsePattern(pattern);
		File f = new File(fn);
		return format(f, metadata, p);
	}

	/** format according to pattern */
	public String format(File f, Metadata metadata, String pattern) throws IOException {
		MetadataFormatPattern p[] = parsePattern(pattern);
		return format(f, metadata, p);
	}

	/**
	 * format according to pattern
	 * 
	 * @param f
	 *            source file
	 * @param metadata
	 *            metadata of f
	 * @param ps
	 *            parsed array of patterns
	 * @return formatted file name, or null if SkipExisting is true and
	 *         destination exists
	 */
	public String format(File f, Metadata metadata, MetadataFormatPattern[] ps) throws IOException {

		String res = "";
		StringBuffer sb = new StringBuffer(1024);

		boolean checkUnique = false;
		boolean skipExisting = false;

		for (MetadataFormatPattern p : ps) {
			if (null == p.tagName || p.tagName.length() < 1) {
				sb.append(p.definition);
			} else if ("Model".equals(p.tagName)) {
				String v = metadata.get("Model");
				if (null == v || v.length() < 1) {
					v = "NoModel";
				}
				sb.append(v);
			} else if ("Make".equals(p.tagName)) {
				String v = metadata.get("Make");
				if (null == v || v.length() < 1) {
					v = "NoMake";
				}
				sb.append(v);
			} else if ("Orientation".equals(p.tagName)) {
				// 'Orientation' -> 'Right side, top (Rotate 90 CW)'
				String v = metadata.get("Orientation");
				if (null == v || v.length() < 1) {
					v = "NoOrientation";
				}
				sb.append(v);
			} else if ("SkipExisting".equals(p.tagName)) {
				skipExisting = true;
			} else if ("Unique".equals(p.tagName)) {
				sb.append("_Unique_");
				checkUnique = true;
			} else if ("Mimetype".equals(p.tagName)) {
				String mt = getMimeType(metadata);
				sb.append(mt);
			} else if ("GPS".equals(p.tagName)) {
				GeoPoint gp = getGPS(metadata);
				if (null != gp) {
					sb.append(gp.toString());
				} else {
					sb.append("NoGPS");
				}
			} else if ("Longitude".equals(p.tagName)) {
				GeoPoint gp = getGPS(metadata);
				if (null != gp) {
					sb.append(String.format("%.5f", gp.lng_deg));
				} else {
					sb.append("NoLongitude");
				}
			} else if ("Latitude".equals(p.tagName)) {
				GeoPoint gp = getGPS(metadata);
				if (null != gp) {
					sb.append(String.format("%.5f", gp.lat_deg));
				} else {
					sb.append("NoLatitude");
				}
			} else if ("Filename".equals(p.tagName)) {
				String base = f.getName();
				String fn = base.replaceFirst("[.][^.]+$", "");
				sb.append(fn);
			} else if ("Extension".equals(p.tagName)) {
				String base = f.getName();
				sb.append(getExtension(base));
			} else if ("CreationDate".equals(p.tagName)) {
				String v = metadata.get("Creation-Date");
				if (null == v || v.length() < 1) {
					v = "NoDate";
				} else {
					try {
						Date d = parser.parse(v);
						DateFormat fmt = new SimpleDateFormat(p.format);
						v = fmt.format(d);
					} catch (ParseException e) {
						v = "DateNotParseable";
						e.printStackTrace();
					}
				}
				sb.append(v);
			} else if ("Width".equals(p.tagName)) {
				sb.append(getWidth(metadata));
			} else if ("Height".equals(p.tagName)) {
				sb.append(getHeight(metadata));
			} else {
				sb.append("undef");
			}
		}

		res = sb.toString();
		
		res = fixHashedFilename(res);
		res = fixFilenameRepetition(res);

		if (skipExisting) {
			File t1 = new File(res);
			if (t1.exists()) {
				return null; // signal skip
			}
		}

		if (checkUnique) {

			int cnt = 10;

			String r1 = res.replaceFirst("_Unique_", "");
			File t1 = new File(r1);

			if (!t1.exists()) {
				res = r1;
			} else {
				while (t1.exists()) {
					r1 = res.replaceFirst("_Unique_", Integer.toString(cnt));
					t1 = new File(r1);
				}
				res = r1;
			}
		}


		return res;
	}

	public String getExtension(String fn) {
		String res = "";
		Matcher m = reFileExtension.matcher(fn);
		if (m.find()) {
			res = m.group(1);
		}
		return res;
	}

	public String getExtension(File f) {
		return getExtension(f.getName());
	}

	public String getMimeType(Metadata md) {
		String mimeType = md.get("Content-Type");
		return mimeType;
	}
}
