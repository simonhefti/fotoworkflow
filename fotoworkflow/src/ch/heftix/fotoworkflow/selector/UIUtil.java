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

/**
 * Helper. Provides static helper methods.
 */
public class UIUtil {

	/**
	 * shorten given string (typically, a filename) by replacing the characters
	 * in the middle of the string with dots.
	 * 
	 * @param in
	 *            string to shorten
	 * @return shortened string
	 */
	public static String shorten(String in) {
		return shorten(in, 32, "...");
	}

	/**
	 * shorten given string (typically, a filename) by replacing the characters
	 * in the middle of the string with dots.
	 * 
	 * @param in
	 *            string to shorten
	 * @param maxLen
	 *            string shorter than this length are not changed
	 * @param replacement
	 *            replacement string to use
	 * @return shortened string
	 */
	public static String shorten(final String in, final int maxLen, final String replacement) {
		StringBuffer sb = new StringBuffer(256);
		if (in == null) {
			return "";
		}
		if (in.length() < maxLen) {
			return in;
		}
		int half = maxLen / 2;
		int rep = replacement.length();
		if (rep > half) {
			throw new IllegalArgumentException("replacement must be shorter than half of maxLen");
		}
		int pos = half - (int) Math.ceil(rep / 2.0d);

		sb.append(in.substring(0, pos));
		sb.append(replacement);
		sb.append(in.substring(in.length() - pos));

		return sb.toString();

	}

	public static String trimNull(final String str) {
		if (null != str) {
			String res = str.trim();
			if (res.length() > 0) {
				return res;
			}
		}
		return null;
	}
	
	public static String removeSpecial(final String str) {
		if (null == str) {
			return null;
		}
		String res = null;
		res = str.replaceAll("\\s", "");
		res = res.replaceAll("'", "");
		res = res.replaceAll(" ", "");
		
		return res;
	}

	public static String stackTrace(final Throwable caught) {
		StringBuffer sb = new StringBuffer(1024);
		sb.append("<pre>");
		stackTrace(caught, sb);
		sb.append("</pre>");
		return sb.toString();
	}

	private static String stackTrace(final Throwable caught, final StringBuffer sb) {
		StackTraceElement[] st = caught.getStackTrace();
		for (int i = 0; i < st.length; i++) {
			sb.append(st[i]);
			sb.append("<br/>");
		}
		Throwable cause = caught.getCause();
		if (null != cause) {
			sb.append("cause:<br/>");
			stackTrace(caught, sb);
		}
		return sb.toString();
	}

}
