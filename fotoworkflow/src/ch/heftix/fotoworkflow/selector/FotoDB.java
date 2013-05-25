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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.resizers.configurations.Dithering;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.tika.metadata.Metadata;
import org.sqlite.SQLiteJDBCLoader;

import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import ch.heftix.fotoworkflow.selector.json.Payload;

public class FotoDB {

	private Connection conn = null;

	QueryRunner fotoExistsQR = new QueryRunner();
	QueryRunner thumbnailExistsQR = new QueryRunner();

	ResultSetHandler<Integer> fotoExistsRSH = null;
	ResultSetHandler<Thumbnail> thumbnailExistsRSH = null;

	TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	Pattern repetionFinder = Pattern.compile("(.+?)_\\1+.*");

	// M: Month in year, H: Hour in day (0-23), m: Minute in hour
	String pCreationDate = "@{CreationDate: yyyy-MM-dd'T'HHmm}";
	String pModel = "@{Model}";

	String fotoattrs = "path,noteid,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note";

	public class Thumbnail {
		public String path;
		byte[] image;
		String mimeType;
		int height;
	}

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
			sb.append("}");
			return sb.toString();
		}
	}

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

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set " + k + "=?" + " where path=?";
		qr.update(conn, sql, v, path);
	}

	public boolean existsFoto(String path) throws SQLException {
		boolean res = false;
		int cnt = fotoExistsQR.query(conn, "select count(path) from foto where path=?", fotoExistsRSH, path);
		if (0 != cnt) {
			res = true;
		}
		return res;
	}

	public void insertFoto(File f) throws IOException, SQLException {
		insertFoto(conn, f);
	}

	public void insertFoto(Connection c, File f) throws IOException, SQLException {

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
		String lng = mdh.format(f, md, "@{Longitude}");
		String lat = mdh.format(f, md, "@{Latitude}");
		String o = mdh.format(f, md, "@{Orientation}");
		int w = mdh.getWidth(md);
		int h = mdh.getHeight(md);

		QueryRunner qr = new QueryRunner();
		String sql = "insert into foto (path,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation) values (?,?,?,?,?,?,?,?)";
		qr.update(c, sql, f.getAbsolutePath(), mt, cd, w, h, make, model, lng, lat, o);

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

		if (searchTerm.length() < 1) {
			res = qr.query(conn, "select " + fotoattrs + " from foto order by creationdate", rsh);
		} else {
			res = qr.query(conn, "select " + fotoattrs + " from foto where foto match ? order by creationdate", rsh,
					searchTerm);
		}

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
		return f;
	}

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
			// note("i rotate(%d) due to %s for %s", rotate, f.orientation, f.path);
			if( 90 == rotate || 270 == rotate ) {
				height = (int)Math.round(height / 1.4);
			}
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

	private void insertThumbnail(Thumbnail t) throws SQLException {
		QueryRunner qr = new QueryRunner();
		qr.update(conn, "insert into thumbnail (path, image, height, mimetype) values (?,?,?,?)", t.path, t.image,
				t.height, t.mimeType);
		// note(":) inserted thumbnail for %s", t.path);
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
		org.apache.commons.dbutils.ResultSetHandler<String> rsh = new org.apache.commons.dbutils.ResultSetHandler<String>() {
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

}
