package net.sf.sevenzip.test;

import net.sf.sevenzip.SequentialOutStream;

public class TestOutputStream implements SequentialOutStream {

	@Override
	public int write(byte[] data) {
		System.out.println("WRITE: '" + new String(data) + "'");
		return data.length;
	}

}
