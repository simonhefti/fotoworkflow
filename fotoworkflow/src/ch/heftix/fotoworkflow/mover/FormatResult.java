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
package ch.heftix.fotoworkflow.mover;


/**
 * 
 */
public class FormatResult {

	public static final int A_UNDEFINED = -1;
	public static final int A_MOVE = 0;
	public static final int A_SKIP = 1;
	public static final int A_DELETE = 2;

	String srcFn = null;
	String res = null;
	int actionCode = A_UNDEFINED;

	public boolean doMove() {
		return actionCode == A_MOVE;
	}

	public boolean doSkip() {
		return actionCode == A_SKIP;
	}
	public boolean doDelete() {
		return actionCode == A_DELETE;
	}
	
	public String getResult() {
		return res;
	}
}
