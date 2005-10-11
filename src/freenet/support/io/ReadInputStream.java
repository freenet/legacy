package freenet.support.io;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;

/**
 * This reader wraps around a stream and provides the methods necessary to
 * efficiently read a Freenet message. Unlike a BufferedReader this does not
 * buffer the stream at all, so the trailing field is left in tact. This is
 * written for Freenet and as such assumes the characters are UTF8 encoded.
 * <p>
 * Multithreading Safe: No
 * </p>
 * 
 * @author oskar
 */
public class ReadInputStream extends FilterInputStream {

	/** The maximum number of chars to allow in a readTo* */
	public static int MAX_LENGTH = 4096;

	/**
	 * Buffer used to hold strings as they are being built. It only grows and
	 * never shrinks until the stream is closed.
	 */
	private StringBuffer strBuffer = new StringBuffer();

	public ReadInputStream(InputStream i) {
		super(i);
	}

	public String readln() throws IOException, EOFException {
		return readTo('\n', '\r');
	}

	/**
	 * Reads until a certain character is found, and returns a string
	 * containing the characters up to, but not including, that character,
	 * though it is also consumed. This method assumes the stream is UTF8
	 * encoded.
	 * 
	 * @param ends
	 *            The character to read up until.
	 * @return The string read, decoded with UTF8
	 * @exception EOFException
	 *                if the end of the stream is reached, with the string as
	 *                read so far as the comment.
	 * @exception IOException
	 *                if the end has not been found within MAX_LENGTH
	 *                characters, or if a bad UTF8 encoding is read, or some
	 *                other error condition occurs.
	 */
	public String readTo(char ends) throws IOException, EOFException {
		strBuffer.setLength(0);
		char r;
		int read = 0;
		while (true) {
			try {
				r = readUTFChar();
				read++;
			} catch (EOFException e) {
				throw new EOFException(strBuffer.toString());
			}
			if (r == -1)
				throw new EOFException(strBuffer.toString());
			if (r == ends)
				break;
			if (read > MAX_LENGTH)
				throw new IOException(strBuffer.toString());
			strBuffer.append(r);
		}
		return strBuffer.toString();
	}

	/**
	 * Reads until a certain character is found, and returns a string
	 * containing the characters up to, but not including, that character,
	 * though it is also consumed. This method assumes the stream is UTF8
	 * encoded.
	 * 
	 * @param ends
	 *            The character to read up until.
	 * @param ignore
	 *            A character that should be removed if it directly precedes
	 *            the terminating character. (This is for the very particular
	 *            situation of removing \r if it comes right before \n in
	 *            Freenet messages. Not sexy but necessary.)
	 * @return The string read, decoded with UTF8
	 * @throws EOFException
	 *             if the end of the stream is reached, with the string as read
	 *             so far as the comment.
	 */
	public String readTo(char ends, char ignore)
		throws IOException, EOFException {
		String s = readTo(ends);
		return s.length() > 0
			&& s.charAt(s.length() - 1) == ignore
				? s.substring(0, s.length() - 1)
				: s;
	}

	/**
	 * Reads until a certain character is found or EOF is encountered or
	 * MAX_LENGTH characters are read, and returns a string containing the
	 * characters up to, but not including, that character, though it is also
	 * consumed. So if a read is cut short by EOF, the truncated string is
	 * returned this time, but EOFException will be thrown on the subsequent
	 * call. This method assumes the stream is UTF8 encoded.
	 * 
	 * @param ends
	 *            The character to read up until.
	 * @return The string read, decoded with UTF8
	 * @exception EOFException
	 *                if the stream is already positioned at EOF (i.e. no
	 *                characters at all are available)
	 * @exception IOException
	 *                if a bad UTF8 encoding is read, or some other error
	 *                condition occurs.
	 */
	public String readToEOF(char ends) throws IOException, EOFException {
		strBuffer.setLength(0);
		char r = ' ';
		int read = 0;
		while (true) {
			try {
				r = readUTFChar();
				read++;
			} catch (EOFException e) {
				if (strBuffer.length() > 0)
					return strBuffer.toString();
				else
					throw e;
			}
			if (r == -1) {
				if (strBuffer.length() > 0)
					return strBuffer.toString();
				else
					throw new EOFException();
			}
			if (r == ends) {
				break;
			}
			if (read > MAX_LENGTH) {
				if (strBuffer.length() > 0)
					return strBuffer.toString();
				else
					throw new EOFException();
			}
			strBuffer.append(r);
		}
		return strBuffer.toString();
	}

	/**
	 * Reads until a certain character is found or EOF is encountered or
	 * MAX_LENGTH characters are read, and returns a string containing the
	 * characters up to, but not including, that character, though it is also
	 * consumed. So if a read is cut short by EOF, the truncated string is
	 * returned this time, but EOFException will be thrown on the subsequent
	 * call. This method assumes the stream is UTF8 encoded.
	 * 
	 * @param ends
	 *            The character to read up until.
	 * @param ignore
	 *            A character that should be removed if it directly precedes
	 *            the terminating character. (This is for the very particular
	 *            situation of removing \r if it comes right before \n in
	 *            Freenet messages. Not sexy but necessary.)
	 * @return The string read, decoded with UTF8
	 * @throws EOFException
	 *             if the end of the stream is reached, with the string as read
	 *             so far as the comment.
	 */
	public final String readToEOF(char ends, char ignore)
		throws IOException, EOFException {
		String s = readToEOF(ends);
		return s.length() > 0
			&& s.charAt(s.length() - 1) == ignore
				? s.substring(0, s.length() - 1)
				: s;
	}

	/**
	 * Reads a UTF8 encoded Unicode character off the stream.
	 * <p>
	 * Yes, it would be better to use java's built in encoding to accomplish
	 * this rather then hardcoding UTF8, but none of java's readers will work
	 * for us as they have a nasty habit of swallowing the entire stream.
	 * Another option would be to read the bytes into an array and use the
	 * string constructor that lets you specify an encoding, that is probably
	 * slower, but should provide a fallback if this fucks with you. This code
	 * is "inspired" by the UTF8 decoder in the GNU classpath libraries which
	 * is (c) Free Software Foundation RCB
	 * </p>
	 * <p>
	 * autophile@dol.net sez: There's always DataInputStream, but its docs say
	 * that it reads a "slight modification" of UTF-8. Specifically, that the
	 * null byte ( u0000) is encoded as two bytes (C0 80) rather than one (00),
	 * and that only the one-, two-, and three-byte formats are used (which is
	 * all this readUTF method supports anyway). So the questions are: Do you
	 * care about the null byte, and does DataInputStream snarf up more bytes
	 * than it needs when you decode a single UTF character?
	 * </p>
	 * <p>
	 * oskar sz: DataInputStream.readUTF() expects two bytes denoting the the
	 * length and then the actual string. That is not what we want.
	 * DataInputStream.readChar() doesn't do UTF at all but just (char)((a
	 * << 8) | (b & 0xff)) So no luck there.
	 * </p>
	 */
	public final char readUTFChar() throws EOFException, IOException {
		int val;
		int b = in.read();
		if (b == -1)
			throw new EOFException("Read -1 from: "+in);

		if ((b & 0xE0) == 0xE0) { // three bytes
			val = (b & 0x0F) << 12;

			if ((b = in.read()) == -1)
				throw new EOFException();
			if ((b & 0x80) != 0x80)
				throw new UTFDataFormatException("Bad encoding");
			val |= (b & 0x3F) << 6;

			if ((b = in.read()) == -1)
				throw new EOFException();
			if ((b & 0x80) != 0x80)
				throw new UTFDataFormatException("Bad encoding");
			val |= (b & 0x3F);
		} else if ((b & 0xC0) == 0xC0) { // two bytes
			val = (b & 0x1F) << 6;

			if ((b = in.read()) == -1)
				throw new EOFException();
			if ((b & 0x80) != 0x80)
				throw new UTFDataFormatException("Bad encoding");
			val |= (b & 0x3F);
		} else if (b < 0x80) { // one byte
			val = b;
		} else {
			throw new UTFDataFormatException("Bad encoding");
		}

		return (char) val;
	}

	/*
	 * @see java.io.InputStream#close()
	 */
	public void close() throws IOException {
		strBuffer = null;
		super.close();
	}

}
