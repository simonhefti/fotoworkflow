/*
 * Copyright (C) 2008-2015 by Simon Hefti. All rights reserved.
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * Initial Developer: Simon Hefti
 */
package ch.heftix.fotoworkflow.selector;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.heftix.fotoworkflow.selector.cmd.AppendNoteCommand;
import ch.heftix.fotoworkflow.selector.cmd.ConfigGetCommand;
import ch.heftix.fotoworkflow.selector.cmd.ConfigSetCommand;
import ch.heftix.fotoworkflow.selector.cmd.ExcludeDocumentaryCommand;
import ch.heftix.fotoworkflow.selector.cmd.ExcludePrivateCommand;
import ch.heftix.fotoworkflow.selector.cmd.FeelLuckyCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCLCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetCommand;
import ch.heftix.fotoworkflow.selector.cmd.GetThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.ImportCommand;
import ch.heftix.fotoworkflow.selector.cmd.InvalidateThumbnailCommand;
import ch.heftix.fotoworkflow.selector.cmd.NextMessageCommand;
import ch.heftix.fotoworkflow.selector.cmd.Params;
import ch.heftix.fotoworkflow.selector.cmd.PingCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseDateFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchCloseLocationFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.SearchFotoCommand;
import ch.heftix.fotoworkflow.selector.cmd.TogglePrivateCommand;
import ch.heftix.fotoworkflow.selector.cmd.UpdateCommand;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class FotoSelector extends NanoHTTPD implements IMessageSink {

	private Map<String, WebCommand> commands = new HashMap<String, WebCommand>();
	private FotoDB db = null;
	private Queue<String> queue = new ConcurrentLinkedQueue<String>();

	public FotoSelector() throws Exception {
		super(1994);
		db = new FotoDB();
	}

	public static void main(String[] args) throws Exception {

		System.setProperty("java.awt.headless", "true");

		FotoSelector fs = new FotoSelector();

		fs.register("list", new SearchFotoCommand(fs)); // list search fotos
		fs.register("closedate", new SearchCloseDateFotoCommand(fs));
		fs.register("closeloc", new SearchCloseLocationFotoCommand(fs));
		fs.register("feellucky", new FeelLuckyCommand(fs));
		fs.register("get", new GetCommand()); // get a single photo

		fs.register("default", new GetCLCommand(fs)); // file via class loader

		fs.register("thumbnail", new GetThumbnailCommand(fs)); // get small
		fs.register("import", new ImportCommand(fs)); // import

		fs.register("store", new UpdateCommand(fs)); // save attribute
		fs.register("note.append", new AppendNoteCommand(fs)); // append note

		fs.register("invalidate_thumbnail", new InvalidateThumbnailCommand(fs));

		fs.register("ping", new PingCommand(fs)); // verify app alive

		fs.register("cfg.get", new ConfigGetCommand(fs)); // get config
		fs.register("cfg.set", new ConfigSetCommand(fs));

		fs.register("msg.next", new NextMessageCommand(fs));

		fs.register("exclude.documentary", new ExcludeDocumentaryCommand(fs));

		fs.register("exclude.private", new ExcludePrivateCommand(fs));
		fs.register("toggle.private", new TogglePrivateCommand(fs));

		try {
			fs.start();
			System.out.println("started");
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
			System.exit(-1);
		}

		BrowserOpen.openURL("http://localhost:1994");

		// now wait
		try {
			System.in.read();
		} catch (Throwable ignored) {
		}
	}

	public void register(String name, WebCommand cmd) {
		commands.put(name, cmd);
	}

	@Override
	public Response serve(IHTTPSession session) {

		if (null == session) {
			Response r = new Response(Status.OK, "text/plain", "IHTTPSession is required");
			return r;
		}

		Params params = new Params(session);

		String cmd = params.get("cmd");

		long now = System.currentTimeMillis();

		WebCommand wc = commands.get(cmd);
		if (null == wc) {
			wc = commands.get("default");
		}

		Response r = null;

//		System.out.println("m/q/u '" + session.getMethod() + "' '" + session.getQueryParameterString() + "' '"
//				+ session.getUri());

		try {
			r = wc.handle(params);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (null != cmd && !"msg.next".equals(cmd)) {
			// asynchronously record this event
			final String user = "";
			final String path = params.get("path");
			final String type = cmd;
			final String uri = session.getUri();
			final String qs = session.getQueryParameterString();
			final int fotoid = params.getInt("fotoid", -1);
			final FotoDB db = this.db;
			final String arg1 = params.get("k");
			final String arg2 = params.get("v");
			Runnable task = new Runnable() {
				public void run() {
					db.addEvent(type, arg1, arg2, user, fotoid, path, uri, qs);
				}
			};
			Thread worker = new Thread(task);
			worker.start();
		}

		return r;
	}

	public void setConf(String key, String val) {
		db.setConf(key, val);
	}

	public String getConf(String key) {
		return db.getConf(key);
	}

	public Foto getFoto(int fotoid) {
		Foto res = null;
		try {
			res = db.getFoto(fotoid);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}

	public void storeInfo(int fotoid, String k, String v) {
		try {
			db.storeInfo(fotoid, k, v);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void appendNote(int fotoid, String v) {
		try {
			db.appendNote(fotoid, v);
		} catch (SQLException e) {
			String msg = String.format("Cannot update note. fotoid: %d. Note: %s. Reason: %s", fotoid, v, e);
			message(msg);
		}
	}

	public void togglePrivate(int fotoid) throws Exception {
		db.togglePrivate(fotoid);
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

	public void toggleExcludePrivatey() {
		boolean ex = db.toggleExcludePrivate();
		if (ex) {
			queue.add("private fotos now excluded by default");
		} else {
			queue.add("private fotos included by default");
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