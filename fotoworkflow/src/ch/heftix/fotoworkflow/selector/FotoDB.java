/*
 * Copyright (C) 2008-2015 by Simon Hefti.
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
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
import org.rabinfingerprint.fingerprint.RabinFingerprintLong;
import org.rabinfingerprint.polynomial.Polynomial;
import org.sqlite.SQLiteJDBCLoader;

import ch.heftix.fotoworkflow.mover.FormatResult;
import ch.heftix.fotoworkflow.mover.GeoPoint;
import ch.heftix.fotoworkflow.mover.TikaMetadataHelper;
import done.cm.ConnectionDescription;
import done.cm.SimpleConnectionManager;

public class FotoDB {

	private Connection conn = null;

	QueryRunner fotoExistsQR = new QueryRunner();
	QueryRunner thumbnailExistsQR = new QueryRunner();
	QueryRunner countFotosQR = new QueryRunner();

	ResultSetHandler<Integer> firstIntRSH = null;
	ResultSetHandler<Thumbnail> thumbnailExistsRSH = null;

	TikaMetadataHelper mdh = new TikaMetadataHelper();
	Pattern reExtensionFilter = Pattern.compile("JPG|JPEG|jpeg|jpg");

	Pattern repetionFinder = Pattern.compile("(.+?)_\\1+.*");

	// M: Month in year, H: Hour in day (0-23), m: Minute in hour
	String pCreationDate = "@{CreationDate: yyyy-MM-dd'T'HHmm}";
	String pModel = "@{Model}";

	String fotoattrs = "fotoid,path,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note,isMissing,isPrivate,fingerprint";

	private final BigInteger key = new BigInteger("15470732547911801");
	private final Polynomial polynomial = Polynomial.createFromLong(key.longValue());
//	private final RabinFingerprintLong rabin = new RabinFingerprintLong(polynomial);

	private boolean excludeDocumentary = true;
	private boolean excludePrivate = true;

	SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	private SimpleConnectionManager cm = new SimpleConnectionManager();
	private ConnectionDescription cd = null;

	private FotoMove fotoMove = null;

	protected FotoDB() throws Exception {

		String home = System.getProperty("user.home");

		cd = new ConnectionDescription();
		cd.driver = "org.sqlite.JDBC";
		cd.id = "fotoarchive";
		cd.jdbcUrl = "jdbc:sqlite:" + home + "/.foto-thumbnails.db";

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

			firstIntRSH = new ResultSetHandler<Integer>() {
				public Integer handle(ResultSet rs) throws SQLException {
					Integer res = null;
					if (rs.next()) {
						res = rs.getInt(1);
						if( rs.wasNull()) {
							res = null;
						}
					}
					return res;
				}
			};

			thumbnailExistsRSH = new ResultSetHandler<Thumbnail>() {
				public Thumbnail handle(ResultSet rs) throws SQLException {
					if (rs.next()) {
						Thumbnail res = new Thumbnail();
						res.fotoid = rs.getInt(1);
						res.path = rs.getString(2);
						res.image = rs.getBytes(3);
						res.height = rs.getInt(4);
						res.mimeType = rs.getString(5);
						return res;
					}
					return null;
				}
			};

		} catch (SQLException e) {
			note("cannot connect to DB: %s", e.getMessage(), e);
		}

		this.fotoMove = new FotoMove(this);
	}

	protected Connection getConnection() throws SQLException {
		String home = System.getProperty("user.home");
		Connection c = DriverManager.getConnection("jdbc:sqlite:" + home + "/.foto-thumbnails.db");
		return c;
	}

	public void moveFoto(int fotoid, String album) throws Exception {
		fotoMove.moveFoto(fotoid, album);
	}

	public void togglePrivate(int fotoid) throws Exception {

		if (-1 == fotoid) {
			return;
		}

		String sql = "update foto set isPrivate = case when isPrivate=1 then 0 when isPrivate=0 then 1 else isPrivate end where fotoid=?";

		Connection c = cm.nextFree(cd);
		QueryRunner qr = new QueryRunner();
		qr.update(conn, sql, fotoid);
		cm.bringConnectionBack(cd.id, c);

		Foto foto = getFoto(fotoid);
		if (foto.isPrivate == 1) {
			moveFoto(fotoid, "private");
		} else {
			String album = mapCategoryToAlbum(foto.category);
			moveFoto(fotoid, album);
		}

	}

	public void storeInfo(int fotoid, String k, String v) throws SQLException {

		List<String> allowedKeys = Arrays.asList("note", "orientation", "category");

		if (null == k || null == v) {
			return;
		}

		if (!allowedKeys.contains(k)) {
			return;
		}

		if (v.length() < 1) {
			return;
		}

		// String stamp = getStamp();
		QueryRunner qr = new QueryRunner();
		String sql = "update foto set " + k + "=?" + " where fotoid=?";
		qr.update(conn, sql, v, fotoid);

		if ("category".equals(k)) {
			String album = mapCategoryToAlbum(v);
			try {
				moveFoto(fotoid, album);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public String mapCategoryToAlbum(String category) {

		if ("best-of".equals(category)) {
			return "bestof";
		} else if ("selection".equals(category)) {
			return "selection";
		} else if ("documentary".equals(category)) {
			return "documentary";
		} else if ("private".equals(category)) {
			return "private";
		}
		return "import";
	}

	public void addEvent(final String type, final String arg1, final String arg2, final String user, int fotoid,
			final String path, final String uri, final String qs) {

		QueryRunner qr = new QueryRunner();
		String sql = "insert into event (stamp, type, arg1, arg2, user, fotoid, path, uri, qs) values (?,?,?,?,?,?,?,?,?)";
		try {
			Connection c = cm.nextFree(cd);
			Date now = new Date();
			qr.update(conn, sql, now, type, arg1, arg2, user, fotoid, path, uri, qs);
			cm.bringConnectionBack(cd.id, c);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getInfo(int fotoid, String key) throws SQLException {

		if (null == key) {
			return null;
		}

		QueryRunner qr = new QueryRunner();
		String sql = String.format("select %s from foto where fotoid=?", key);
		ResultSetHandler<String> rsh = new ResultSetHandler<String>() {
			public String handle(ResultSet rs) throws SQLException {
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		};
		String res = qr.query(conn, sql, rsh, fotoid);
		return res;
	}

	public String getNote(int fotoid) throws SQLException {
		return getInfo(fotoid, "note");
	}

	public void appendNote(int fotoid, String v) throws SQLException {

		if (null == v) {
			return;
		}

		if (v.length() < 1) {
			return;
		}

		String stamp = getStamp();

		String note = getNote(fotoid);
		if (null == note) {
			note = v;
		} else {
			note = String.format("%s, %s", note, v);
		}

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set note=?,stamp=?" + " where fotoid=?";
		qr.update(conn, sql, note, stamp, fotoid);
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

	public boolean existsFoto(int fotoid) throws SQLException {
		boolean res = false;
		Integer cnt = fotoExistsQR.query(conn, "select count(path) from foto where fotoid=?", firstIntRSH, fotoid);
		if (null != cnt) {
			res = true;
		}
		return res;
	}

	public boolean existsFoto(File f) throws SQLException {
		return existsFoto(f.getAbsolutePath());
	}

	public boolean existsFoto(String path) throws SQLException {
		boolean res = false;
		if (null == path) {
			return res;
		}
		Integer cnt = fotoExistsQR.query(conn, "select count(path) from foto where path=?", firstIntRSH, path);
		if (null != cnt) {
			res = true;
		}
		return res;
	}

	public Integer existsFingerprint(String fingerPrint) throws SQLException {
		Integer fotoid = null;

		if (null == fingerPrint) {
			return fotoid;
		}

		QueryRunner qr = new QueryRunner();
		fotoid = qr.query(conn, "select fotoid from foto where fingerprint=?", firstIntRSH, fingerPrint);

		return fotoid;
	}

	public void insertFoto(File f, String note, final String fp) throws IOException, SQLException {
		insertFoto(conn, f, note, fp);
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

	public void deleteFoto(int fotoid) throws Exception {

		Connection c = cm.nextFree(cd);
		PreparedStatement ps = c.prepareStatement("delete from foto where fotoid=?");
		ps.clearParameters();
		ps.setInt(1, fotoid);
		ps.execute();
		ps.close();
		cm.bringConnectionBack(cd.id, c);
	}

	public void deleteFoto(String path) throws Exception {

		Connection c = cm.nextFree(cd);
		PreparedStatement ps = c.prepareStatement("delete from foto where path=?");
		ps.clearParameters();
		ps.setString(1, path);
		ps.execute();
		ps.close();
		cm.bringConnectionBack(cd.id, c);
	}

	/**
	 * save a new foto in the catalog
	 * @param c DB connection to use
	 * @param f file to insert
	 * @param note user-added note
	 * @param fp fingerprint
	 * @throws IOException
	 * @throws SQLException
	 */
	public void insertFoto(Connection c, File f, String note, final String fp) throws IOException, SQLException {

		if (null == f) {
			return;
		}

		if (!f.exists()) {
			return;
		}

		if (null == fp) {
			return;
		}

		// check existence
		boolean cnt = existsFoto(f);
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
		int isPrivate = 0;

		boolean hasNote = (null != note && note.length() > 1);

		StringBuffer vals = new StringBuffer(1024);
		StringBuffer sb = new StringBuffer(1024);
		sb.append("insert into foto (fotoid,path,mimetype,creationdate");
		vals.append("?,?,?,?");
		sb.append(",year,month,day,hour,minute");
		vals.append(",?,?,?,?,?");
		sb.append(",w,h,make,model,geo_long,geo_lat,orientation");
		vals.append(",?,?,?,?,?,?,?");
		if (hasNote) {
			sb.append(",note");
			vals.append(",?");
		}
		sb.append(", isMissing, isPrivate, fingerprint)");
		vals.append(",?,?,?");

		sb.append(" values(");
		sb.append(vals.toString());
		sb.append(")");

		String sql = sb.toString();

		int fotoid = nextSequence("foto");

		QueryRunner qr = new QueryRunner();

		if (hasNote) {
			qr.update(c, sql, fotoid, f.getAbsolutePath(), mt, cd, year, month, day, hour, minute, w, h, make, model,
					lng, lat, o, note, isMissing, isPrivate, fp);
		} else {
			qr.update(c, sql, fotoid, f.getAbsolutePath(), mt, cd, year, month, day, hour, minute, w, h, make, model,
					lng, lat, o, isMissing, isPrivate, fp);
		}

	}

	public void updatePath(int fotoid, String newPath) {

		try {
			Connection c = cm.nextFree(cd);
			String sql = "update foto set path=? where fotoid=?";
			PreparedStatement p = c.prepareStatement(sql);
			p.clearParameters();
			p.setString(1, newPath);
			p.setInt(2, fotoid);
			p.executeUpdate();
			p.close();
			cm.bringConnectionBack(cd.id, c);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// public void XXupdateFoto(File f, String note) throws IOException,
	// SQLException {
	// updateFoto(conn, f, note);
	// }

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
	public void XXupdateFoto(Connection c, File f, String note) throws IOException, SQLException {

		if (null == f) {
			return;
		}

		if (!f.exists()) {
			return;
		}

		// check existence
		boolean cnt = existsFoto(f);
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
		sb.append(",geo_long=?,geo_lat=?,orientation=?,isMissing=0");
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

	// protected void XXupdateMetadata(Connection c, int fotoid) throws
	// IOException, SQLException {
	//
	// File f = new File(path);
	// if (!f.exists()) {
	// return;
	// }
	//
	// Metadata md = mdh.readMetadata(f);
	//
	// String cd = mdh.format(f, md, "@{CreationDate: yyyy-MM-dd'T'HHmm}");
	//
	// QueryRunner qr = new QueryRunner();
	// String sql = "update foto set creationdate=? where path=?";
	// qr.update(c, sql, cd, path);
	// }

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
		if (excludePrivate) {
			sb.append(" and isPrivate=0");
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

	public List<Foto> XXsearchSimilarFoto(int fotoid, int page, int pagesize) throws SQLException {

		List<Foto> res = null;

		StringBuffer sb = new StringBuffer(1024);
		sb.append("select ");
		sb.append(fotoattrs);
		sb.append(" from foto where path in (");
		sb.append(" select distinct p from (");
		sb.append(" select p2 p, d from distance where fotoid1 = ? and d < 15");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		sb.append(" union all");
		if (excludeDocumentary) {
			sb.append(" and (category is NULL OR category <> 'documentary')");
		}
		sb.append(" select p1 p, d from distance where fotoid2 = ? and d < 15");
		sb.append(" union all");
		sb.append(" select path, 0 from foto where fotoid=?");
		sb.append(") order by d asc");
		sb.append(")");

		String sql = sb.toString();

		res = searchFotoBySQL(page, pagesize, sql, fotoid, fotoid, fotoid);

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
	public List<Foto> searchCloseDate(int fotoid, int page, int pagesize) throws SQLException {
		List<Foto> res = new ArrayList<Foto>();
		res = searchCloseDate(fotoid, page, pagesize, 1);
		return res;
	}

	public List<Foto> searchCloseDate(int fotoid, int page, int pagesize, int nDays) throws SQLException {

		Foto ref = getFoto(fotoid);
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
		if (excludePrivate) {
			sb.append(" and isPrivate=0");
		}
		sb.append(" order by creationdate");

		String sql = sb.toString();

		List<Foto> res = searchFotoBySQL(fotoid, pagesize, sql, cd, cd);
		return res;
	}

	public List<Foto> searchCloseLocation(int fotoid, int page, int pagesize) throws Exception {

		List<Foto> res = new ArrayList<Foto>();

		Foto ref = getFoto(fotoid);
		if ("NoLongitude".equals(ref.geo_long)) {
			throw new Exception("This foto has no geo location to use as reference");
		}

		double lng = Double.parseDouble(ref.geo_long);
		double lat = Double.parseDouble(ref.geo_lat);

		GeoPoint refpoint = new GeoPoint();
		refpoint.setLng(lng);
		refpoint.setLat(lat);

		res = searchCloseLocation(fotoid, pagesize, refpoint);

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
		if (excludePrivate) {
			sb.append(" and isPrivate=0");
		}

		String sql = sb.toString();

		int delta = 5;
		res = searchFotoBySQL(page, pagesize, sql, ref.lng_deg - delta, ref.lng_deg + delta, ref.lat_deg - delta,
				ref.lat_deg + delta);

		return res;
	}

	public int countFotos() throws SQLException {
		int cnt = countFotosQR.query(conn, "select count(path) from foto", firstIntRSH);
		return cnt;
	}

	public List<Foto> feelLucky(String searchTerm, int page, int pagesize) throws Exception {

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

	private Foto rs2Foto(ResultSet rs) throws SQLException {
		// "fotoid,path,mimetype,creationdate,w,h,make,model,geo_long,geo_lat,orientation,category,note,isMissing,isPrivate";

		Foto f = new Foto();
		f.fotoid = rs.getInt(1);
		f.path = rs.getString(2);
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
		f.isMissing = rs.getInt(14);
		f.isPrivate = rs.getInt(15);
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
	public Foto getFoto(int fotoid) throws SQLException {

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
		Foto res = qr.query(conn, "select " + fotoattrs + " from foto where fotoid=?", rsh, fotoid);

		return res;
	}

	public void invalidateThumbnail(int fotoid) throws IOException, SQLException {

		QueryRunner q2 = new QueryRunner();
		q2.update(conn, "delete from thumbnail where path=?", fotoid);
	}

	public Thumbnail getThumbnail(int fotoid, int width, int height) throws IOException, SQLException {

		Thumbnail res = null;
		Foto foto = getFoto(fotoid);

		// check DB first
		res = thumbnailExistsQR.query(conn,
				"select fotoid,image,height,mimetype from thumbnail where fotoid=? and height=?", thumbnailExistsRSH,
				fotoid, height);

		if (null == res) { // thumbnail not yet cached

			if (foto.h < height) { // for small images, do not calculate

				File f = new File(foto.path);

				res = new Thumbnail();
				res.mimeType = foto.mimeType;
				res.image = slurp(f);
				res.height = foto.h;
				res.path = foto.path;

			} else { // create and cache

				res = createThumbnail(foto, width, height);
				if (null != res.image) {
					insertThumbnail(res);
				}
			}
		}

		return res;
	}

	private Thumbnail createThumbnail(Foto f, int width, int height) throws FileNotFoundException, IOException {

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
			Thumbnails.of(is).useExifOrientation(false).dithering(Dithering.ENABLE).outputQuality(.9)
					.size(width, height).rotate(rotate).toOutputStream(baos);
		} else {
			Thumbnails.of(is).useExifOrientation(false).dithering(Dithering.ENABLE).outputQuality(.9)
					.size(width, height).toOutputStream(baos);
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
		qr.update(conn, "insert into thumbnail (fotoid, image, height, mimetype) values (?,?,?,?)", t.fotoid, t.image,
				t.height, t.mimeType);

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

	public void setExcludePrivate(boolean val) {
		excludePrivate = val;
	}

	public boolean toggleExcludePrivate() {
		if (excludePrivate) {
			excludePrivate = false;
		} else {
			excludePrivate = true;
		}
		return excludePrivate;
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

	public void updateFingerprint1(final int fotoid, final TikaMetadataHelper mdh) throws SQLException, IOException {

		Foto foto = getFoto(fotoid);

		if (null == foto) {
			return;
		}

		File f = new File(foto.path);

		if (null == f || !f.exists()) {
			return;
		}

		FormatResult fr = mdh.format(f);
		String fingerprint = fr.getResult();

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set fingerprint=? where fotoid=?";
		qr = new QueryRunner();
		qr.update(conn, sql, fingerprint, fotoid);
	}
	
	public String getFingerprint(final String fn) throws IOException {
		File f = new File(fn);
		return getFingerprint(f);
	}


	public String getFingerprint(final File f) throws IOException {

		String res = null;

		if (null == f || !f.exists()) {
			return res;
		}

		byte[] bytes = slurp(f);
		
		RabinFingerprintLong r = new RabinFingerprintLong(polynomial);
		r.pushBytes(bytes);

		res = Long.toString(r.getFingerprintLong(), 16);

		return res;
	}

	public void updateFingerprint(final int fotoid, final String fp) throws SQLException, IOException {

		QueryRunner qr = new QueryRunner();
		String sql = "update foto set fingerprint=? where fotoid=?";
		qr = new QueryRunner();
		qr.update(conn, sql, fp, fotoid);
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

	public synchronized int nextSequence(final String name) {

		int res = -1;

		try {
			Connection c = cm.nextFree(cd);
			PreparedStatement ps = c.prepareStatement("update sequence set val=val+1 where name=?");
			ps.clearParameters();
			ps.setString(1, name);
			ps.executeUpdate();
			ps.close();

			ps = c.prepareStatement("select val from sequence where name=?");
			ps.clearParameters();
			ps.setString(1, name);

			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				res = rs.getInt(1);
			}
			rs.close();
			ps.close();
			cm.bringConnectionBack(cd.id, c);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return res;
	}
}
