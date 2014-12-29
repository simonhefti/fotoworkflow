/* Copyright (C) 2011-2014 by D1 Solutions AG.
 * All rights reserved.
 *
 * The copyright to the computer program(s) herein is the property of
 * D1 Solutions AG, Switzerland.  The program(s) may be used and/or copied
 * only with the written permission of D1 Solutions AG or in accordance
 * with the terms and conditions stipulated in the agreement/contract
 * under which the program(s) have been supplied.
 */
package done.cm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleConnectionManager {

	Map<String, Queue<Connection>> cmap = new ConcurrentHashMap<String, Queue<Connection>>();
	Set<String> driverMap = new HashSet<String>();

	public SimpleConnectionManager() {
	}

	public boolean connectionWorks(ConnectionDescription cd) {

		boolean res = false;

		try {
			Connection c = nextFree(cd);
			if (null != c) {
				res = true;
			}
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery("select 1");
			rs.close();
			s.close();
			bringConnectionBack(cd.id, c);
		} catch (Exception e) {
			res = false;
		}

		return res;
	}

	public synchronized Connection nextFree(ConnectionDescription cd) throws Exception {

		Connection c = null;

		Queue<Connection> lst = cmap.get(cd.id);
		if (null == lst) {
			lst = new ArrayBlockingQueue<Connection>(10);
			cmap.put(cd.id, lst);
		}
		if (lst.isEmpty()) {
			c = createNewConnection(cd);
		} else {
			c = lst.poll();
		}
		if (c.isClosed()) {
			c = createNewConnection(cd);
		}
		return c;
	}

	/**
	 * get connection for given connection name
	 */
	private Connection createNewConnection(ConnectionDescription cd) throws Exception {

		Connection res = null;

		if (!driverMap.contains(cd.driver)) {
			Class.forName(cd.driver);
		}

		String u = cd.user;
		if (null == u) {
			res = DriverManager.getConnection(cd.jdbcUrl);
		} else {
			res = DriverManager.getConnection(cd.jdbcUrl, u, cd.pw);
		}
		return res;
	}

	/** return connection for further use */
	public synchronized void bringConnectionBack(String cn, Connection c) throws SQLException {
		if (!c.isClosed()) {
			Queue<Connection> lst = cmap.get(cn);
			lst.add(c);
		}
	}

	/** return connection for no further use */
	public synchronized void trash(String cn, Connection c) throws SQLException {
		/* just ignore it */
	}

	public void shutDown() throws SQLException {

		for (String k : cmap.keySet()) {
			Queue<Connection> q = cmap.get(k);
			while (!q.isEmpty()) {
				Connection c = q.poll();
				c.close();
			}
		}
	}

	public int getMaxConnections() {
		int res = 0;
		for (String k : cmap.keySet()) {
			Queue<Connection> q = cmap.get(k);
			int s = q.size();
			if (s > res) {
				res = s;
			}
		}
		return res;
	}

	public String getStatistics() {
		return String.format("#drivers %d #cd: %d max_connections: %d", driverMap.size(), cmap.size(),
				getMaxConnections());

	}

	public String getOverview() {
		StringBuffer sb = new StringBuffer(4096);
		for (String k : cmap.keySet()) {
			Queue<Connection> q = cmap.get(k);
			int s = q.size();
			if (s > 0) {
				sb.append(String.format("cm: id: %s size: %d\n", k, s));
			}
		}
		return sb.toString();
	}
}