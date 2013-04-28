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
 */
public class Version {

	public static String CURRENT_DB_VERSION = "1";

	public static String getVersion() {
		return "0.2.0";
	}

	public static void main(String[] args) {
		System.out.println(getVersion());
	}

}
