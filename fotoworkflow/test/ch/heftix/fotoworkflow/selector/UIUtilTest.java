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

public class UIUtilTest extends TestCase {
	
	public void test1() throws Exception {
		String res = UIUtil.removeSpecial("Foo's Bar");
		assertEquals("FoosBar", res);
		
		res = UIUtil.removeSpecial("FoosBar");
		assertEquals("FoosBar", res);
		
		res = UIUtil.removeSpecial(null);
		assertNull(res);

	}

}
