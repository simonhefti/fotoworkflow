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
package ch.heftix.fotoworkflow.selector.json;


public class StringBufferPayload implements Payload {

	private StringBuffer sb = new StringBuffer(2048);

	public void append(String s) {
		sb.append(s);
	}
	
	public int length() {
		return sb.length();
	}

	public String toJSON() {
		return sb.toString();
	}

}