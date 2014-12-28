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

import ch.heftix.fotoworkflow.selector.cmd.AppendNoteCommand;
import ch.heftix.fotoworkflow.selector.cmd.ConfigGetCommand;
import ch.heftix.fotoworkflow.selector.cmd.ConfigSetCommand;
import ch.heftix.fotoworkflow.selector.cmd.ExcludeDocumentaryCommand;
import ch.heftix.fotoworkflow.selector.cmd.FeelLuckyCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCLCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.ImportCommand;
import ch.heftix.fotoworkflow.selector.cmd.InvalidateThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.NextMessageCommand;
import ch.heftix.fotoworkflow.selector.cmd.NextThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.PingCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseDateFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseLocationFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchSimilarFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdateCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdatePHashCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdatePHashsCommand;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;
import ch.heftix.fotoworkflow.selector.drive.DriveOAuthCommand;
import ch.heftix.fotoworkflow.selector.drive.DriveUtil;
import ch.heftix.fotoworkflow.selector.drive.DriveVerifyCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteLinkCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteListFotos;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteOAuthCommand;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteTagURLonlyNotes;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteUtil;
import ch.heftix.fotoworkflow.selector.evernote.EvernoteVerifyCommand;

public class FotoSelector implements Container, IMessageSink {

	private Map<String, WebCommand> commands = new HashMap<String, WebCommand>();
	private FotoDB db = null;
	public EvernoteUtil evernoteState = new EvernoteUtil();
	public DriveUtil driveState = new DriveUtil();
	private Queue<String> queue = new ConcurrentLinkedQueue<String>();

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
		fs.register("note.append", new AppendNoteCommand(fs)); // append note

		fs.register("evernote-oauth", new EvernoteOAuthCommand(fs));
		fs.register("evernote-verify", new EvernoteVerifyCommand(fs));
		fs.register("evernote-link", new EvernoteLinkCommand(fs));
		
		
		fs.register("evernote-list", new EvernoteListFotos(fs));
		fs.register("evernote-blankurl-process", new EvernoteTagURLonlyNotes(fs));
		
		fs.register("drive.oauth", new DriveOAuthCommand(fs));
		fs.register("drive.verify", new DriveVerifyCommand(fs));
		// fs.register("evernote-link", new EvernoteLinkCommand(fs));

		fs.register("invalidate_thumbnail", new InvalidateThumbnailCommand(fs));

		fs.register("ping", new PingCommand(fs)); // verify app alive

		fs.register("cfg.get", new ConfigGetCommand(fs)); // get config
		fs.register("cfg.set", new ConfigSetCommand(fs));

		fs.register("msg.next", new NextMessageCommand(fs));
		// fs.register("msg.get", new GetMessagesCommand(fs));

		fs.register("update-phash", new UpdatePHashCommand(fs));
		fs.register("update-phashs", new UpdatePHashsCommand(fs));

		fs.register("exclude-documentary", new ExcludeDocumentaryCommand(fs));
		fs.register("thumbnail.precache", new NextThumbnailCommand(fs));

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

	public void appendNote(String path, String v) {
		try {
			db.appendNote(path, v);
		} catch (SQLException e) {
			String msg = String.format("Cannot update note. Path: %s. Note: %s. Reason: %s", path, v, e);
			message(msg);
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
		boolean ex = db.toggleExcludeDocumentary();
		if (ex) {
			queue.add("category documentary now excluded by default");
		} else {
			queue.add("category documentary included by default");
		}
	}

	public synchronized void message(String msg) {
		queue.add(msg);
	}

	public synchronized void message(String fmt, Object... args) {
		String msg = String.format(fmt, args);
		queue.add(msg);
	}

	public String nextMessage() {
		return queue.poll();
	}
}