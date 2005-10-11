package freenet.keys;

import java.io.InputStream;
import java.util.Random;

import freenet.Core;
import freenet.Key;
import freenet.KeyException;
import freenet.Presentation;
import freenet.Storables;
import freenet.crypt.Digest;
import freenet.crypt.SHA1;
import freenet.support.io.DataNotValidIOException;
import freenet.support.io.VerifyingInputStream;

public final class CHK extends Key {

	// FIXME -- change keytype number -- is 0x0302 ok?
	public static int keyNumber = 0x0302;

	/**
	 * Create a new CHK from the given byte representation.
	 */
	public CHK(byte[] keyval) throws KeyException {
		super(keyval);
		if (val.length != 23
			|| (val[21] & 0xff) != (keyNumber >> 8 & 0xff)
			|| (val[22] & 0xff) != (keyNumber & 0xff))
			throw new KeyException("Byte array does not contain a CHK");
	}

	/**
	 * Create a random CHK
	 */
	private CHK(Random r) {
	    super(20, 15, keyNumber);
	    byte[] buf = new byte[20];
	    r.nextBytes(buf);
	    System.arraycopy(buf, 0, val, 0, 20);
	}
	
	public static CHK randomKey(Random r) {
	    return new CHK(r);
	}
	
	/**
	 * Create a new CHK from the given Storables and length
	 */
	public CHK(Storables storables, int log2size) throws KeyException {
		super(20, log2size, keyNumber);
		if (!storables.isLegalForCHK())
			throw new KeyException("illegal Storables");
		Digest ctx = SHA1.getInstance();
		storables.hashUpdate(ctx);
		System.arraycopy(ctx.digest(), 0, val, 0, 20);
	}

	public VerifyingInputStream verifyStream(
		InputStream data,
		Storables storables,
		long transLength)
		throws DataNotValidIOException {

		if (!storables.isLegalForCHK()) {
			throw new DataNotValidIOException(Presentation.CB_BAD_KEY);
		}
		int log2size = val[20];
		long partSize = storables.getPartSize();
		long dataLength = getDataLength(transLength, partSize);

		if (log2size < LOG2_MINSIZE
			|| log2size > LOG2_MAXSIZE
			|| 1 << log2size != dataLength
			|| partSize != getPartSize(dataLength)) {
			throw new DataNotValidIOException(Presentation.CB_BAD_KEY);
		}

		// check correctness of routing key
		Digest ctx = SHA1.getInstance();
		storables.hashUpdate(ctx);
		if (!freenet.crypt.Util.byteArrayEqual(ctx.digest(), val, 0, 20))
			throw new DataNotValidIOException(Presentation.CB_BAD_KEY);

		return super.verifyStream(data, storables, transLength);
	}
}
