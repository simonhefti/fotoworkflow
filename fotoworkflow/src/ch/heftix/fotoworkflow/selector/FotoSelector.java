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

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import ch.heftix.fotoworkflow.selector.cmd.ExcludeDocumentaryCommand;
import ch.heftix.fotoworkflow.selector.cmd.FeelLuckyCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCLCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetConfigCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.ImportCommand;
import ch.heftix.fotoworkflow.selector.cmd.InvalidateThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.NextMessageCommand;
import ch.heftix.fotoworkflow.selector.cmd.PingCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseDateFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseLocationFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchSimilarFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdateCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdatePHashCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdatePHashsCommand;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteLinkCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteUtil;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteVerifyCommand;
import ch.heftix.fotoworkflow.selector.evernote.OAuthCommand;

public class FotoSelector implements Container {

	private Map<String, WebCommand> commands = new HashMap<String, WebCommand>();
	private FotoDB db = null;
	public EvernoteUtil oAuthState = new EvernoteUtil();
	public Queue<String> queue = new ConcurrentLinkedQueue<String>();

	public FotoSelector() throws Exception {
		db = new FotoDB();
	}

	public static void main(String[] args) throws Exception {

		System.setProperty("java.awt.headless", "true");

		FotoSelector fs = new FotoSelector();
		Server server = new ContainerServer(fs);
		Connection connection = new SocketConnection(server);
		SocketAddress address = new InetSocketAddress(1994);

		fs.register("list", new SearchFotoCommand(fs)); // list search fotos
		fs.register("similar", new SearchSimilarFotoCommand(fs));
		fs.register("closedate", new SearchCloseDateFotoCommand(fs));
		fs.register("closeloc", new SearchCloseLocationFotoCommand(fs));
		fs.register("feellucky", new FeelLuckyCommand(fs));
		fs.register("get", new GetCommand()); // get a single photo

		fs.register("default", new GetCLCommand(fs)); // file via class loader

		fs.register("thumbnail", new GetThumbnailCommand(fs)); // get small
		fs.register("import", new ImportCommand(fs)); // import

		fs.register("store", new UpdateCommand(fs)); // save attribute

		fs.register("evernote-oauth", new OAuthCommand(fs));
		fs.register("evernote-verify", new EvernoteVerifyCommand(fs));
		fs.register("evernote-link", new EvernoteLinkCommand(fs));

		fs.register("invalidate_thumbnail", new InvalidateThumbnailCommand(fs));

		fs.register("ping", new PingCommand(fs)); // verify app alive

		fs.register("cfg.get", new GetConfigCommand(fs)); // get config

		fs.register("msg.next", new NextMessageCommand(fs));

		fs.register("update-phash", new UpdatePHashCommand(fs));
		fs.register("update-phashs", new UpdatePHashsCommand(fs));

		fs.register("exclude-documentary", new ExcludeDocumentaryCommand(fs));

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

	public FotoDB getDB() {
		return this.db;
	}

	public List<Foto> searchFoto(String searchTerm, int page, int pagesize) throws SQLException {
		List<Foto> res = null;
		res = db.searchFoto(searchTerm, page, pagesize);
		return res;
	}
	
	public void toggleExcludeDocumentary() {
		db.toggleExcludeDocumentary();
	}

}