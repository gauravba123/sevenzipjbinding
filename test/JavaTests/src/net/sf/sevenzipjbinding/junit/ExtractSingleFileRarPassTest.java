package net.sf.sevenzipjbinding.junit;

import net.sf.sevenzipjbinding.ArchiveFormat;

public class ExtractSingleFileRarPassTest extends ExtractSingleFileAbstractPassTest {

	public ExtractSingleFileRarPassTest() {
		super(ArchiveFormat.RAR, 0, 2, 5);
	}

}