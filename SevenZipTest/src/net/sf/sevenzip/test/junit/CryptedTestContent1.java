package net.sf.sevenzip.test.junit;

public class CryptedTestContent1 extends StandardTest {

	@Override
	protected int getTestId() {
		return 1;
	}

	@Override
	protected boolean usingPassword() {
		return true;
	}

}
