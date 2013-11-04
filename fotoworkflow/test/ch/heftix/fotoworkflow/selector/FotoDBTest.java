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

import junit.framework.TestCase;

public class FotoDBTest extends TestCase {

	public void testStamp() throws Exception {
		FotoDB db = new FotoDB();
		String tst = db.getStamp();
		System.out.println(tst);
		assertNotNull(tst);
		String[] t2 = tst.split(" ");
		assertEquals(2, t2.length);
	}

}
