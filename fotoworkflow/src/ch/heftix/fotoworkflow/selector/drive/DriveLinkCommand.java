/*
 * Copyright (C) 2008-2014 by Simon Hefti. All rights reserved.
 * Licensed under the EPL 1.0 (Eclipse Public License).
 * (see http://www.eclipse.org/legal/epl-v10.html)
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * Initial Developer: Simon Hefti
 */
package ch.heftix.fotoworkflow.selector.drive;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.heftix.fotoworkflow.selector.Foto;
import ch.heftix.fotoworkflow.selector.FotoSelector;
import ch.heftix.fotoworkflow.selector.cmd.WebCommand;
import ch.heftix.fotoworkflow.selector.json.JsonHelper;
import ch.heftix.fotoworkflow.selector.json.JsonResponse;

import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Data;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;

public class DriveLinkCommand implements WebCommand {

	FotoSelector fs = null;
	Notebook noteBook = null;
	private DateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HHmm");

	public DriveLinkCommand(FotoSelector fs) {
		this.fs = fs;
	}

	public void handle(Request request, Response response) {
		
		try {

			JsonResponse jr = new JsonResponse();

			String token = fs.getConf("evernote.accestoken");
			if( null == token || token.length() < 10) {
				jr.code = "error";
				jr.msg = "please authorize with evernote first";
				JsonHelper.send(jr, response);
				return;
			}

			String path = request.getParameter("path");
			
			if( null == path) {
				jr.code = "error";
				jr.msg = "path is a required argument, and it is missing";
				JsonHelper.send(jr, response);
				return;
			}
			
			File file = new File(path);
			if( ! file.exists()) {
				jr.code = "error";
				jr.msg = "file " + path + " not found";
				JsonHelper.send(jr, response);
				return;
			}
			
			Foto f = fs.getFoto(path);

			NoteStore.Client ns = null;
			try {
				ns = fs.evernoteState.getNoteStore(token);
			} catch (Exception e) {
				jr.code = "error";
				jr.msg = "no connection to Evernote. Please try again later. Reason: " + e.getMessage();
				JsonHelper.send(jr, response);
				return;
			}

			if (null == noteBook) {
				noteBook = getNotebook(ns, "FotoWF", token);
			}

			Note note = new Note();
			note.setTitle(f.getDescription());
			note.setNotebookGuid(noteBook.getGuid());
			if (null != f.creationdate && !"NoDate".equals(f.creationdate)) {
				Date d = parser.parse(f.creationdate);
				note.setCreated(d.getTime());
			}

			Resource resource = new Resource();

			resource.setData(readFileAsData(path));
			resource.setMime(f.mimeType);

			ResourceAttributes attributes = new ResourceAttributes();
			attributes.setFileName(path);
			attributes.setCameraMake(f.make);
			attributes.setCameraModel(f.model);
			if (null != f.geo_lat && !"NoLatitude".equals(f.geo_lat)) {
				attributes.setLatitude(Double.parseDouble(f.geo_lat));
			}
			if (null != f.geo_long && !"NoLongitude".equals(f.geo_long)) {
				attributes.setLongitude(Double.parseDouble(f.geo_long));
			}
			resource.setAttributes(attributes);

			note.addToResources(resource);

			note.addToTagNames("FotoWF");
			if ("best-of".equals(f.category)) {
				note.addToTagNames("best-of");
			}
			if ("documentary".equals(f.category)) {
				note.addToTagNames("documentary");
			}
			if ("selection".equals(f.category)) {
				note.addToTagNames("selection");
			}

			String hashHex = bytesToHex(resource.getData().getBodyHash());

			String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" + "<en-note>"
					+ "<en-media type=\"" + f.mimeType + "\" hash=\"" + hashHex + "\"/>" + "</en-note>";
			note.setContent(content);

			Note createdNote = null;
			try {
				createdNote = ns.createNote(token, note);
			} catch (Exception e) {
				jr.code = "error";
				jr.msg = "Saving foto to Evernote failed: " + e.getMessage();
				JsonHelper.send(jr, response);
				return;
			}
			fs.storeInfo(path, "noteId", createdNote.getGuid());
			
			jr.msg = "Foto inserted, got ID: " + createdNote.getGuid();
			jr.code = "info";
			JsonHelper.send(jr, response);
			System.out.println("foto inserted: " + createdNote);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private Notebook getNotebook(NoteStore.Client ns, String name, String token) throws Exception {

		List<Notebook> notebooks = ns.listNotebooks(token);
		Notebook res = null;
		for (Notebook notebook : notebooks) {
			String nbName = notebook.getName();
			if (name.equals(nbName)) {
				res = notebook;
				break;
			}
		}
		if (null == res) {
			res = new Notebook();
			res.setName(name);
			ns.createNotebook(token, res);
		}
		return res;
	}

	/**
	 * Helper method to read the contents of a file on disk and create a new
	 * Data object.
	 */
	private static Data readFileAsData(String fileName) throws Exception {

		// Read the full binary contents of the file
		FileInputStream in = new FileInputStream(fileName);
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		byte[] block = new byte[10240];
		int len;
		while ((len = in.read(block)) >= 0) {
			byteOut.write(block, 0, len);
		}
		in.close();
		byte[] body = byteOut.toByteArray();

		// Create a new Data object to contain the file contents
		Data data = new Data();
		data.setSize(body.length);
		data.setBodyHash(MessageDigest.getInstance("MD5").digest(body));
		data.setBody(body);

		return data;
	}

	/**
	 * Helper method to convert a byte array to a hexadecimal string.
	 */
	public static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte hashByte : bytes) {
			int intVal = 0xff & hashByte;
			if (intVal < 0x10) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(intVal));
		}
		return sb.toString();
	}

}