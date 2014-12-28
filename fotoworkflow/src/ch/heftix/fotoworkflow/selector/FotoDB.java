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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.resizers.configurations.Dithering;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.tika.metadata.Metadata;
import org.sqlite.SQLiteJDBCLoader;

import ch.heftix.fotoworkflow.mover.GeoPoint;
import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;

public class FotoDB {

	private Connection conn = null;

	QueryRunner fotoExistsQR = new QueryRunner();
	QueryRunner thumbnailExistsQR = new QueryRunner();
	QueryRunner countFotosQR = new QueryRunner();

	ResultSetHandler<Integer> fotoExistsRSH = null;
	ResultSetHandler<Integer> countFotosRSH = null;
	ResultSetHandler<Thumbnail> thumbnailExistsRSH = null;

	TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	Pattern repetionFinder = Pattern.compile("(.+?)_\\1+.*");

	// M: Month in year, H: Hour in day (0-23), m: Minute in hour
	String pCreationDate = "@{CreationDate: yyyy-MM-dd'T'HHmm}";
	String pModel = "@{Model}";

	String fotoattrs = "path,noteid,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note,phash,isMissing,stamp";

	private boolean excludeDocumentary = true;

	SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	protected FotoDB() throws Exception {

		Class.forName("org.sqlite.JDBC");

		if (!SQLiteJDBCLoader.isNativeMode()) {
			note("cannot use sqlite native mode, falling back to (slower) pure-java mode");
		}

		try {
			conn = getConnection(); // create a database connection

			checkCreateSchema();

			Statement stmt = conn.createStatement();
			stmt.execute("PRAGMA cache_size=100000");
			stmt.close();

			fotoExistsRSH = new ResultSetHandler<Integer>() {
				public Integer handle(ResultSet rs) throws SQLException {
					if (rs.next()) {
						return rs.getInt(1);
					}
					return 0;
				}
			};

			countFotosRSH = fotoExistsRSH;

			thumbnailExistsRSH = new ResultSetHandler<Thumbnail>() {
				public Thumbnail handle(ResultSet rs) throws SQLException {
					if (rs.next()) {
						Thumbnail res = new Thumbnail();
						res.path = rs.getString(1);
						res.image = rs.getBytes(2);
						res.height = rs.getInt(3);
						res.mimeType = rs.getString(4);
						return res;
					}
					return null;
				}
			};

		} catch (SQLException e) {
			note("cannot connect to DB: %s", e.getMessage(), e);
		}
	}

	protected Connection getConnection() throws SQLException {
		String home = System.getProperty("user.home");
		Connection c = DriverManager.getConnection("jdbc:sqlite:" + home + "/.foto-thumbnails.db");
		return c;
	}

	public void storeInfo(String path, String k, String v) throws SQLException {

		List<String> allowedKeys = Arrays.asList("note", "orientation", "category", "noteId");

		if (null == path || null == k || null == v) {
			return;
		}

		if (!allowedKeys.contains(k)) {
			return;
		}

		if (v.length() < 1) {
			return;
		}

		String stamp = getStamp();

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set " + k + "=?,stamp=?" + " where path=?";
		qr.update(conn, sql, v, stamp, path);
	}

	private String getInfo(String path, String key) throws SQLException {

		if (null == path || null == key) {
			return null;
		}

		QueryRunner qr = new QueryRunner();
		String sql = String.format("select %s from foto where path=?", key);
		ResultSetHandler<String> rsh = new ResultSetHandler<String>() {
			public String handle(ResultSet rs) throws SQLException {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		};
		String res = qr.query(conn, sql, rsh, path);
		return res;
	}

	public String getNote(String path) throws SQLException {
		return getInfo(path, "note");
	}

	public void appendNote(String path, String v) throws SQLException {

		if (null == path || null == v) {
			return;
		}

		if (v.length() < 1) {
			return;
		}

		String stamp = getStamp();

		String note = getNote(path);
		if (null == note) {
			note = v;
		} else {
			note = String.format("%s, %s", note, v);
		}

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set note=?,stamp=?" + " where path=?";
		qr.update(conn, sql, note, stamp, path);
	}

	public synchronized String getStamp() {
		// String user = System.getProperty("user.name");
		// user = UIUtil.removeSpecial(user);
		Date now = new Date();
		String znow = timestampFormatter.format(now);
		// String stamp = String.format("%s %s", znow, user);
		// return stamp;
		return znow;
	}

	public boolean existsFoto(String path) throws SQLException {
		boolean res = false;
		int cnt = fotoExistsQR.query(conn, "select count(path) from foto where path=?", fotoExistsRSH, path);
		if (0 != cnt) {
			res = true;
		}
		return res;
	}

	public void insertFoto(File f, String note) throws IOException, SQLException {
		insertFoto(conn, f, note);
	}

	private int getIntFromDateString(String str) {
		return getIntFromDateString(str, 0);
	}

	private int getIntFromDateString(String str, int def) {

		int res = def;
		if (null == str || str.length() < 1 || "NoDate".equals(str)) {
			return def;
		}
		try {
			res = Integer.parseInt(str);
		} catch (NumberFormatException e) {
			res = def;
		}
		return res;
	}

	public void insertFoto(Connection c, File f, String note) throws IOException, SQLException {

		if (null == f) {
			return;
		}

		if (!f.exists()) {
			return;
		}

		// check existence
		boolean cnt = existsFoto(f.getAbsolutePath());
		if (cnt) {
			return;
		}

		String ext = mdh.getExtension(f);
		Matcher m = reExtensionFilter.matcher(ext);
		if (!m.matches()) {
			return;
		}

		Metadata md = mdh.readMetadata(f);

		String mt = mdh.format(f, md, "@{Mimetype}");
		String model = mdh.format(f, md, "@{Model}");
		String make = mdh.format(f, md, "@{Make}");
		String cd = mdh.format(f, md, "@{CreationDate: yyyy-MM-dd'T'HHmm}");
		int year = getIntFromDateString(mdh.format(f, md, "@{CreationDate: yyyy}"));
		int month = getIntFromDateString(mdh.format(f, md, "@{CreationDate: MM}"));
		int day = getIntFromDateString(mdh.format(f, md, "@{CreationDate: dd}"));
		int hour = getIntFromDateString(mdh.format(f, md, "@{CreationDate: HH}"));
		int minute = getIntFromDateString(mdh.format(f, md, "@{CreationDate: mm}"));
		String lng = mdh.format(f, md, "@{Longitude}");
		String lat = mdh.format(f, md, "@{Latitude}");
		String o = mdh.format(f, md, "@{Orientation}");
		int w = mdh.getWidth(md);
		int h = mdh.getHeight(md);
		String phash = Foto.defaultHash;
		int isMissing = 0;
		String stamp = getStamp();

		StringBuffer sb = new StringBuffer(1024);
		sb.append("insert into foto (path,mimetype,creationdate");
		sb.append(",year,month,day,hour,minute");
		sb.append(",w,h,make,model,geo_long,geo_lat,orientation");
		if (null != note && note.length() > 1) {
			sb.append(",note");
		}
		sb.append(", phash, isMissing, stamp)");
		if (null != note && note.length() > 1) {
			sb.append(" values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
		} else {
			sb.append(" values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
		}
		String sql = sb.toString();

		QueryRunner qr = new QueryRunner();

		if (null != note && note.length() > 1) {
			qr.update(c, sql, f.getAbsolutePath(), mt, cd, year, month, day, hour, minute, w, h, make, model, lng, lat,
					o, note, phash, isMissing, stamp);
		} else {
			qr.update(c, sql, f.getAbsolutePath(), mt, cd, year, month, day, hour, minute, w, h, make, model, lng, lat,
					o, phash, isMissing, stamp);
		}

	}

	public void updateFoto(File f, String note) throws IOException, SQLException {
		updateFoto(conn, f, note);
	}

	/**
	 * update data in foto index from file
	 * 
	 * @param c
	 *            DB connection
	 * @param f
	 *            file to take data from
	 * @param note
	 *            import note
	 * @throws IOException
	 * @throws SQLException
	 */
	public void updateFoto(Connection c, File f, String note) throws IOException, SQLException {

		if (null == f) {
			return;
		}

		if (!f.exists()) {
			return;
		}

		// check existence
		boolean cnt = existsFoto(f.getAbsolutePath());
		if (!cnt) {
			return;
		}

		String ext = mdh.getExtension(f);
		Matcher m = reExtensionFilter.matcher(ext);
		if (!m.matches()) {
			return;
		}

		Metadata md = mdh.readMetadata(f);

		String mt = mdh.format(f, md, "@{Mimetype}");
		String model = mdh.format(f, md, "@{Model}");
		String make = mdh.format(f, md, "@{Make}");
		String cd = mdh.format(f, md, "@{CreationDate: yyyy-MM-dd'T'HHmm}");
		String lng = mdh.format(f, md, "@{Longitude}");
		String lat = mdh.format(f, md, "@{Latitude}");
		String o = mdh.format(f, md, "@{Orientation}");
		int w = mdh.getWidth(md);
		int h = mdh.getHeight(md);
		String phash = Foto.defaultHash;
		int year = getIntFromDateString(mdh.format(f, md, "@{CreationDate: yyyy}"));
		int month = getIntFromDateString(mdh.format(f, md, "@{CreationDate: MM}"));
		int day = getIntFromDateString(mdh.format(f, md, "@{CreationDate: dd}"));
		int hour = getIntFromDateString(mdh.format(f, md, "@{CreationDate: HH}"));
		int minute = getIntFromDateString(mdh.format(f, md, "@{CreationDate: mm}"));

		QueryRunner qr = new QueryRunner();

		StringBuffer sb = new StringBuffer(1024);
		sb.append("update foto set mimetype=?,creationdate=?,w=?,h=?,make=?,model=?");
		sb.append(",geo_long=?,geo_lat=?,orientation=?,phash=?,isMissing=0");
		sb.append(",year=?,month=?,day=?,hour=?,minute=?");
		if (null != note && note.length() > 1) {
			sb.append(",note=?");
		}
		sb.append(" where path=?");
		String sql = sb.toString();

		if (null != note && note.length() > 1) {
			qr.update(c, sql, mt, cd, w, h, make, model, lng, lat, o, phash, year, month, day, hour, minute, note,
					f.getAbsolutePath());
		} else {
			qr.update(c, sql, mt, cd, w, h, make, model, lng, lat, o, phash, year, month, day, hour, minute,
					f.getAbsolutePath());
		}

	}

	protected void updateMetadata(Connection c, String path) throws IOException, SQLException {

		File f = new File(path);
		if (!f.exists()) {
			return;
		}

		Metadata md = mdh.readMetadata(f);

		String cd = mdh.format(f, md, "@{CreationDate: yyyy-MM-dd'T'HHmm}");

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set creationdate=? where path=?";
		qr.update(c, sql, cd, path);
	}

	public void updatePHashs() throws IOException, SQLException {

		String sql = "select path from foto where phash='" + Foto.defaultHash + "'";

		ResultSetHandler<List<String>> rsh = new ResultSetHandler<List<String>>() {
			public List<String> handle(ResultSet rs) throws SQLException {
				List<String> res = new ArrayList<String>();
				while (rs.next()) {
					res.add(rs.getString(1));
				}
				return res;
			}
		};

		QueryRunner qr = new QueryRunner();
		List<String> paths = qr.query(conn, sql, rsh);

		int cnt = 0;

		for (String p : paths) {
			updatePHash(p);
			cnt++;
			if (cnt % 1000 == 0) {
				System.out.println(String.format("cnt: %d", cnt));
			}
		}
	}

	public void updatePHash(String path) throws IOException, SQLException {
		updatePHash(this.conn, path);
	}

	public void updatePHash(Connection c, String path) throws IOException, SQLException {

		if (null == path) {
			return;
		}

		if (path.length() < 1) {
			return;
		}

		File f = new File(path);
		if (!f.exists()) {
			return;
		}
		PHash ph = new PHash();
		FileInputStream fis = new FileInputStream(f);
		String h = Foto.defaultHash;
		try {
			h = ph.getHash(fis);
		} catch (Exception e) {
			// TODO
			e.printStackTrace();
		}

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set phash=? where path=?";
		qr.update(c, sql, h, path);
	}

	protected void fixFilenameRepetitions(Connection c, String path) throws IOException, SQLException {

		File f = new File(path);
		if (!f.exists()) {
			return;
		}

		String fn = f.getName();
		Matcher matcher = repetionFinder.matcher(fn);
		String repeated = matcher.matches() ? matcher.group(1) : null;

		// System.out.println("repeated:" + repeated);

		boolean dryRun = false;

		if (null != repeated) {

			String p2 = path.replaceFirst(repeated + "_", "");

			if (dryRun) {
				note("would move %s to %s", path, p2);
			} else {

				File nf = new File(p2);
				nf.mkdirs();

				Path source = Paths.get(path);
				Path target = Paths.get(p2);
				try {
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
					note("  --> %s", p2);
					QueryRunner qr = new QueryRunner();
					String sql = "update foto set path=? where path=?";
					qr.update(c, sql, p2, path);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public List<Foto> searchFoto(String searchTerm, int page, int pagesize) throws SQLException {

		List<Foto> res = null;
		if (null == searchTerm) {
			searchTerm = "";
		}

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where isMissing=0");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		if (searchTerm.length() > 0) {
			sb.append(" and foto match ?");
		}
		sb.append(" order by creationdate");
		String sql = sb.toString();

		if (searchTerm.length() < 1) {
			res = searchFotoBySQL(page, pagesize, sql);
		} else {
			res = searchFotoBySQL(page, pagesize, sql, searchTerm);
		}

		return res;
	}

	public List<Foto> searchSimilarFoto(String path, int page, int pagesize) throws SQLException {

		List<Foto> res = null;

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where path in (");
		sb.append(" select distinct p from (");
		sb.append(" select p2 p, d from distance where p1 = ? and d < 15");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		sb.append(" union all");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		sb.append(" select p1 p, d from distance where p2 = ? and d < 15");
		sb.append(" union all");
		sb.append(" select path, 0 from foto where path=?");
		sb.append(") order by d asc");
		sb.append(")");

		String sql = sb.toString();

		res = searchFotoBySQL(page, pagesize, sql, path, path, path);

		return res;
	}

	/**
	 * search fotos based on date of reference foto
	 * 
	 * @param path
	 *            primary key of reference foto
	 * @param page
	 *            which page to return
	 * @param pagesize
	 *            current page size
	 * @return search result (list of fotos)
	 * @throws SQLException
	 */
	public List<Foto> searchCloseDate(String path, int page, int pagesize) throws SQLException {
		List<Foto> res = new ArrayList<Foto>();
		res = searchCloseDate(path, page, pagesize, 3);
		// int nDays = 2;
		// int cnt = 0;
		// while (res.size() < pagesize * 2 && cnt < 7) {
		// res = searchCloseDate(path, page, pagesize, nDays);
		// nDays *= 2;
		// cnt++;
		// System.out.println(String.format("nDays: %d cnt %d size %d", nDays,
		// cnt, res.size()));
		//
		// }
		return res;
	}

	public List<Foto> searchCloseDate(String path, int page, int pagesize, int nDays) throws SQLException {

		Foto ref = getFoto(path);
		String cd = ref.creationdate.substring(0, 10);

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where creationdate between");
		sb.append(String.format(" date(?,'-%d days') and date(?,'+%d days')", nDays, nDays));
		sb.append(" and isMissing=0");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		sb.append(" order by creationdate");

		String sql = sb.toString();

		List<Foto> res = searchFotoBySQL(page, pagesize, sql, cd, cd);
		return res;
	}

	public List<Foto> searchCloseLocation(String path, int page, int pagesize) throws Exception {

		List<Foto> res = new ArrayList<Foto>();

		Foto ref = getFoto(path);
		if ("NoLongitude".equals(ref.geo_long)) {
			throw new Exception("This foto has no geo location to use as reference");
		}

		double lng = Double.parseDouble(ref.geo_long);
		double lat = Double.parseDouble(ref.geo_lat);

		GeoPoint refpoint = new GeoPoint();
		refpoint.setLng(lng);
		refpoint.setLat(lat);

		res = searchCloseLocation(page, pagesize, refpoint);

		GeoPoint gp = new GeoPoint();
		gp.setLng(lng);
		gp.setLat(lat);

		// calculate distances
		for (Foto f : res) {
			if (!"NoLongitude".equals(f.geo_long)) {
				GeoPoint g2 = new GeoPoint();
				g2.setLng(f.geo_long);
				g2.setLat(f.geo_lat);
				f.tmpKmFrom = g2.kmFrom(gp);
			}
		}

		Collections.sort(res, new Comparator<Foto>() {
			public int compare(Foto f1, Foto f2) {
				if (f2.tmpKmFrom < f1.tmpKmFrom) {
					return 1;
				} else if (f2.tmpKmFrom > f1.tmpKmFrom) {
					return -1;
				}
				return 0;
			}
		});

		if (page < 1) {
			page = 1;
		}

		if (pagesize < 1) {
			pagesize = 6;
		}

		List<Foto> r2 = new ArrayList<Foto>();
		int start = (page - 1) * pagesize;
		for (int i = start; i < start + pagesize && i < res.size(); i++) {
			r2.add(res.get(i));
		}

		return r2;
	}

	public List<Foto> searchCloseLocation(int page, int pagesize, GeoPoint ref) throws Exception {

		List<Foto> res = new ArrayList<Foto>();

		// System.out.println(String.format("ref: %s", ref));

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where");
		sb.append(" cast(geo_long as real) between ? and ?");
		sb.append(" and cast(geo_lat as real) between ? and ?");
		sb.append(" and isMissing=0");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}

		String sql = sb.toString();

		int delta = 5;
		res = searchFotoBySQL(page, pagesize, sql, ref.lng_deg - delta, ref.lng_deg + delta, ref.lat_deg - delta,
				ref.lat_deg + delta);
		// int cnt = 0;
		// while (res.size() < pagesize * 2 && cnt < 7) {
		// res = searchFotoBySQL(page, pagesize, sql, ref.lng_deg - delta,
		// ref.lng_deg + delta, ref.lat_deg - delta,
		// ref.lat_deg + delta);
		// delta *= 2;
		// cnt++;
		// System.out.println(String.format("delta: %d cnt %d size %d", delta,
		// cnt, res.size()));
		// }

		return res;
	}

	public int countFotos() throws SQLException {
		int cnt = countFotosQR.query(conn, "select count(path) from foto", countFotosRSH);
		return cnt;
	}

	public List<Foto> feelLucky(String searchTerm, int page, int pagesize) throws Exception {

		// System.out.println(String.format("feel lucky: page %d", page));
		List<Foto> res = searchFoto(searchTerm, page, pagesize);

		return res;
	}

	private List<Foto> searchFotoBySQL(int page, int pagesize, String sql, Object... args) throws SQLException {

		List<Foto> res = null;

		if (page < 1) {
			page = 1;
		}

		if (pagesize < 1) {
			pagesize = 6;
		}

		final int p = page;
		final int ps = pagesize;

		ResultSetHandler<List<Foto>> rsh = new ResultSetHandler<List<Foto>>() {
			public List<Foto> handle(ResultSet rs) throws SQLException {
				int cnt = 0;
				List<Foto> res = new ArrayList<Foto>();
				while (cnt < (p - 1) * ps) {
					rs.next();
					cnt++;
				}
				cnt = 0;
				while (rs.next() && cnt < ps) {
					Foto f = rs2Foto(rs);
					res.add(f);
					cnt++;
				}
				return res;
			}
		};

		QueryRunner qr = new QueryRunner();
		res = qr.query(conn, sql, rsh, args);

		return res;
	}

	private List<Foto> unboundSearchFotoBySQL(String sql, Object... args) throws SQLException {

		List<Foto> res = null;

		ResultSetHandler<List<Foto>> rsh = new ResultSetHandler<List<Foto>>() {
			public List<Foto> handle(ResultSet rs) throws SQLException {
				List<Foto> res = new ArrayList<Foto>();
				while (rs.next()) {
					Foto f = rs2Foto(rs);
					res.add(f);
				}
				return res;
			}
		};

		QueryRunner qr = new QueryRunner();
		res = qr.query(conn, sql, rsh, args);

		return res;
	}

	private Foto rs2Foto(ResultSet rs) throws SQLException {
		// "path,noteid,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note";
		Foto f = new Foto();
		f.path = rs.getString(1);
		f.noteId = rs.getString(2);
		f.mimeType = rs.getString(3);
		f.creationdate = rs.getString(4);
		f.w = rs.getInt(5);
		f.h = rs.getInt(6);
		f.make = rs.getString(7);
		f.model = rs.getString(8);
		f.geo_long = rs.getString(9);
		f.geo_lat = rs.getString(10);
		f.orientation = rs.getString(11);
		f.category = rs.getString(12);
		f.note = rs.getString(13);
		f.phash = rs.getString(14);
		f.isMissing = rs.getInt(15);
		f.stamp = rs.getString(16);
		return f;
	}

	/**
	 * retrieve foto from
	 * 
	 * @param path
	 *            primary key of foto
	 * @return @see Foto
	 * @throws SQLException
	 */
	public Foto getFoto(String path) throws SQLException {

		QueryRunner qr = new QueryRunner();
		ResultSetHandler<Foto> rsh = new ResultSetHandler<Foto>() {
			public Foto handle(ResultSet rs) throws SQLException {
				Foto res = null;
				if (rs.next()) {
					res = rs2Foto(rs);
				}
				return res;
			}
		};
		Foto res = qr.query(conn, "select " + fotoattrs + " from foto where path=?", rsh, path);

		QueryRunner q2 = new QueryRunner();
		String z = getStamp();
		q2.update(conn, "update foto set viewed_last=? where path=?", z, path);

		return res;
	}

	public void invalidateThumbnail(String path) throws IOException, SQLException {

		if (null == path) {
			return;
		}

		QueryRunner q2 = new QueryRunner();
		q2.update(conn, "delete from thumbnail where path=?", path);

		boolean debug = false;

		if (debug) {

			ResultSetHandler<Integer> rsh = new ResultSetHandler<Integer>() {
				public Integer handle(ResultSet rs) throws SQLException {
					Integer res = null;
					if (rs.next()) {
						res = rs.getInt(1);
					}
					return res;
				}
			};

			int cnt = q2.query(conn, "select count(*) from thumbnail where path=?", rsh, path);
			note("i FotoDB.invalidateThumbnail: now have %d thumnbails for %s", cnt, path);
		}
	}

	public Thumbnail getThumbnail(File f, int height) throws IOException, SQLException {

		Thumbnail res = null;

		if (null == f) {
			return res;
		}

		// System.out.println(String.format("D getThumbnail %s %d", f.getName(),
		// height));

		// check DB first
		res = thumbnailExistsQR.query(conn,
				"select path,image,height,mimetype from thumbnail where path=? and height=?", thumbnailExistsRSH,
				f.getAbsoluteFile(), height);

		if (null == res) { // thumbnail not yet cached

			Foto foto = getFoto(f.getAbsolutePath());

			if (foto.h < height) { // for small images, do not calculate
				res = new Thumbnail();
				res.mimeType = foto.mimeType;
				res.image = slurp(f);
				res.height = foto.h;
				res.path = foto.path;

			} else { // create and cache

				res = createThumbnail(foto, height);

				if (null != res.image) {
					// System.out.println(String.format("D ... storing cache %s %d",
					// f.getName(), height));
					insertThumbnail(res);
				}
			}
		}

		return res;
	}

	private Thumbnail createThumbnail(Foto f, int height) throws FileNotFoundException, IOException {

		// note(":) creating thumbnail for %s", f.path);

		Thumbnail res = new Thumbnail();
		res.path = f.path;
		res.mimeType = f.mimeType;

		int rotate = 0;
		if (null != f.orientation && f.orientation.length() > 1) {
			if (f.orientation.contains("90 CW")) {
				rotate = 90;
			}
			if (f.orientation.contains("270 CW")) {
				rotate = 270;
			}
			if (f.orientation.contains("180")) {
				// Bottom, right side (Rotate 180)
				rotate = 180;
			}
		}

		InputStream is = new FileInputStream(f.path);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		if (rotate != 0) {
			// note("i rotate(%d) due to %s for %s", rotate, f.orientation,
			// f.path);
			// if( 90 == rotate || 270 == rotate ) {
			// height = (int)Math.round(height / 1.45);
			// }
			Thumbnails.of(is).useExifOrientation(false).dithering(Dithering.ENABLE).outputQuality(.9)
					.size(height, height).rotate(rotate).toOutputStream(baos);
		} else {
			Thumbnails.of(is).useExifOrientation(false).dithering(Dithering.ENABLE).outputQuality(.9)
					.size(height, height).toOutputStream(baos);
		}

		is.close();
		res.image = baos.toByteArray();
		res.height = height;
		baos.close();
		baos = null;

		return res;
	}

	// private void insertThumbnail(Thumbnail t) throws SQLException {
	// toBeCachedThumbnails.add(t);
	// if (toBeCachedThumbnails.size() >= 8) {
	// QueryRunner qr = new QueryRunner();
	// while (!toBeCachedThumbnails.isEmpty()) {
	// Thumbnail tn = toBeCachedThumbnails.remove();
	// // note("inserting %s", tn.path);
	// qr.update(conn,
	// "insert into thumbnail (path, image, height, mimetype) values (?,?,?,?)",
	// tn.path,
	// tn.image, tn.height, tn.mimeType);
	// }
	// }
	// }

	private void insertThumbnail(Thumbnail t) throws SQLException {
		QueryRunner qr = new QueryRunner();
		qr.update(conn, "insert into thumbnail (path, image, height, mimetype) values (?,?,?,?)", t.path, t.image,
				t.height, t.mimeType);

	}

	private byte[] slurp(File f) throws IOException {
		byte[] res = new byte[0];
		if (null == f || !f.exists() || !f.isFile()) {
			return res;
		}
		res = new byte[(int) f.length()];
		InputStream is = new FileInputStream(f);
		is.read(res);
		is.close();
		return res;
	}

	private void runSQLScript(final Statement stmt, final String filename) throws IOException, SQLException {

		note("running DB script: " + filename);

		InputStream is = FotoDB.class.getResourceAsStream(filename);
		BufferedReader in = new BufferedReader(new InputStreamReader(is));

		String str;
		StringBuffer sql = new StringBuffer(256);

		while ((str = in.readLine()) != null) {
			str = str.trim();
			// line may contain comment
			int cmtPos = str.indexOf("--");
			if (cmtPos >= 0) {
				str = str.substring(0, cmtPos);
			}
			if (str.length() > 0) {
				// after cutting comment, we still have some text
				sql.append(" ");
				sql.append(str);
				if (str.endsWith(";")) {
					// end of statement.execute
					String tmp = sql.toString();
					tmp = tmp.trim();
					note("sql: '" + tmp + "'");
					stmt.execute(tmp);
					sql = new StringBuffer(256);
				}
			}
		}
		in.close();
		is.close();
	}

	/**
	 * run update schema update script and return new DB version as queried from
	 * updated DB
	 */
	private String updateSchema(final String updateScript) throws SQLException, IOException {

		boolean ac = conn.getAutoCommit();

		conn.setAutoCommit(true);

		Statement stmt = conn.createStatement();
		runSQLScript(stmt, updateScript);

		stmt.close();
		stmt = null;

		conn.setAutoCommit(ac);

		String v = getVersion();

		return v;
	}

	private void checkCreateSchema() throws SQLException, IOException {

		String version = getVersion();
		if (!"1".equals(version)) {
			updateSchema("foto-thumbnails-db.sql");
		}
	}

	private String getVersion() {

		QueryRunner qr = new QueryRunner();
		ResultSetHandler<String> rsh = new ResultSetHandler<String>() {
			public String handle(ResultSet rs) throws SQLException {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		};
		String version = null;
		try {
			version = qr.query(conn, "select v from conf where k='version'", rsh);
		} catch (SQLException e) {
			note("DB error: %s", e);
		}
		return version;
	}

	public String getConf(String key) {

		QueryRunner qr = new QueryRunner();
		ResultSetHandler<String> rsh = new ResultSetHandler<String>() {
			public String handle(ResultSet rs) throws SQLException {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		};
		String version = null;
		try {
			version = qr.query(conn, "select v from conf where k=?", rsh, key);
		} catch (SQLException e) {
			note("DB error: %s", e);
		}
		return version;
	}

	public void setConf(String key, String val) {

		String c = getConf(key);
		QueryRunner qr = new QueryRunner();
		if (null == c) {
			try {
				qr.update(conn, "insert into conf (k,v) values (?,?)", key, val);
			} catch (SQLException e) {
				note("DB cannot insert conf %s (%s): %s", key, val, e);
			}
		} else {
			try {
				qr.update(conn, "update conf set v=? where k=?", val, key);
			} catch (SQLException e) {
				note("DB cannot update conf %s (%s): %s", key, val, e);
			}
		}
	}

	public static void note(String fmt, Object... args) {
		System.out.println(String.format(fmt, args));
	}

	public void setExcludeDocumentary(boolean val) {
		excludeDocumentary = val;
	}

	public boolean toggleExcludeDocumentary() {
		if (excludeDocumentary) {
			excludeDocumentary = false;
		} else {
			excludeDocumentary = true;
		}
		return excludeDocumentary;
	}

	public void updateExif(Foto f) {

		File exiftool = new File("/usr/bin/exiftool");
		if (!exiftool.exists()) {
			return;
		}

		File file = new File(f.path);

		ProcessBuilder b = null;

		List<String> exifToolArgs = new ArrayList<String>();
		exifToolArgs.add("/usr/bin/exiftool");
		exifToolArgs.add("-F");

		boolean hasWork = false;

		if (null != f.note && f.note.length() > 1) {
			exifToolArgs.add("-UserComment=" + f.note);
			hasWork = true;
		}
		if (null != f.category && f.category.length() > 1) {
			exifToolArgs.add("-ImageDescription=" + f.category);
			hasWork = true;
		}
		exifToolArgs.add(file.getAbsolutePath());

		if (hasWork) {
			b = new ProcessBuilder(exifToolArgs);

			try {
				Process p = b.start();

				InputStreamReader isr = new InputStreamReader(p.getInputStream());
				BufferedReader br = new BufferedReader(isr);
				String line;

				while ((line = br.readLine()) != null) {
					System.out.println(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
