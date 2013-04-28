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
package ch.heftix.fotoworkflow.selector;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import ch.heftix.fotoworkflow.selector.FotoDB.Foto;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteLinkCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteUtil;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteVerifyCommand;
import ch.heftix.fotoworkflow.selector.evernote.OAuthCommand;

public class FotoSelector implements Container {

	private Map<String, WebCommand> commands = new HashMap<String, WebCommand>();
	protected FotoDB db = null;
	public EvernoteUtil oAuthState = new EvernoteUtil();

	public FotoSelector() throws Exception {
		db = new FotoDB();
	}

	public static void main(String[] args) throws Exception {

		System.setProperty("java.awt.headless", "true");

		FotoSelector fs = new FotoSelector();
		Server server = new ContainerServer(fs);
		Connection connection = new SocketConnection(server);
		SocketAddress address = new InetSocketAddress(1994);

		fs.register("list", new SearchFotoCommand(fs));

		WebCommand getCommand = new GetCommand();
		fs.register("get", getCommand);

		WebCommand defaultCommand = new GetCLCommand(fs);
		fs.register("default", defaultCommand);

		fs.register("thumbnail", new GetThumbnailCommand(fs));
		fs.register("scan", new ScanCommand(fs));

		fs.register("store", new UpdateCommand(fs));

		fs.register("evernote-oauth", new OAuthCommand(fs));
		fs.register("evernote-verify", new EvernoteVerifyCommand(fs));
		fs.register("evernote-link", new EvernoteLinkCommand(fs));

		fs.register("invalidate_thumbnail", new InvalidateThumbnailCommand(fs));

		fs.register("ping", new PingCommand());

		connection.connect(address);

		BrowserOpen.openURL("http://localhost:1994");
	}

	public void register(String name, WebCommand cmd) {
		commands.put(name, cmd);
	}

	public void handle(Request request, Response response) {

		try {
			Query q = request.getQuery();
			Object cmd = q.get("cmd");
			WebCommand wc = commands.get(cmd);

			long time = System.currentTimeMillis();

			response.setValue("Server", "FotoWorkflow/" + Version.getVersion());
			response.setDate("Date", time);

			if (null == wc) {
				wc = commands.get("default");
			}
			wc.handle(request, response);

			response.close();

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	public void setConf(String key, String val) {
		db.setConf(key, val);
	}

	public String getConf(String key) {
		return db.getConf(key);
	}

	public Foto getFoto(String path) {
		Foto res = null;
		try {
			res = db.getFoto(path);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}

	public void storeInfo(String path, String k, String v) {
		try {
			db.storeInfo(path, k, v);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}