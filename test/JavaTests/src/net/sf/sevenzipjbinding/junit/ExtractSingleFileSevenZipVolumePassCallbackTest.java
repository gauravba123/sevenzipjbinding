package net.sf.sevenzipjbinding.junit;

import net.sf.sevenzipjbinding.ArchiveFormat;

public class ExtractSingleFileSevenZipVolumePassCallbackTest extends ExtractSingleFileAbstractPassTest {

	public ExtractSingleFileSevenZipVolumePassCallbackTest() {
		super(ArchiveFormat.SEVEN_ZIP, 0, 5, 9);
		usingPasswordCallback();
		useVolumedSevenZip();
	}
}
