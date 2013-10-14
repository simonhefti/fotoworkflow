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

	String fotoattrs = "path,noteid,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note,phash,isMissing";

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

	public void insertFoto(File f, String note) throws IOException, SQLException {
		insertFoto(conn, f, note);
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
		String lng = mdh.format(f, md, "@{Longitude}");
		String lat = mdh.format(f, md, "@{Latitude}");
		String o = mdh.format(f, md, "@{Orientation}");
		int w = mdh.getWidth(md);
		int h = mdh.getHeight(md);
		String phash = Foto.defaultHash;
		int isMissing = 0;

		QueryRunner qr = new QueryRunner();

		if (null != note && note.length() > 1) {
			String sql = "insert into foto (path,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation, note, phash, isMissing) values (?,?,?,?,?,?,?,?,?,?,?,?,?)";
			qr.update(c, sql, f.getAbsolutePath(), mt, cd, w, h, make, model, lng, lat, o, note, phash, isMissing);
		} else {
			String sql = "insert into foto (path,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation, phash, isMissing) values (?,?,?,?,?,?,?,?,?,?,?,?)";
			qr.update(c, sql, f.getAbsolutePath(), mt, cd, w, h, make, model, lng, lat, o, phash, isMissing);
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
				List<String> res = new ArrayList<>();
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

		if (searchTerm.length() < 1) {
			String sql = "select " + fotoattrs + " from foto where isMissing=0 order by creationdate";
			res = searchFotoBySQL(page, pagesize, sql);
		} else {
			String sql = "select " + fotoattrs + " from foto where isMissing=0 and foto match ? order by creationdate";
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
		sb.append(" union all");
		sb.append(" select p1 p, d from distance where p2 = ? and d < 15");
		sb.append(" union all");
		sb.append(" select path, 0 from foto where path=?");
		sb.append(") order by d asc");
		sb.append(")");

		String sql = sb.toString();

		res = searchFotoBySQL(page, pagesize, sql, path, path, path);

		return res;
	}

	public List<Foto> searchCloseDate(String path, int page, int pagesize) throws SQLException {

		Foto ref = getFoto(path);
		String cd = ref.creationdate.substring(0, 10);

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where creationdate between");
		sb.append(" date(?,'-5 days') and date(?,'+5 days')");
		sb.append(" and isMissing=0");

		String sql = sb.toString();

		List<Foto> res = searchFotoBySQL(page, pagesize, sql, cd, cd);
		return res;
	}

	public List<Foto> searchCloseLocation(String path, int page, int pagesize) throws SQLException {

		List<Foto> res = new ArrayList<Foto>();
		
		Foto ref = getFoto(path);
		if( "NoLongitude".equals(ref.geo_long)) {
			return res;
		}
		
		double lng = Double.parseDouble(ref.geo_long);
		double lat = Double.parseDouble(ref.geo_lat);
		
		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where");
		// cast(geo_long as real) between 4 and 8 and cast(geo_lat as real) between 43 and 50;
		sb.append(" cast(geo_long as real) between ? and ?");
		sb.append(" and cast(geo_lat as real) between ? and ?");
		sb.append(" and isMissing=0");

		String sql = sb.toString();

		res = searchFotoBySQL(page, pagesize, sql, lng - 3, lng + 3, lat -3, lat + 3);
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
